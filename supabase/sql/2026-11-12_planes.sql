-- Ruma: planes de pago para hogares (Ruma Plus), con pago manual por Yape (2026-11-12).
-- APLICADO en STAGING el 2026-11-12 y verificado con supabase/tests/planes_staging.py (21/21) y, sin
-- regresión, con supabase/tests/canje_seguro_staging.py (5/5).
-- 2026-11-16, STAGING: los boletos del sorteo pasan a 'pronto' en plans.features (la base ya los cuenta, pero
-- todavía no hay premio ni fecha publicados) y el catálogo vuelve a escribir features al re-ejecutarse.
-- Falta repetirlo en producción (después de 2026-11-11_arbitrios_porcentaje.sql: este script redefine
-- validate_redemption sin quitarle nada).
-- Investigación, precios y límites éticos: docs/planes-de-pago.md.
--
-- Regla de siempre: el cliente pide, la base decide. Ningún beneficio depende de un flag de la app.
--   * plans: catálogo editable desde la base (precio mensual y anual, prueba, beneficios y la lista que ve
--     la gente). benefits guarda SOLO lo que la base aplica; features es texto, con status 'activo' o 'pronto'.
--   * plan_subscriptions: pending_payment -> active -> expired, o cancelled. El cliente no tiene INSERT ni
--     UPDATE: pide con request_plan / start_plan_trial / cancel_my_plan, y solo un operador
--     (is_operator()) activa con activate_plan_subscription o rechaza con reject_plan_payment.
--   * plan_payment_config: el número / QR de Yape del negocio. Queda en NULL (placeholder) hasta que el
--     dueño lo cargue; las apps dicen «se publicará pronto» y la solicitud igual se guarda.
--   * user_benefits(uid): los beneficios en vigor AHORA (status active y now() entre starts_at y ends_at).
--     Vencer no depende del cron: una suscripción pasada de fecha deja de dar beneficios en ese instante.
--     El cron diario y my_plan() solo ponen el status en 'expired' para que se lea bien.
-- Beneficios aplicados de verdad en la base:
--   1. redeem_min_points: mínimo de canje en efectivo (Yape/Plin) más bajo. validate_redemption usa el menor
--      entre el de la opción y el del plan; sigue exigiendo unidades enteras (1.000 pts = S/ 1).
--   2. dispatch_priority: trg_pickup_priority fija pickups.priority al crear el recojo (nunca la app; un
--      PATCH no la cambia) y el tablero de los riders ordena por priority antes que por antigüedad.
--   3. raffle_tickets_bonus: raffle_tickets(uid, mes) cuenta los boletos del sorteo mensual: 1 por recojo
--      pesado en el mes (tope 4) y, si hubo al menos uno y el plan estuvo vigente en ese mes, el bono.
--      Pagar no da boletos sin reciclar: sin recojo pesado, cero.
--   4. referral_bonus_points: lo aplica referral_prize, de 2026-11-12_referidos.sql (ese script va DESPUÉS de
--      este), sumándolo al premio del invitante con plan vigente. Ningún plan lo define hoy: es decisión del
--      dueño (update plans set benefits = benefits || '{"referral_bonus_points":500}' where code = 'PLUS').
--   El sorteo: raffle_tickets ya cuenta los boletos, pero no hay sorteo corriendo; por eso en features van
--   como 'pronto'. Pásalos a 'activo' cuando el premio, la fecha y las bases estén publicados.
-- Pasarela (Culqi / Mercado Pago) después: su webhook verificado llamaría a la misma lógica de
-- activate_plan_subscription (payment_method 'culqi' / 'mercadopago', payment_reference = id del cargo),
-- desde una Edge Function con la service key, nunca desde la app.

begin;

-- ---------- Catálogo ----------

