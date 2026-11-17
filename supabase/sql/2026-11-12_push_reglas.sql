-- Ruma: reglas de los avisos push (2026-11-12). Va DESPUÉS de 2026-11-12_push_notifications.sql.
-- APLICADO en STAGING el 2026-11-12. Falta repetirlo en producción (junto con el script anterior).
--
-- Qué decide la base, y solo la base:
--   1. Qué avisos salen del teléfono: columna notifications.push ('none' | 'normal' | 'urgent'), que escribe
--      quien crea el aviso. Por defecto 'none': un aviso nuevo no molesta a nadie hasta que alguien lo decide.
--   2. Cuándo: 'urgent' sale al instante a cualquier hora (un recojo en vivo); 'normal' respeta el silencio de
--      21:00 a 08:00 de Lima y sale a las 08:00.
--   3. Reintentos: cada aviso elegible es una fila de push_queue. pg_cron la reintenta cada minuto con espera
--      creciente (1, 2, 4 y 8 min; 4 intentos) hasta que la función send-push la marca como enviada o caduca.
--   4. Web Push (PWA en iPhone y navegador): push_tokens guarda endpoint + p256dh + auth con platform = 'web'.
--
-- Nuevo aviso: «puntos acreditados» cuando el acopio pesa la entrega (settle_delivery fija
-- pickups.generator_points_final). Antes ese momento, el de pago real, pasaba en silencio.
--
-- Fuera de este script (los pone scripts/setup-push-secrets.sh, nunca el repo):
--   vault: push_hook_secret (firma de la llamada) y push_function_url (URL de send-push de ESTE proyecto).
--   Sin los dos, la cola se llena pero no se llama a nadie.
-- En producción: pg_cron y pg_net deben poder activarse; el resto es idéntico.

begin;

create extension if not exists pg_cron with schema pg_catalog;

-- 1. La regla viaja con el aviso ------------------------------------------------------------------------------

alter table public.notifications
  add column if not exists push text not null default 'none' check (push in ('none', 'normal', 'urgent'));

-- 2. Suscripciones web ----------------------------------------------------------------------------------------

alter table public.push_tokens add column if not exists p256dh text;
alter table public.push_tokens add column if not exists auth text;
alter table public.push_tokens drop constraint if exists push_tokens_web_keys;
alter table public.push_tokens add constraint push_tokens_web_keys
  check (platform <> 'web' or (p256dh is not null and auth is not null));

-- Con dos firmas, PostgREST no sabría cuál elegir: la de 4 argumentos reemplaza a la de 2.
drop function if exists public.register_push_token(text, text);

create or replace function public.register_push_token(
  p_token text, p_platform text default 'android', p_p256dh text default null, p_auth text default null)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_platform text := coalesce(nullif(p_platform, ''), 'android');
begin
  if auth.uid() is null then
    raise exception 'Inicia sesión para recibir avisos.' using errcode = '42501';
  end if;
  if v_platform = 'android' then
    if p_token is null or length(p_token) not between 20 and 4096 or p_token !~ '^[A-Za-z0-9_:.-]+$' then
      raise exception 'Token de avisos inválido.' using errcode = '22023';
    end if;
    p_p256dh := null; p_auth := null;
  elsif v_platform = 'web' then
    -- send-push hará un POST a esta dirección: solo los servicios push de los navegadores, nunca una URL libre.
    if p_token is null or length(p_token) > 2048 or p_token !~
       '^https://([a-z0-9-]+\.)*(fcm\.googleapis\.com|push\.apple\.com|push\.services\.mozilla\.com|notify\.windows\.com)/' then
      raise exception 'Suscripción de avisos inválida.' using errcode = '22023';
    end if;
    if p_p256dh is null or p_p256dh !~ '^[A-Za-z0-9_-]{86,88}$' or p_auth is null or p_auth !~ '^[A-Za-z0-9_-]{22,24}$' then
      raise exception 'Suscripción de avisos inválida.' using errcode = '22023';
    end if;
  else
    raise exception 'Plataforma de avisos inválida.' using errcode = '22023';
  end if;

  -- Un mismo teléfono puede cambiar de cuenta: el token pasa a quien inició sesión ahora.
  insert into public.push_tokens (token, user_id, platform, p256dh, auth, updated_at)
  values (p_token, auth.uid(), v_platform, p_p256dh, p_auth, now())
  on conflict (token) do update
    set user_id = excluded.user_id, platform = excluded.platform, p256dh = excluded.p256dh, auth = excluded.auth, updated_at = now();

  -- Diez dispositivos por cuenta: nadie hace que send-push recorra miles de tokens inventados.
  delete from public.push_tokens
   where user_id = auth.uid()
     and token not in (select token from public.push_tokens where user_id = auth.uid() order by updated_at desc limit 10);
