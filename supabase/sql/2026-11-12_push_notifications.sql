-- Ruma: avisos push con la app cerrada (2026-11-12). Guía de puesta en marcha: docs/push-setup.md.
-- APLICADO en STAGING el 2026-11-12 (junto con 2026-11-12_push_reglas.sql); función send-push desplegada ahí.
-- Verificar con supabase/tests/push_staging.py. Los secretos (vault + función) siguen sin cargar en STAGING.
-- Falta repetirlo en producción (este script, luego push_reglas): requiere pg_net y pg_cron.
--
-- Cada fila nueva en public.notifications (las escribe trg_pickup_notify y las demás reglas de la base) se envía
-- a los celulares del usuario: un disparador llama, vía pg_net, a la Edge Function send-push, que la manda por
-- Firebase Cloud Messaging. El cliente solo registra su token; nunca lee los de otros ni decide qué se envía.
--
-- Requisitos fuera de este script (ver supabase/functions/send-push/index.ts):
--   * secreto de la función PUSH_HOOK_SECRET = el mismo valor guardado abajo en vault como push_hook_secret;
--   * secreto FCM_SERVICE_ACCOUNT = JSON de la cuenta de servicio de Firebase (sin él la función no envía nada).
-- El valor del secreto del disparador se inserta aparte (no va en el repo):
--   select vault.create_secret('<valor>', 'push_hook_secret');

begin;

create extension if not exists pg_net with schema extensions;

-- Tokens de los dispositivos. Sin lectura ni escritura directa desde la app: solo vía las RPC de abajo.
create table if not exists public.push_tokens (
  token text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  platform text not null default 'android' check (platform in ('android', 'web')),
  updated_at timestamptz not null default now()
);
create index if not exists push_tokens_user_idx on public.push_tokens (user_id);
alter table public.push_tokens enable row level security;
revoke all on public.push_tokens from anon, authenticated;

-- Un mismo teléfono puede cambiar de cuenta: el token pasa a quien inició sesión ahora.
create or replace function public.register_push_token(p_token text, p_platform text default 'android')
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'Inicia sesión para recibir avisos.' using errcode = '42501';
  end if;
  if p_token is null or length(p_token) < 20 or length(p_token) > 4096 then
    raise exception 'Token de avisos inválido.' using errcode = '22023';
  end if;
  insert into public.push_tokens (token, user_id, platform, updated_at)
  values (p_token, auth.uid(), coalesce(nullif(p_platform, ''), 'android'), now())
  on conflict (token) do update set user_id = excluded.user_id, platform = excluded.platform, updated_at = now();
end;
$$;

-- Al cerrar sesión el teléfono deja de recibir los avisos de esa cuenta.
create or replace function public.unregister_push_token(p_token text)
returns void
language sql
security definer
set search_path = public
as $$
  delete from public.push_tokens where token = p_token and user_id = auth.uid();
$$;

revoke all on function public.register_push_token(text, text) from public, anon;
revoke all on function public.unregister_push_token(text) from public, anon;
grant execute on function public.register_push_token(text, text) to authenticated;
grant execute on function public.unregister_push_token(text) to authenticated;

-- Disparador: solo si el usuario tiene algún teléfono registrado y el secreto existe.
create or replace function public.push_notification()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_secret text;
begin
  if not exists (select 1 from public.push_tokens where user_id = new.user_id) then
    return new;
  end if;
  select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'push_hook_secret';
  if v_secret is null then
    return new;
  end if;
  perform net.http_post(
    url := 'https://mbbdrmicjobydprvbpsj.supabase.co/functions/v1/send-push',
    body := jsonb_build_object('id', new.id),
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-push-secret', v_secret)
  );
  return new;
end;
$$;
revoke all on function public.push_notification() from public, anon, authenticated;

drop trigger if exists trg_notification_push on public.notifications;
create trigger trg_notification_push
after insert on public.notifications
for each row execute function public.push_notification();

commit;