create table if not exists public.plans (
  code         text primary key,
  audience     text not null default 'hogar' check (audience in ('hogar', 'negocio')),
  name         text not null,
  tagline      text,
  price_month  numeric(10,2) not null default 0 check (price_month >= 0),
  price_year   numeric(10,2) check (price_year is null or price_year >= 0),
  trial_days   integer not null default 0 check (trial_days between 0 and 60),
  benefits     jsonb not null default '{}'::jsonb,
  features     jsonb not null default '[]'::jsonb,
  is_active    boolean not null default true,
  sort_order   integer not null default 0,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
comment on column public.plans.benefits is
  'Lo que la base aplica: redeem_min_points, dispatch_priority, raffle_tickets_bonus, referral_bonus_points.';
comment on column public.plans.features is
  'Lo que ve la gente: [{title, detail, status: activo|pronto}]. Texto, no aplica nada.';

insert into public.plans (code, audience, name, tagline, price_month, price_year, trial_days, benefits, features, sort_order)
values
  ('GRATIS', 'hogar', 'Ruma', 'Todo lo que necesitas para reciclar y cobrar', 0, null, 0, '{}'::jsonb,
   '[{"title":"Recojo en tu puerta","detail":"Pides con una foto y un Eco-Rider pasa","status":"activo"},
     {"title":"Puntos por tu material pesado","detail":"1.000 pts = S/ 1","status":"activo"},
     {"title":"Cobro por Yape o Plin","detail":"Desde S/ 5","status":"activo"},
     {"title":"1 boleto del sorteo por recojo pesado","detail":"Hasta 4 al mes, cuando publiquemos el premio","status":"pronto"},
     {"title":"Rumi, tu asistente","detail":"Qué va en cada tacho y cuánto vale","status":"activo"}]'::jsonb, 0),
  ('PLUS', 'hogar', 'Ruma Plus', 'Más comodidad, no más puntos', 4.90, 39.90, 14,
   '{"redeem_min_points":1000,"dispatch_priority":1,"raffle_tickets_bonus":2}'::jsonb,
   '[{"title":"Cobra desde S/ 1","detail":"Sin esperar a juntar S/ 5","status":"activo"},
     {"title":"Tu recojo primero en el tablero","detail":"Los Eco-Riders lo ven antes que los demás","status":"activo"},
     {"title":"+2 boletos del sorteo al mes","detail":"Si ese mes tuviste al menos un recojo pesado","status":"pronto"},
     {"title":"Recojo con hora fija","detail":"Elige tu franja de la semana","status":"pronto"},
     {"title":"Protección de racha","detail":"Una semana libre al mes sin perder tu racha","status":"pronto"},
     {"title":"Ofertas del Mercadito solo para Plus","detail":"Descuentos de bodegas del barrio","status":"pronto"},
     {"title":"Plan familiar","detail":"Hasta 3 hogares de la misma familia","status":"pronto"}]'::jsonb, 1)
-- features es el texto de este script; precios y benefits editados en la base se respetan.
on conflict (code) do update set features = excluded.features;

-- ---------- Suscripciones ----------

do $$ begin
  create type public.plan_status as enum ('pending_payment', 'active', 'expired', 'cancelled');
exception when duplicate_object then null; end $$;

create table if not exists public.plan_subscriptions (
  id                uuid primary key default gen_random_uuid(),
  user_id           uuid not null references public.profiles(id) on delete cascade,
  plan_code         text not null references public.plans(code),
  period            text not null check (period in ('month', 'year', 'trial')),
  amount            numeric(10,2) not null check (amount >= 0),
  status            public.plan_status not null default 'pending_payment',
  reference         text not null unique,
  payment_method    text not null default 'yape_manual',
  payment_reference text,
  starts_at         timestamptz,
  ends_at           timestamptz,
  activated_by      uuid references public.profiles(id),
  activated_at      timestamptz,
  cancelled_at      timestamptz,
  cancel_reason     text,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  check (status <> 'active' or (starts_at is not null and ends_at > starts_at))
);
create index if not exists plan_subscriptions_user_idx on public.plan_subscriptions (user_id, status);
-- One open request per person: a second tap returns the same reference instead of piling up debts.
create unique index if not exists plan_subscriptions_one_pending
  on public.plan_subscriptions (user_id) where status = 'pending_payment';

create table if not exists public.plan_payment_config (
  id           boolean primary key default true check (id),
  yape_number  text,           -- NULL hasta que el dueño cargue el número real del negocio
  yape_holder  text,
  yape_qr_url  text,
  instructions text not null default 'Yapea el monto exacto y escribe tu código en el mensaje. Activamos tu plan en menos de 24 horas.',
  updated_at   timestamptz not null default now()
);
-- «¿Tienes un negocio? Escríbenos»: un enlace https://wa.me/… o mailto:…; NULL hasta que el dueño lo cargue.
alter table public.plan_payment_config add column if not exists business_contact text;
insert into public.plan_payment_config (id) values (true) on conflict do nothing;

drop trigger if exists trg_plans_updated on public.plans;
create trigger trg_plans_updated before update on public.plans for each row execute function public.set_updated_at();
drop trigger if exists trg_plan_subscriptions_updated on public.plan_subscriptions;
create trigger trg_plan_subscriptions_updated before update on public.plan_subscriptions for each row execute function public.set_updated_at();

-- Supabase grants everything on new tables to anon/authenticated: take it back, give back reads only.
revoke all on public.plans, public.plan_subscriptions, public.plan_payment_config from anon, authenticated;
grant select on public.plans to anon, authenticated;
grant select on public.plan_subscriptions, public.plan_payment_config to authenticated;

alter table public.plans enable row level security;
alter table public.plan_subscriptions enable row level security;
alter table public.plan_payment_config enable row level security;

drop policy if exists plans_read on public.plans;
create policy plans_read on public.plans for select using (is_active);
drop policy if exists plan_subscriptions_read on public.plan_subscriptions;
create policy plan_subscriptions_read on public.plan_subscriptions for select to authenticated
  using (user_id = (select auth.uid()) or (select public.is_operator()));
drop policy if exists plan_payment_config_read on public.plan_payment_config;
create policy plan_payment_config_read on public.plan_payment_config for select to authenticated using (true);

-- ---------- Beneficios en vigor ----------

create or replace function public.user_benefits(p_user uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select coalesce((
    select p.benefits
      from public.plan_subscriptions s
      join public.plans p on p.code = s.plan_code
     where s.user_id = p_user
       and s.status = 'active'
       and s.starts_at <= now() and s.ends_at > now()
     order by s.ends_at desc
     limit 1), '{}'::jsonb)
$$;
revoke execute on function public.user_benefits(uuid) from public, anon, authenticated;

/** Raffle tickets of [p_month] (any day in it, Lima calendar): weighed pickups capped at 4, plus the plan's bonus. */
create or replace function public.raffle_tickets(p_user uuid, p_month date default (now() at time zone 'America/Lima')::date)
returns table (base integer, bonus integer, total integer)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_from timestamptz := (date_trunc('month', p_month)::timestamp) at time zone 'America/Lima';
  v_to   timestamptz := ((date_trunc('month', p_month) + interval '1 month')::timestamp) at time zone 'America/Lima';
  v_base int;
  v_bonus int := 0;
begin
  select least(count(*), 4)::int into v_base
    from public.pickups
   where generator_id = p_user and status = 'completed'
     and points_status in ('confirmado', 'ajustado')
     and completed_at >= v_from and completed_at < v_to;
  if v_base > 0 then
    select coalesce(max((p.benefits->>'raffle_tickets_bonus')::int), 0) into v_bonus
      from public.plan_subscriptions s join public.plans p on p.code = s.plan_code
     where s.user_id = p_user and s.status in ('active', 'expired')
       and s.starts_at < v_to and s.ends_at > v_from;
  end if;
  return query select v_base, v_bonus, v_base + v_bonus;
end;
$$;
revoke execute on function public.raffle_tickets(uuid, date) from public, anon, authenticated;

create or replace function public.expire_plan_subscriptions()
returns integer
language sql
security definer
set search_path = public
as $$
  with done as (
    update public.plan_subscriptions set status = 'expired'
     where status = 'active' and ends_at <= now() returning 1)
  select count(*)::int from done
$$;
revoke execute on function public.expire_plan_subscriptions() from public, anon, authenticated;

-- ---------- Lo que pide la app ----------

/** Everything the Planes screen needs in one call: the plan in force, an open request, benefits, how to pay. */
create or replace function public.my_plan()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_out jsonb;
begin
  if v_uid is null then raise exception 'Tu sesión expiró. Vuelve a entrar.' using errcode = '28000'; end if;
  update public.plan_subscriptions set status = 'expired'
   where user_id = v_uid and status = 'active' and ends_at <= now();
  select jsonb_build_object(
    'active', (select jsonb_build_object('id', s.id, 'plan_code', s.plan_code, 'name', p.name, 'period', s.period,
                        'starts_at', s.starts_at, 'ends_at', s.ends_at, 'cancelled_at', s.cancelled_at,
                        'payment_method', s.payment_method)
                 from public.plan_subscriptions s join public.plans p on p.code = s.plan_code
                where s.user_id = v_uid and s.status = 'active' and s.starts_at <= now() and s.ends_at > now()
                order by s.ends_at desc limit 1),
    -- A renewal paid early starts when the current one ends: the plan runs until the last one.
    'active_until', (select max(ends_at) from public.plan_subscriptions where user_id = v_uid and status = 'active'),
    'pending', (select jsonb_build_object('id', s.id, 'plan_code', s.plan_code, 'name', p.name, 'period', s.period,
                         'amount', s.amount, 'reference', s.reference, 'created_at', s.created_at)
                  from public.plan_subscriptions s join public.plans p on p.code = s.plan_code
                 where s.user_id = v_uid and s.status = 'pending_payment' limit 1),
    'trial_available', not exists (select 1 from public.plan_subscriptions
                                    where user_id = v_uid and (period = 'trial' or status in ('active', 'expired'))),
    'benefits', public.user_benefits(v_uid),
    'tickets', (select to_jsonb(t) from public.raffle_tickets(v_uid) t),
    'payment', (select jsonb_build_object('yape_number', c.yape_number, 'yape_holder', c.yape_holder,
                         'yape_qr_url', c.yape_qr_url, 'instructions', c.instructions,
                         'business_contact', c.business_contact)
                  from public.plan_payment_config c limit 1)
  ) into v_out;
  return v_out;
end;
$$;

/** Opens (or returns the open) payment request. The amount comes from the catalog, never from the app. */
create or replace function public.request_plan(p_plan text, p_period text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_plan public.plans;
  v_amount numeric;
  v_sub public.plan_subscriptions;
  v_ref text;
begin
  if v_uid is null then raise exception 'Tu sesión expiró. Vuelve a entrar.' using errcode = '28000'; end if;
  if not exists (select 1 from public.profiles where id = v_uid and role = 'generador') then
    raise exception 'Los planes son para hogares.' using errcode = '42501';
  end if;
  select * into v_plan from public.plans where code = p_plan and is_active and audience = 'hogar';
  if not found or v_plan.price_month <= 0 then
    raise exception 'Ese plan no está disponible.' using errcode = '22023';
  end if;
  v_amount := case p_period when 'month' then v_plan.price_month when 'year' then v_plan.price_year end;
  if v_amount is null or v_amount <= 0 then
    raise exception 'Elige pago mensual o anual.' using errcode = '22023';
  end if;

  select * into v_sub from public.plan_subscriptions where user_id = v_uid and status = 'pending_payment';
  if found then
    if v_sub.plan_code = p_plan and v_sub.period = p_period then
      return jsonb_build_object('id', v_sub.id, 'reference', v_sub.reference, 'amount', v_sub.amount, 'period', v_sub.period);
    end if;
    -- Changing the choice before paying replaces the request: nothing was charged yet.
    update public.plan_subscriptions set status = 'cancelled', cancelled_at = now(), cancel_reason = 'Cambió de opción'
     where id = v_sub.id;
  end if;

  loop
    -- No 0/O/1/I: the code is read off a phone and typed into a Yape message.
    v_ref := 'RUMA-' || (select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + floor(random() * 32)::int, 1), '')
                           from generate_series(1, 6));
    exit when not exists (select 1 from public.plan_subscriptions where reference = v_ref);
  end loop;
  insert into public.plan_subscriptions (user_id, plan_code, period, amount, reference)
  values (v_uid, p_plan, p_period, v_amount, v_ref)
  returning * into v_sub;
  return jsonb_build_object('id', v_sub.id, 'reference', v_sub.reference, 'amount', v_sub.amount, 'period', v_sub.period);
end;
$$;

/** The free trial: once per person, ever, with no payment data and nothing that renews by itself. */
create or replace function public.start_plan_trial(p_plan text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_plan public.plans;
  v_sub public.plan_subscriptions;
begin
  if v_uid is null then raise exception 'Tu sesión expiró. Vuelve a entrar.' using errcode = '28000'; end if;
  if not exists (select 1 from public.profiles where id = v_uid and role = 'generador') then
    raise exception 'Los planes son para hogares.' using errcode = '42501';
  end if;
  select * into v_plan from public.plans where code = p_plan and is_active and audience = 'hogar' and trial_days > 0;
  if not found then raise exception 'Este plan no tiene prueba gratis.' using errcode = '22023'; end if;
  -- The lock serialises two taps: only one trial row can ever be written per person.
  perform 1 from public.generator_profiles where profile_id = v_uid for update;
  if exists (select 1 from public.plan_subscriptions where user_id = v_uid and (period = 'trial' or status in ('active', 'expired'))) then
    raise exception 'La prueba gratis es para quien nunca tuvo el plan.' using errcode = '22023';
  end if;
  insert into public.plan_subscriptions (user_id, plan_code, period, amount, status, reference, payment_method,
                                         starts_at, ends_at, activated_at)
  values (v_uid, p_plan, 'trial', 0, 'active', 'PRUEBA-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
          'trial', now(), now() + make_interval(days => v_plan.trial_days), now())
  returning * into v_sub;
  return jsonb_build_object('id', v_sub.id, 'ends_at', v_sub.ends_at);
end;
$$;

/**
 * Cancelling is one tap and needs no reason. A request not yet paid is withdrawn. A plan already paid
 * keeps its benefits until the date it was paid for: nothing renews by itself, so nothing else is charged.
 */
create or replace function public.cancel_my_plan(p_subscription_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_sub public.plan_subscriptions;
begin
  select * into v_sub from public.plan_subscriptions where id = p_subscription_id and user_id = v_uid for update;
  if not found then raise exception 'No encontramos ese plan.' using errcode = '22023'; end if;
  if v_sub.status = 'pending_payment' then
    update public.plan_subscriptions set status = 'cancelled', cancelled_at = now(), cancel_reason = 'Retirada por el usuario'
     where id = v_sub.id;
    return 'Solicitud retirada. No se cobró nada.';
  elsif v_sub.status = 'active' then
    update public.plan_subscriptions set cancelled_at = coalesce(cancelled_at, now()), cancel_reason = 'Cancelado por el usuario'
     where id = v_sub.id;
    return 'Listo: no se renovará. Mantienes tus beneficios hasta el ' ||
           to_char(v_sub.ends_at at time zone 'America/Lima', 'DD/MM/YYYY') || '.';
  end if;
  raise exception 'Ese plan ya no está activo.' using errcode = '22023';
end;
$$;

-- ---------- Lo que hace el operador ----------

/** Only an operator, after seeing the Yape arrive. A renewal paid early queues after the current period. */
create or replace function public.activate_plan_subscription(p_subscription_id uuid, p_payment_reference text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_sub public.plan_subscriptions;
  v_start timestamptz;
  v_end timestamptz;
begin
  if not public.is_operator() then
    raise exception 'Solo un operador de Ruma puede activar un plan.' using errcode = '42501';
  end if;
  if coalesce(trim(p_payment_reference), '') = '' then
    raise exception 'Anota el número de operación del Yape.' using errcode = '22023';
  end if;
  select * into v_sub from public.plan_subscriptions where id = p_subscription_id for update;
  if not found or v_sub.status <> 'pending_payment' then
    raise exception 'Esa solicitud no está pendiente de pago.' using errcode = '22023';
  end if;
  select greatest(now(), coalesce(max(ends_at), now())) into v_start
    from public.plan_subscriptions where user_id = v_sub.user_id and status = 'active';
  v_end := v_start + case v_sub.period when 'year' then interval '1 year' else interval '1 month' end;
  update public.plan_subscriptions
     set status = 'active', starts_at = v_start, ends_at = v_end, payment_reference = trim(p_payment_reference),
         activated_by = auth.uid(), activated_at = now()
   where id = v_sub.id;
  insert into public.notifications (user_id, kind, title, body, reference_type, reference_id)
  values (v_sub.user_id, 'system', 'Tu plan Ruma Plus está activo',
          'Recibimos tu pago. Tus beneficios valen hasta el ' || to_char(v_end at time zone 'America/Lima', 'DD/MM/YYYY') ||
          '. No se renueva solo.', 'plan_subscription', v_sub.id);
  return jsonb_build_object('id', v_sub.id, 'starts_at', v_start, 'ends_at', v_end);
end;
$$;

create or replace function public.reject_plan_payment(p_subscription_id uuid, p_reason text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_operator() then
    raise exception 'Solo un operador de Ruma puede rechazar un pago.' using errcode = '42501';
  end if;
  update public.plan_subscriptions
     set status = 'cancelled', cancelled_at = now(), cancel_reason = coalesce(nullif(trim(p_reason), ''), 'Pago no recibido')
   where id = p_subscription_id and status = 'pending_payment';
  if not found then raise exception 'Esa solicitud no está pendiente de pago.' using errcode = '22023'; end if;
end;
$$;

revoke execute on function public.my_plan(), public.request_plan(text, text), public.start_plan_trial(text),
  public.cancel_my_plan(uuid), public.activate_plan_subscription(uuid, text), public.reject_plan_payment(uuid, text)
  from public, anon;
grant execute on function public.my_plan(), public.request_plan(text, text), public.start_plan_trial(text),
  public.cancel_my_plan(uuid), public.activate_plan_subscription(uuid, text), public.reject_plan_payment(uuid, text)
  to authenticated;

-- ---------- Beneficio 2: prioridad en el despacho ----------

alter table public.pickups add column if not exists priority smallint not null default 0;
create index if not exists pickups_board_idx on public.pickups (priority desc, created_at) where status = 'requested' and rider_id is null;

create or replace function public.set_pickup_priority()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op = 'INSERT' then
    new.priority := coalesce((public.user_benefits(new.generator_id)->>'dispatch_priority')::smallint, 0);
  else
    new.priority := old.priority;  -- set once, by the database: a PATCH from any app can't buy a place in line
  end if;
  return new;
end;
$$;
revoke execute on function public.set_pickup_priority() from public, anon, authenticated;
drop trigger if exists trg_pickup_priority on public.pickups;
create trigger trg_pickup_priority before insert or update of priority on public.pickups
  for each row execute function public.set_pickup_priority();

-- ---------- Beneficio 1: mínimo de canje en efectivo (definición de 2026-11-11_arbitrios_porcentaje.sql + el plan) ----------

create or replace function public.validate_redemption()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_balance integer;
  v_opt public.redemption_options;
  v_max numeric;
  v_per numeric;
  v_used numeric;
  v_min integer;
begin
  if new.option_id is null then
    raise exception 'Elige una opción de canje.' using errcode = '22023';
  end if;
  select * into v_opt from public.redemption_options where id = new.option_id;
  if not found or not v_opt.is_active then
    raise exception 'Esa opción de canje no está disponible.' using errcode = '22023';
  end if;
  -- A plan in force can lower the cash minimum; never below one whole unit, which the next check keeps.
  v_min := v_opt.min_points;
  if v_opt.kind = 'cash' then
    v_min := least(v_min, coalesce(nullif(public.user_benefits(new.user_id)->>'redeem_min_points', '')::int, v_min));
  end if;
  if new.points_spent is null or new.points_spent < greatest(v_min, 1) then
    raise exception 'El canje mínimo es de % pts.', v_min using errcode = '22023';
  end if;
  if v_opt.points_per_unit > 0 and mod(new.points_spent, v_opt.points_per_unit) <> 0 then
    raise exception 'Canjea en múltiplos de % pts.', v_opt.points_per_unit::int using errcode = '22023';
  end if;

  -- The server values the redemption; whatever the client sent is overwritten.
  new.kind := v_opt.kind;
  new.monetary_value := case when v_opt.points_per_unit > 0
                             then round(new.points_spent / v_opt.points_per_unit * v_opt.unit_value, 2) else 0 end;
  new.quantity := case when v_opt.points_per_unit > 0 then new.points_spent / v_opt.points_per_unit else 1 end;
  new.status := 'pending';
  new.processed_at := null;
  new.payout_reference := null;

  -- The wallet row lock also serialises this user's yearly cap: two taps can't both fit under it.
  select points_balance into v_balance
    from public.generator_profiles where profile_id = new.user_id for update;
  if v_balance is null then
    raise exception 'No encontramos tu billetera de Eco-Puntos.' using errcode = '22023';
  end if;

  v_max := nullif(v_opt.metadata->>'max_percent_year', '')::numeric;
  if v_max is not null then
    v_per := coalesce(nullif(v_opt.metadata->>'percent_per_unit', '')::numeric, 1);
    select coalesce(sum(r.quantity), 0) * v_per into v_used
      from public.redemptions r
     where r.user_id = new.user_id
       and r.option_id = new.option_id
       and r.status not in ('cancelled', 'failed')
       and (r.created_at at time zone 'America/Lima') >= date_trunc('year', now() at time zone 'America/Lima');
    if v_used + new.quantity * v_per > v_max then
      raise exception 'Ya usaste el % %% de descuento de este año (máximo % %%).',
        trim_scale(v_used), trim_scale(v_max) using errcode = '22023';
    end if;
  end if;

  if v_balance < new.points_spent then
    raise exception 'Saldo insuficiente: tienes % pts.', v_balance using errcode = '22023';
  end if;
  return new;
end;
$$;

commit;

-- Fuera de la transacción: el cron diario que deja el status en 'expired' (los beneficios ya vencen solos).
select cron.unschedule('ruma-plans-expire') where exists (select 1 from cron.job where jobname = 'ruma-plans-expire');
select cron.schedule('ruma-plans-expire', '5 5 * * *', 'select public.expire_plan_subscriptions()');