end;
$$;

revoke all on function public.register_push_token(text, text, text, text) from public, anon;
grant execute on function public.register_push_token(text, text, text, text) to authenticated;

-- 3. Cola de envío --------------------------------------------------------------------------------------------

create table if not exists public.push_queue (
  notification_id bigint primary key references public.notifications(id) on delete cascade,
  level text not null check (level in ('normal', 'urgent')),
  attempts integer not null default 0,
  next_attempt_at timestamptz not null,
  expires_at timestamptz not null,
  sent_at timestamptz,
  last_error text,
  created_at timestamptz not null default now()
);
create index if not exists push_queue_due_idx on public.push_queue (next_attempt_at) where sent_at is null;
alter table public.push_queue enable row level security;
revoke all on public.push_queue from anon, authenticated;

-- Silencio nocturno: lo no urgente espera a las 08:00 de Lima.
create or replace function public.push_not_before(p_level text, p_at timestamptz default now())
returns timestamptz
language sql
stable
set search_path = public
as $$
  select case
    when p_level = 'urgent' then p_at
    when (p_at at time zone 'America/Lima')::time >= time '21:00'
      then (date_trunc('day', p_at at time zone 'America/Lima') + interval '1 day 8 hours') at time zone 'America/Lima'
    when (p_at at time zone 'America/Lima')::time < time '08:00'
      then (date_trunc('day', p_at at time zone 'America/Lima') + interval '8 hours') at time zone 'America/Lima'
    else p_at
  end;
$$;

-- Llama a send-push por cada fila vencida. La función marca sent_at; lo que no se marca se reintenta.
-- ponytail: sin tope por pasada; con miles de avisos por minuto, procesar por lotes con SKIP LOCKED.
create or replace function public.push_dispatch(p_id bigint default null)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_secret text;
  v_url text;
  r record;
  n integer := 0;
begin
  select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'push_hook_secret';
  select decrypted_secret into v_url from vault.decrypted_secrets where name = 'push_function_url';
  if v_secret is null or v_url is null then
    return 0;
  end if;
  for r in
    update public.push_queue q
       set attempts = q.attempts + 1,
           next_attempt_at = now() + make_interval(mins => (2 ^ q.attempts)::integer)
     where q.sent_at is null and q.attempts < 4 and q.expires_at > now() and q.next_attempt_at <= now()
       and (p_id is null or q.notification_id = p_id)
    returning q.notification_id
  loop
    perform net.http_post(
      url := v_url,
      body := jsonb_build_object('id', r.notification_id),
      headers := jsonb_build_object('Content-Type', 'application/json', 'x-push-secret', v_secret),
      timeout_milliseconds := 20000
    );
    n := n + 1;
  end loop;
  return n;
end;
$$;

create or replace function public.push_notification()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_at timestamptz;
begin
  if new.push = 'none' or not exists (select 1 from public.push_tokens where user_id = new.user_id) then
    return new;
  end if;
  v_at := public.push_not_before(new.push);
  insert into public.push_queue (notification_id, level, next_attempt_at, expires_at)
  values (new.id, new.push, v_at,
          v_at + case new.push when 'urgent' then interval '30 minutes' else interval '12 hours' end);
  if v_at <= now() then
    perform public.push_dispatch(new.id);
  end if;
  return new;
exception when others then
  -- Un fallo del push nunca deshace el paso del recojo (ni la liquidación) que escribió el aviso.
  raise warning 'push_notification: %', sqlerrm;
  return new;
end;
$$;

revoke all on function public.push_not_before(text, timestamptz) from public, anon, authenticated;
revoke all on function public.push_dispatch(bigint) from public, anon, authenticated;
revoke all on function public.push_notification() from public, anon, authenticated;

drop trigger if exists trg_notification_push on public.notifications;
create trigger trg_notification_push
after insert on public.notifications
for each row execute function public.push_notification();

-- 4. Qué avisa cada paso del recojo -----------------------------------------------------------------------------
-- Mismo texto que antes; cambia la columna push y se añade el aviso del pesaje.
--   urgent: rider asignado (a los dos), «va en camino» (la ventana dura horas: este es el aviso de «ahora»),
--           recojo completado (familia), cancelado (rider), «buscando otro Eco-Rider» (familia).
--   normal: puntos acreditados en el acopio.
--   none  : «está llegando» y «llegó» (pasos repetidos a minutos de «va en camino»; el mapa ya los muestra)
--           y el «Recojo completado» del rider, que lo acaba de marcar él mismo.
-- Cambiar una regla es cambiar el valor de push en su insert, aquí y en ningún otro sitio.

