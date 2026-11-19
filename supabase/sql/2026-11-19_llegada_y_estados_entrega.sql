-- 2026-11-19 · "He llegado" con GPS, y los estados de la entrega que ve el rider (pedido del dueño)
-- APLICADO en STAGING (mbbdrmicjobydprvbpsj) el 2026-11-19. Falta repetirlo en producción.
-- Verificar con supabase/tests/llegada_estados_staging.sql (se revierte solo; espera "7/7 PASA").
--
-- 1. "He llegado al punto" (status collecting) solo vale si la última posición del rider tiene menos de
--    arrival_fix_max_age_s y está a arrival_radius_m o menos de la casa. Antes cualquiera lo marcaba desde lejos
--    (hueco antifraude visto en la prueba de punta a punta del 2026-11-18). Las cuentas *@ruma.test quedan fuera,
--    como en require_pickup_contact, para que las pruebas automáticas no necesiten GPS.
-- 2. La entrega tiene una hora de llegada (deliveries.arrived_at). El rider la marca con "Llegué al acopio" y el
--    operador también puede (si el rider lo olvidó). Lo que ve el rider:
--       en_camino sin arrived_at → "En camino al acopio"      en_camino con arrived_at → "En el centro de acopio"
--       recibida / pesada        → "Contando"                  liquidada                → "Terminado · pago en proceso"
--    Sin estados nuevos en el enum: los que ya existían alcanzan, solo cambian sus nombres en pantalla.
-- 3. Cada cambio avisa al rider (notificación), para que no tenga que preguntar.

begin;

alter table public.platform_settings
  add column if not exists arrival_radius_m      integer not null default 150,
  add column if not exists arrival_fix_max_age_s integer not null default 180;

-- ---- 1. Llegada con GPS ----
create or replace function public.check_rider_arrival()
returns trigger language plpgsql security definer set search_path = public, extensions
as $$
declare
  v_fix   geography;
  v_at    timestamptz;
  v_dist  numeric;
  s       public.platform_settings;
begin
  -- auth.role(), not current_user: inside a SECURITY DEFINER function current_user is the owner, so the first
  -- version skipped every check (caught by supabase/tests/llegada_estados_staging.sql).
  if coalesce(auth.role(), '') <> 'authenticated' or new.rider_id is distinct from auth.uid() or new.geog is null then
    return new;
  end if;
  if exists (select 1 from auth.users u where u.id = new.rider_id and lower(u.email) like '%@ruma.test') then
    return new;
  end if;
  select * into s from platform_settings where id;
  select rp.current_geog, rp.last_location_at into v_fix, v_at from rider_profiles rp where rp.profile_id = new.rider_id;
  if v_fix is null or v_at is null or v_at < now() - make_interval(secs => s.arrival_fix_max_age_s) then
    raise exception 'Activa tu GPS: necesitamos ver que llegaste a la casa.';
  end if;
  v_dist := round(st_distance(v_fix, new.geog)::numeric);
  if v_dist > s.arrival_radius_m then
    raise exception 'Aún estás a % m de la casa. Acércate para marcar que llegaste.', v_dist;
  end if;
  return new;
end $$;
revoke all on function public.check_rider_arrival() from public, anon, authenticated;

drop trigger if exists trg_pickup_arrival on public.pickups;
create trigger trg_pickup_arrival before update of status on public.pickups
  for each row when (new.status = 'collecting' and old.status is distinct from new.status)
  execute function public.check_rider_arrival();

-- ---- 2. Llegada al acopio ----
alter table public.deliveries add column if not exists arrived_at timestamptz;

create or replace function public.mark_delivery_arrived(p_delivery_id uuid)
returns public.deliveries language plpgsql security definer set search_path = public
as $$
declare d public.deliveries;
begin
  select * into d from deliveries where id = p_delivery_id for update;
  if not found then raise exception 'Entrega no encontrada.'; end if;
  if not (d.rider_id = auth.uid() or (is_operator() and d.center_id = current_operator_center())) then
    raise exception 'No puedes marcar esta entrega.';
  end if;
  if d.status::text <> 'en_camino' then return d; end if;   -- ya en conteo o después: nada que marcar
  update deliveries set arrived_at = coalesce(arrived_at, now()) where id = p_delivery_id returning * into d;
  return d;
end $$;
revoke all on function public.mark_delivery_arrived(uuid) from public, anon;
grant execute on function public.mark_delivery_arrived(uuid) to authenticated;

-- ---- 3. Avisos al rider ----
create or replace function public.notify_delivery_change()
returns trigger language plpgsql security definer set search_path = public
as $$
declare v_center text;
begin
  if new.rider_id is null then return null; end if;
  select name into v_center from collection_centers where id = new.center_id;
  if old.arrived_at is null and new.arrived_at is not null and auth.uid() is distinct from new.rider_id then
    insert into notifications (user_id, kind, title, body, reference_type, reference_id)
    values (new.rider_id, 'pickup_update', 'Tu carga está en el centro de acopio',
            coalesce(v_center, 'El acopio') || ' registró la llegada de ' || coalesce(new.code, 'tu entrega') || '.', 'delivery', new.id);
  end if;
  if new.status is distinct from old.status then
    if new.status::text = 'recibida' then
      insert into notifications (user_id, kind, title, body, reference_type, reference_id)
      values (new.rider_id, 'pickup_update', 'Están contando tu carga',
              coalesce(v_center, 'El acopio') || ' empezó a pesar y verificar ' || coalesce(new.code, 'tu entrega') || '.', 'delivery', new.id);
    elsif new.status::text = 'liquidada' then
      insert into notifications (user_id, kind, title, body, reference_type, reference_id, push)
      values (new.rider_id, 'points_earned', 'Terminado: pago en proceso',
              coalesce(new.code, 'Tu entrega') || ' quedó verificada. Te corresponden S/' || to_char(coalesce(new.rider_amount, 0), 'FM990.00') || ', en proceso de pago.',
              'delivery', new.id, 'urgent');
    elsif new.status::text = 'rechazada' then
      insert into notifications (user_id, kind, title, body, reference_type, reference_id, push)
      values (new.rider_id, 'pickup_update', 'El acopio rechazó tu carga',
              coalesce(new.reject_reason, 'Revisa el detalle de ' || coalesce(new.code, 'tu entrega') || '.'), 'delivery', new.id, 'urgent');
    end if;
  end if;
  return null;
end $$;
revoke all on function public.notify_delivery_change() from public, anon, authenticated;

drop trigger if exists trg_delivery_notify on public.deliveries;
create trigger trg_delivery_notify after update on public.deliveries
  for each row execute function public.notify_delivery_change();

commit;
