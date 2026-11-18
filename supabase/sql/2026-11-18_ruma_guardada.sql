-- 2026-11-18 · "Tu ruma guardada": juntar botellas en varios escaneos y pedir un solo recojo
-- APLICADO en STAGING (mbbdrmicjobydprvbpsj) el 2026-11-18. Falta repetirlo en producción.
-- Verificar con supabase/tests/ruma_guardada_staging.sql (corre todo y lo revierte; espera "9/9 PASA").
--
-- Antes: create_pickup_from_detection enlazaba solo el ÚLTIMO escaneo pendiente. Quien escaneaba 3 botellas hoy y 4
-- mañana pedía un recojo de 4; las 3 primeras quedaban 'pending' para siempre y nunca se comparaban con el rider.
-- Ahora:
--   * Todo escaneo 'pending' de los últimos 30 días es "la ruma guardada" de la familia (saved_pile() la resume).
--   * Pedir recojo junta TODOS esos escaneos: pickup_items = suma por material, y cada detección queda enlazada
--     (detections.pickup_id) y 'scheduled'. pickups.detection_id sigue apuntando al más reciente (compatibilidad).
--   * complete_pickup marca 'collected' todos los escaneos enlazados.
--   * El muestreo IA vs familia vs rider (ruma_analytics) suma la IA de todos los escaneos del recojo; si vienen de
--     proveedores/modelos distintos la procedencia queda marcada como mixta, no se mezcla en silencio.
--   * Si el recojo se cancela o falla, sus escaneos vuelven a la ruma ('pending', sin pickup_id).
-- El cliente no escribe en detections (sin INSERT/UPDATE para authenticated): todo pasa por estas funciones.

alter table public.detections add column if not exists pickup_id uuid references public.pickups(id) on delete set null;
create index if not exists detections_pickup_id_idx on public.detections(pickup_id);
create index if not exists detections_pending_idx on public.detections(generator_id, detected_at) where status = 'pending';

-- Backfill: los recojos ya creados enlazaban un solo escaneo.
update public.detections d set pickup_id = p.id
  from public.pickups p
 where p.detection_id = d.id and d.pickup_id is null;
-- Los escaneos de recojos ya cancelados quedaron 'scheduled' para siempre: vuelven a la ruma.
update public.detections d set status = 'pending', pickup_id = null
  from public.pickups p
 where p.id = d.pickup_id and d.status = 'scheduled' and p.status in ('cancelled', 'failed');

-- Los escaneos que forman la ruma guardada de una familia. Única definición de la ventana.
create or replace function public.saved_detection_ids(p_uid uuid)
returns setof uuid
language sql stable security definer set search_path = public
as $$
  -- ponytail: fixed 30-day window; move it to platform_settings if the pilot asks for longer storage.
  select d.id from detections d
   where d.generator_id = p_uid and d.status = 'pending' and d.pickup_id is null
     and d.detected_at > now() - interval '30 days'
$$;
revoke all on function public.saved_detection_ids(uuid) from public, anon, authenticated;

-- Resumen para la app: cuántas unidades de cada material tiene guardadas y en cuántos escaneos.
create or replace function public.saved_pile()
returns table (material_id uuid, code text, name text, quantity int, points int, scans int, since timestamptz)
language sql stable security definer set search_path = public
as $$
  select m.id, m.code::text, m.name::text, sum(di.quantity)::int, sum(coalesce(di.points, 0))::int,
         count(distinct d.id)::int, min(d.detected_at)
    from detections d
    join detection_items di on di.detection_id = d.id
    join materials m on m.id = di.material_id
   where d.id in (select public.saved_detection_ids(auth.uid())) and di.quantity > 0
   group by m.id, m.code, m.name
   order by sum(di.quantity) desc
$$;
revoke all on function public.saved_pile() from public, anon;
grant execute on function public.saved_pile() to authenticated;

create or replace function public.create_pickup_from_detection(
  p_address_id uuid, p_scheduled_date date, p_window_label text,
  p_latitude double precision default null, p_longitude double precision default null)
returns uuid
language plpgsql security definer set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_ids uuid[];
  v_latest uuid;
  v_points int;
  v_kg numeric;
  v_pickup_id uuid;
  v_ok_fix boolean := p_latitude between -90 and 90 and p_longitude between -180 and 180;
