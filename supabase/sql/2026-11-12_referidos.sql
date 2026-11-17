-- Ruma: referidos que pagan al primer pesaje (2026-11-12). Análisis y cifras en docs/referidos.md.
-- APLICADO en STAGING el 2026-11-12 y verificado con supabase/tests/referidos_staging.py (13/13).
-- 2026-11-16, STAGING: referral_prize suma el bono del plan del invitante (requiere user_benefits, de
-- 2026-11-12_planes.sql: aplica ese script ANTES que este).
-- Falta repetirlo en producción. Reemplaza handle_new_user: reléelo en producción antes de aplicarlo allí.
--
-- Reglas (todas en la base; la app solo pide):
--   * Cada recolector (rol generador) tiene un código de 6 caracteres que genera la base (referral_codes).
--   * El invitado queda atribuido UNA sola vez (referrals.referred_id es único): en el registro, por el metadato
--     referral_code del sign-up, o después con rpc/apply_referral_code, solo en sus primeros
--     platform_settings.referral_attach_days días y nunca después de haber tenido un pesaje. Nunca a sí mismo.
--   * El premio se paga en PUNTOS, por el libro point_transactions (tipo 'referral'), cuando lo pesado en el acopio
--     del invitado llega a referral_min_kg. Lo dispara settle_delivery al fijar pickups.generator_points_final.
--     Nunca al registrarse. Un índice único impide pagar dos veces la misma invitación a la misma persona.
--   * Antes de pagar se rechaza (sin pagar a nadie) si el invitante y el invitado comparten teléfono o dirección,
--     o si el rider de ese recojo es el invitante o comparte teléfono con alguno de los dos.
--   * Topes: referral_monthly_cap pagados por invitante al mes y referral_global_monthly_cap en toda la plataforma.
--     Pasado el tope la invitación sigue 'pendiente' (motivo 'tope_mensual') y se paga en un pesaje del mes siguiente.
--   * Los montos se leen de platform_settings a través de referral_prize(invitante), que le suma al invitante
--     el referral_bonus_points de su plan vigente: subir el premio es un UPDATE, no un cambio de código.
--   * referrals y referral_codes no tienen permisos de escritura para la app; se leen con rpc/my_referral_summary.

begin;

-- ---------- Configuración (un plan puede leer otra cosa en referral_prize) ----------
alter table public.platform_settings
  add column if not exists referral_referrer_points   integer      not null default 2000,
  add column if not exists referral_referred_points   integer      not null default 1000,
  add column if not exists referral_min_kg            numeric(6,2) not null default 1,
  add column if not exists referral_monthly_cap       integer      not null default 5,
  add column if not exists referral_global_monthly_cap integer     not null default 60,
  add column if not exists referral_attach_days       integer      not null default 7;

-- ---------- Tablas ----------
create table if not exists public.referral_codes (
  profile_id uuid primary key references public.profiles(id) on delete cascade,
  -- Sin 0/O, 1/I/L: se dicta por teléfono y se escribe a mano.
  code text not null unique check (code ~ '^[A-HJKMNP-Z2-9]{6}$'),
  created_at timestamptz not null default now()
);

create table if not exists public.referrals (
  id uuid primary key default gen_random_uuid(),
  referrer_id uuid not null references public.profiles(id) on delete cascade,
  referred_id uuid not null unique references public.profiles(id) on delete cascade,
  code text not null,
  status text not null default 'pendiente' check (status in ('pendiente', 'pagado', 'rechazado')),
  -- Pendiente: null o 'tope_mensual'. Rechazado: mismo_telefono, misma_direccion, rider_vinculado.
  reason text,
  referrer_points integer not null default 0,
  referred_points integer not null default 0,
  qualifying_pickup_id uuid references public.pickups(id) on delete set null,
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  check (referrer_id <> referred_id)
);
create index if not exists idx_referrals_referrer on public.referrals (referrer_id, status, decided_at);
create index if not exists idx_referrals_paid on public.referrals (decided_at) where status = 'pagado';
create index if not exists idx_referrals_pickup on public.referrals (qualifying_pickup_id);

alter table public.referral_codes enable row level security;
alter table public.referrals enable row level security;
revoke all on public.referral_codes, public.referrals from anon, authenticated;
grant select on public.referral_codes, public.referrals to authenticated;
drop policy if exists referral_codes_select on public.referral_codes;
create policy referral_codes_select on public.referral_codes for select to authenticated
  using (profile_id = (select auth.uid()) or (select public.is_operator()));