create or replace function public.notify_pickup_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_rider text;
  v_gen   text;
  v_prev  text;
begin
  -- El acopio pesó la entrega: los puntos dejan de estar «por confirmar».
  if tg_op = 'UPDATE' and old.generator_points_final is null and new.generator_points_final is not null then
    insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push)
    values (new.generator_id, 'points_earned',
            case when new.generator_points_final > 0 then '¡Tus Eco-Puntos ya están disponibles!'
                 else 'Tu recojo no sumó puntos' end,
            case when new.generator_points_final > 0
                 then '+' || new.generator_points_final || ' Eco-Puntos: el centro de acopio pesó tu recojo. Ya puedes canjearlos.'
                 else 'El centro de acopio no pudo verificar el material de este recojo.' end,
            'pickup', new.id, 'normal');
  end if;

  if tg_op = 'UPDATE' and new.status is not distinct from old.status
     and new.rider_id is not distinct from old.rider_id then
    return null;
  end if;

  select coalesce(display_name, 'Tu Eco-Rider') into v_rider from public.profiles where id = new.rider_id;
  select coalesce(display_name, 'Una familia')  into v_gen   from public.profiles where id = new.generator_id;

  if new.rider_id is not null and (tg_op = 'INSERT' or old.rider_id is distinct from new.rider_id) then
    insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push) values
      (new.generator_id, 'rider_assigned', coalesce(v_rider, 'Un Eco-Rider') || ' recogerá tu reciclaje',
       coalesce('Llega en ~' || new.eta_minutes || ' min. ', '') || 'Síguelo en vivo desde la app.', 'pickup', new.id, 'urgent'),
      (new.rider_id, 'rider_assigned', 'Nuevo recojo asignado',
       v_gen || coalesce(' · ' || new.window_label, '') || '. Ábrelo en tu ruta.', 'pickup', new.id, 'urgent');
  end if;

  if tg_op = 'UPDATE' and new.status is distinct from old.status then
    case new.status
      when 'en_route' then
        insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push)
        values (new.generator_id, 'pickup_update', 'Tu Eco-Rider va en camino',
                v_rider || ' salió hacia tu casa. Míralo acercarse en el mapa.', 'pickup', new.id, 'urgent');
      when 'arriving' then
        insert into public.notifications (user_id, kind, title, body, reference_type, reference_id)
        values (new.generator_id, 'rider_arriving', 'Tu Eco-Rider está llegando',
                v_rider || ' está a pocos metros. ¡Ten listos tus residuos!', 'pickup', new.id);
      when 'collecting' then
        insert into public.notifications (user_id, kind, title, body, reference_type, reference_id)
        values (new.generator_id, 'pickup_update', v_rider || ' llegó',
                'Está recogiendo y contando tus residuos.', 'pickup', new.id);
      when 'completed' then
        insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push) values
          (new.generator_id, 'points_earned', 'Recojo completado',
           coalesce('+' || nullif(new.generator_points_awarded, 0) || ' Eco-Puntos por confirmar en el centro de acopio. ', '')
             || '¡Gracias por reciclar!', 'pickup', new.id, 'urgent');
        if new.rider_id is not null then
          insert into public.notifications (user_id, kind, title, body, reference_type, reference_id)
          values (new.rider_id, 'points_earned', 'Recojo completado',
                  'Ganaste S/' || to_char(coalesce(new.rider_earnings, 0), 'FM990.00') || ' por el recojo de ' || v_gen || '.',
                  'pickup', new.id);
        end if;
      when 'cancelled' then
        if new.rider_id is not null then
          insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push)
          values (new.rider_id, 'pickup_update', 'Recojo cancelado',
                  v_gen || ' canceló el recojo. Ya no está en tu ruta.', 'pickup', new.id, 'urgent');
        end if;
      when 'requested' then
        if old.rider_id is not null then
          select coalesce(display_name, 'Tu Eco-Rider') into v_prev from public.profiles where id = old.rider_id;
          insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push)
          values (new.generator_id, 'pickup_update', 'Buscando otro Eco-Rider',
                  v_prev || ' no podrá ir. Tu recojo volvió a la cola y otro Eco-Rider lo tomará.', 'pickup', new.id, 'urgent');
        end if;
      else null;
    end case;
  end if;
  return null;
end
$$;

-- 5. Reintentos y limpieza --------------------------------------------------------------------------------------

select cron.schedule('ruma-push-dispatch', '* * * * *', $cron$select public.push_dispatch()$cron$);
select cron.schedule('ruma-push-cleanup', '17 9 * * *', $cron$
  delete from public.push_queue where expires_at < now() - interval '30 days';
  delete from cron.job_run_details where end_time < now() - interval '3 days';
$cron$);

commit;