begin
  if v_uid is null then raise exception 'not authenticated'; end if;

  -- Lock the pile so two taps on "Pedir recojo" can't book the same bottles twice.
  select array_agg(d.id order by d.detected_at desc), sum(coalesce(d.total_points, 0)), sum(coalesce(d.total_weight_kg, 0))
    into v_ids, v_points, v_kg
    from (select * from detections
           where id in (select public.saved_detection_ids(v_uid))
           for update) d;
  v_latest := v_ids[1];

  insert into pickups (
    generator_id, address_id, detection_id, scheduled_date, window_label,
    generator_points_awarded, total_weight_kg, status, latitude, longitude
  ) values (
    v_uid, p_address_id, v_latest, p_scheduled_date, p_window_label,
    coalesce(v_points, 0), coalesce(v_kg, 0), 'requested',
    case when v_ok_fix then p_latitude end, case when v_ok_fix then p_longitude end
  ) returning id into v_pickup_id;

  if v_latest is not null then
    insert into pickup_items (pickup_id, material_id, reported_quantity, points)
    select v_pickup_id, di.material_id, sum(di.quantity), sum(coalesce(di.points, 0))
      from detection_items di
     where di.detection_id = any(v_ids) and di.quantity > 0
     group by di.material_id;

    update detections set status = 'scheduled', pickup_id = v_pickup_id where id = any(v_ids);
  end if;

  return v_pickup_id;
end $$;

create or replace function public.complete_pickup(p_pickup_id uuid)
returns void
language plpgsql security definer set search_path = public
as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'not authenticated'; end if;

  update pickups
     set status = 'completed'
   where id = p_pickup_id
     and status in ('assigned','en_route','arriving','collecting')
     and (rider_id = v_uid or is_operator());
  if not found then raise exception 'pickup not found or not permitted'; end if;

  update detections d
     set status = 'collected'
   where d.pickup_id = p_pickup_id
      or d.id = (select p.detection_id from pickups p where p.id = p_pickup_id);
end $$;

-- A cancelled or failed pickup gives its bottles back to the family's pile.
create or replace function public.release_pickup_detections()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  update detections set status = 'pending', pickup_id = null
   where pickup_id = new.id and status = 'scheduled';
  return new;
end $$;
revoke all on function public.release_pickup_detections() from public, anon, authenticated;

drop trigger if exists trg_pickup_release_detections on public.pickups;
create trigger trg_pickup_release_detections
  after update of status on public.pickups
  for each row when (new.status in ('cancelled', 'failed') and old.status is distinct from new.status)
  execute function public.release_pickup_detections();

-- The rider may read every scan of a pickup they work, not only the latest one.
drop policy if exists detections_read_related on public.detections;
create policy detections_read_related on public.detections for select using (
  is_operator() or exists (
    select 1 from pickups pk
     where (pk.id = detections.pickup_id or pk.detection_id = detections.id)
       and pk.rider_id = (select auth.uid())));

create or replace function ruma_analytics.capture_pickup_counts()
returns trigger
language plpgsql security definer set search_path = ''
as $$
declare
  v_provenance jsonb := '{}'::jsonb;
  v_sources int;
  v_test boolean;
begin
  -- Do not reconstruct completed pickups, or replace a first snapshot on retries.
  if old.status::text = 'completed' or new.status::text <> 'completed' then return new; end if;
  insert into ruma_analytics.pickup_count_captures(pickup_id) values (new.id)
    on conflict (pickup_id) do nothing;
  if not found then return new; end if;

  -- Every scan of this pickup (several when the family saved bottles over days).
  select count(distinct coalesce(d.provenance, '{}'::jsonb)), (array_agg(d.provenance order by d.detected_at desc))[1]
    into v_sources, v_provenance
    from public.detections d
   where d.pickup_id = new.id or d.id = new.detection_id;
  -- Different providers/models in one pickup: flag it instead of crediting the latest one with all of it.
  if v_sources > 1 then
    v_provenance := jsonb_build_object('mixed', true, 'sources', v_sources);
  end if;

  select exists(select 1 from auth.users u
    where u.id = new.generator_id and lower(u.email) like '%@ruma.test') into v_test;

  insert into ruma_analytics.pickup_count_samples
    (pickup_id, material_id, detection_id, ai_quantity, reported_quantity, rider_quantity, provenance, test_account)
  select new.id, coalesce(ai.material_id, rider.material_id), new.detection_id,
         ai.quantity, rider.reported_quantity, rider.confirmed_quantity,
         coalesce(v_provenance, '{}'::jsonb), v_test
  from (
    select di.material_id, sum(di.quantity) as quantity
      from public.detection_items di
      join public.detections d on d.id = di.detection_id
     where d.pickup_id = new.id or d.id = new.detection_id
     group by di.material_id
  ) ai
  full join (
    select pi.material_id, pi.reported_quantity, pi.confirmed_quantity
    from public.pickup_items pi where pi.pickup_id = new.id
  ) rider on rider.material_id = ai.material_id
  on conflict (pickup_id, material_id) do nothing;
  return new;
end;
$$;