drop policy if exists referrals_select on public.referrals;
create policy referrals_select on public.referrals for select to authenticated
  using (referrer_id = (select auth.uid()) or referred_id = (select auth.uid()) or (select public.is_operator()));

-- Una invitación paga a cada persona una sola vez.
create unique index if not exists uq_point_txn_referral
  on public.point_transactions (user_id, reference_id) where reference_type = 'referral';

-- ---------- Funciones internas ----------
create or replace function public.phone_key(p text)
returns text language sql immutable set search_path = public as $$
  select nullif(right(regexp_replace(coalesce(p, ''), '\D', '', 'g'), 9), '')
$$;

create or replace function public.referral_code_for(p_profile uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v text;
  a constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
begin
  select code into v from public.referral_codes where profile_id = p_profile;
  if found then return v; end if;
  loop
    v := '';
    for i in 1..6 loop
      v := v || substr(a, 1 + (get_byte(extensions.gen_random_bytes(1), 0) % length(a)), 1);
    end loop;
    begin
      insert into public.referral_codes (profile_id, code) values (p_profile, v);
      return v;
    exception when unique_violation then
      -- Same code taken (retry) or a concurrent call already gave this person one.
      select code into v from public.referral_codes where profile_id = p_profile;
      if found then return v; end if;
    end;
  end loop;
end;
$$;

-- Lo que paga una invitación de este invitante: la configuración, más el bono del plan vigente del invitante
-- (plans.benefits.referral_bonus_points, leído con user_benefits de 2026-11-12_planes.sql). Hoy ningún plan lo define.
create or replace function public.referral_prize(p_referrer uuid, out referrer_points integer, out referred_points integer)
language sql stable security definer set search_path = public as $$
  select s.referral_referrer_points
           + greatest(coalesce((public.user_benefits(p_referrer)->>'referral_bonus_points')::integer, 0), 0),
         s.referral_referred_points
    from public.platform_settings s where s.id
$$;

create or replace function public.attach_referral(p_referred uuid, p_code text)
returns public.referrals
language plpgsql
security definer
set search_path = public
as $$
declare
  v_code text := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  v_profile public.profiles;
  v_referrer uuid;
  v_days integer;
  v_row public.referrals;
begin
  select * into v_profile from public.profiles where id = p_referred;
  if not found or v_profile.role <> 'generador' then
    raise exception 'Solo las familias recicladoras pueden usar un código de invitación.' using errcode = '22023';
  end if;
  if exists (select 1 from public.referrals where referred_id = p_referred) then
    raise exception 'Ya tienes un código de invitación registrado.' using errcode = '22023';
  end if;
  select referral_attach_days into v_days from public.platform_settings where id;
  if v_profile.created_at < now() - make_interval(days => v_days) then
    raise exception 'El código de invitación se ingresa en tus primeros % días.', v_days using errcode = '22023';
  end if;
  if exists (select 1 from public.pickups where generator_id = p_referred and generator_points_final is not null) then
    raise exception 'El código solo vale antes de tu primer pesaje.' using errcode = '22023';
  end if;
  select profile_id into v_referrer from public.referral_codes where code = v_code;
  if not found then
    raise exception 'Ese código no existe. Revísalo con quien te invitó.' using errcode = '22023';
  end if;
  if v_referrer = p_referred then
    raise exception 'No puedes usar tu propio código.' using errcode = '22023';
  end if;
  insert into public.referrals (referrer_id, referred_id, code)
  values (v_referrer, p_referred, v_code)
  returning * into v_row;
  return v_row;
end;
$$;

-- ---------- Pago al pesaje ----------
create or replace function public.pay_referral_on_weigh()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  r public.referrals;
  s public.platform_settings;
  v_kg numeric;
  v_reason text;
  v_ref public.profiles;
  v_inv public.profiles;
  v_rider_phone text;
  v_month timestamptz := date_trunc('month', now() at time zone 'America/Lima') at time zone 'America/Lima';
  v_prize record;
  v_inv_name text;
begin
  select * into r from public.referrals
   where referred_id = new.generator_id and status = 'pendiente' for update;
  if not found then return null; end if;

  -- What the acopio weighed for the invitee so far: settle_delivery rewrote confirmed_quantity from the scale.
  select coalesce(sum(pi.confirmed_quantity * m.avg_weight_kg), 0) into v_kg
    from public.pickups p
    join public.pickup_items pi on pi.pickup_id = p.id
    join public.materials m on m.id = pi.material_id
   where p.generator_id = new.generator_id and p.generator_points_final is not null;

  -- Serialises every referral payout, so neither cap can be overrun by two settlements at once.
  select * into s from public.platform_settings where id for update;
  if v_kg < s.referral_min_kg then return null; end if;

  select * into v_ref from public.profiles where id = r.referrer_id;
  select * into v_inv from public.profiles where id = r.referred_id;
  select phone into v_rider_phone from public.profiles where id = new.rider_id;

  v_reason := case
    when public.phone_key(v_ref.phone) = public.phone_key(v_inv.phone) then 'mismo_telefono'
    when new.rider_id = r.referrer_id
      or public.phone_key(v_rider_phone) in (public.phone_key(v_ref.phone), public.phone_key(v_inv.phone)) then 'rider_vinculado'
    -- ponytail: 20 m flags two flats in one building too; an operator can pay those by hand.
    when (new.geog is not null and (
            exists (select 1 from public.addresses a
                     where a.profile_id = r.referrer_id and a.geog is not null and extensions.st_dwithin(a.geog, new.geog, 20))
         or exists (select 1 from public.pickups p
                     where p.generator_id = r.referrer_id and p.geog is not null and extensions.st_dwithin(p.geog, new.geog, 20))))
      or exists (select 1 from public.addresses a1 join public.addresses a2
                   on lower(regexp_replace(a1.address_line, '\W', '', 'g')) = lower(regexp_replace(a2.address_line, '\W', '', 'g'))
                 where a1.profile_id = r.referrer_id and a2.profile_id = r.referred_id) then 'misma_direccion'
  end;

  if v_reason is not null then
    update public.referrals
       set status = 'rechazado', reason = v_reason, decided_at = now(), qualifying_pickup_id = new.id
     where id = r.id;
    return null;
  end if;

  if (select count(*) from public.referrals
       where referrer_id = r.referrer_id and status = 'pagado' and decided_at >= v_month) >= s.referral_monthly_cap
  or (select count(*) from public.referrals
       where status = 'pagado' and decided_at >= v_month) >= s.referral_global_monthly_cap then
    -- Stays pending: a weighing next month pays it.
    update public.referrals set reason = 'tope_mensual' where id = r.id;
    return null;
  end if;

  select * into v_prize from public.referral_prize(r.referrer_id);
  v_inv_name := coalesce(nullif(v_inv.display_name, ''), nullif(v_inv.full_name, ''), 'tu invitado');

  if v_prize.referrer_points > 0 then
    insert into public.point_transactions (user_id, type, amount, reference_type, reference_id, description)
    values (r.referrer_id, 'referral', v_prize.referrer_points, 'referral', r.id,
            'Invitaste a ' || v_inv_name || ': su primer reciclaje se pesó');
    insert into public.notifications (user_id, kind, title, body, reference_type, reference_id)
    values (r.referrer_id, 'points_earned', '¡Ganaste ' || v_prize.referrer_points || ' pts por invitar!',
            v_inv_name || ' ya tuvo su primer reciclaje pesado. Los puntos están en tu saldo.', 'referral', r.id);
  end if;
  if v_prize.referred_points > 0 then
    insert into public.point_transactions (user_id, type, amount, reference_type, reference_id, description)
    values (r.referred_id, 'referral', v_prize.referred_points, 'referral', r.id,
            'Bienvenida a Ruma: tu primer reciclaje se pesó');
    insert into public.notifications (user_id, kind, title, body, reference_type, reference_id)
    values (r.referred_id, 'points_earned', '¡+' || v_prize.referred_points || ' pts de bienvenida!',
            'Tu primer reciclaje ya se pesó en el acopio. Los puntos de bienvenida están en tu saldo.', 'referral', r.id);
  end if;

  update public.referrals
     set status = 'pagado', reason = null, decided_at = now(), qualifying_pickup_id = new.id,
         referrer_points = v_prize.referrer_points, referred_points = v_prize.referred_points
   where id = r.id;
  return null;
end;
$$;

drop trigger if exists trg_pickup_referral on public.pickups;
create trigger trg_pickup_referral
  after update of generator_points_final on public.pickups
  for each row
  when (old.generator_points_final is null and new.generator_points_final is not null)
  execute function public.pay_referral_on_weigh();

-- ---------- RPC para la app ----------
create or replace function public.apply_referral_code(p_code text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_row public.referrals;
begin
  if v_uid is null then raise exception 'not authenticated'; end if;
  v_row := public.attach_referral(v_uid, p_code);
  return jsonb_build_object(
    'status', v_row.status,
    'referrer_name', (select coalesce(nullif(display_name, ''), full_name) from public.profiles where id = v_row.referrer_id));
end;
$$;

create or replace function public.my_referral_summary()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_me public.profiles;
  s public.platform_settings;
  v_prize record;
  v_month timestamptz := date_trunc('month', now() at time zone 'America/Lima') at time zone 'America/Lima';
  v_by public.referrals;
begin
  if v_uid is null then raise exception 'not authenticated'; end if;
  select * into v_me from public.profiles where id = v_uid;
  if v_me.role is distinct from 'generador' then
    raise exception 'Invita y gana es para las familias recicladoras.' using errcode = '22023';
  end if;
  select * into s from public.platform_settings where id;
  select * into v_prize from public.referral_prize(v_uid);
  select * into v_by from public.referrals where referred_id = v_uid;
  return jsonb_build_object(
    'code', public.referral_code_for(v_uid),
    'referrer_points', v_prize.referrer_points,
    'referred_points', v_prize.referred_points,
    'min_kg', s.referral_min_kg,
    'monthly_cap', s.referral_monthly_cap,
    'attach_days', s.referral_attach_days,
    'paid_this_month', (select count(*) from public.referrals
                         where referrer_id = v_uid and status = 'pagado' and decided_at >= v_month),
    'earned_points', (select coalesce(sum(referrer_points), 0) from public.referrals
                       where referrer_id = v_uid and status = 'pagado'),
    'can_apply', v_by.id is null
                 and v_me.created_at >= now() - make_interval(days => s.referral_attach_days)
                 and not exists (select 1 from public.pickups where generator_id = v_uid and generator_points_final is not null),
    'invited_by', case when v_by.id is null then null else jsonb_build_object(
                    'name', (select coalesce(nullif(display_name, ''), full_name) from public.profiles where id = v_by.referrer_id),
                    'status', v_by.status, 'reason', v_by.reason, 'points', v_by.referred_points) end,
    'invites', coalesce((
      select jsonb_agg(jsonb_build_object(
               'name', coalesce(nullif(p.display_name, ''), nullif(p.full_name, ''), 'Tu invitado'),
               'status', r.status, 'reason', r.reason, 'points', r.referrer_points,
               'created_at', r.created_at, 'decided_at', r.decided_at) order by r.created_at desc)
        from public.referrals r join public.profiles p on p.id = r.referred_id
       where r.referrer_id = v_uid), '[]'::jsonb));
end;
$$;

-- ---------- El código llega con el registro (metadato referral_code) ----------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_role public.user_role;
begin
  begin
    v_role := coalesce((new.raw_user_meta_data->>'role')::public.user_role, 'generador');
  exception when others then
    v_role := 'generador';
  end;

  insert into public.profiles (id, role, status, full_name, display_name, phone, avatar_url)
  values (
    new.id,
    v_role,
    'active',
    new.raw_user_meta_data->>'full_name',
    coalesce(new.raw_user_meta_data->>'display_name', new.raw_user_meta_data->>'full_name'),
    new.raw_user_meta_data->>'phone',
    new.raw_user_meta_data->>'avatar_url'
  );

  -- create the matching role extension row
  if v_role = 'generador' then
    insert into public.generator_profiles (profile_id) values (new.id);
    -- A wrong invitation code must never cost the person their account: it can be entered again later.
    if nullif(trim(new.raw_user_meta_data->>'referral_code'), '') is not null then
      begin
        perform public.attach_referral(new.id, new.raw_user_meta_data->>'referral_code');
      exception when others then null;
      end;
    end if;
  elsif v_role = 'eco_rider' then
    insert into public.rider_profiles (profile_id) values (new.id);
  end if;
  -- operador rows are provisioned by an admin (needs center_id), not here

  return new;
end;
$function$;

-- ---------- Permisos: Postgres concede EXECUTE a PUBLIC por defecto ----------
revoke all on function public.referral_code_for(uuid) from public, anon, authenticated;
revoke all on function public.referral_prize(uuid) from public, anon, authenticated;
revoke all on function public.attach_referral(uuid, text) from public, anon, authenticated;
revoke all on function public.pay_referral_on_weigh() from public, anon, authenticated;
revoke all on function public.apply_referral_code(text) from public, anon;
revoke all on function public.my_referral_summary() from public, anon;
grant execute on function public.apply_referral_code(text) to authenticated;
grant execute on function public.my_referral_summary() to authenticated;

-- Códigos para quienes ya existen (los nuevos lo reciben al abrir «Invita y gana»).
select public.referral_code_for(id) from public.profiles where role = 'generador';

commit;
