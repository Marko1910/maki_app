-- Ruma: concordancia operativa IA/rider. APLICADO en STAGING el 2026-11-06 (función viva idéntica a la base,
-- detect-material desplegado después, recojo controlado PU-08F8F1F8 capturado: IA 1, familia 1, rider 2).
-- Sin probar en staging: liquidación de una entrega pesada (requiere el panel). Falta repetirlo en producción.
-- Aplicar en STAGING antes de desplegar detect-material; clientes viejos compatibles.
-- Sin backfill: la procedencia y el conteo anterior a liquidación no son recuperables.
begin;

alter table public.detections add column if not exists provenance jsonb not null default '{}'::jsonb;

-- Reemplazo transaccional, sin CASCADE. El octavo argumento tiene default.
drop function public.commit_detection(uuid, text, text, numeric, integer, numeric, jsonb);
create or replace function public.commit_detection(
  p_generator_id uuid,
  p_session_id text,
  p_model_version text,
  p_streak numeric,
  p_total_points integer,
  p_total_value numeric,
  p_items jsonb,
  p_provenance jsonb default '{}'::jsonb
) returns jsonb
language plpgsql
security definer
set search_path = public
as $function$
declare
  v_detection public.detections;
  v_items jsonb;
begin
  if p_generator_id is null or p_session_id is null or p_session_id = '' then
    raise exception 'El escaneo requiere usuario y sesión.';
  end if;

  insert into public.detections
    (generator_id, session_id, status, model_version, streak_multiplier, total_points, total_value, provenance)
  values
    (p_generator_id, p_session_id, 'pending', p_model_version, p_streak, p_total_points, p_total_value, coalesce(p_provenance, '{}'::jsonb))
  on conflict (session_id) where session_id is not null do nothing
  returning * into v_detection;

  if not found then
    select * into v_detection from public.detections
     where session_id = p_session_id and generator_id = p_generator_id;
    if not found then
      raise exception 'La sesión de escaneo pertenece a otro usuario.';
    end if;
  else
    if jsonb_typeof(p_items) is distinct from 'array' then
      raise exception 'Los materiales del escaneo deben ser un arreglo.';
    end if;
    if jsonb_array_length(p_items) = 0 then
      raise exception 'El escaneo debe contener materiales.';
    end if;

    insert into public.detection_items
      (detection_id, material_id, quantity, confidence, quality_score, points, brands)
    select v_detection.id, i.material_id, i.quantity, i.confidence,
           i.quality_score, i.points, i.brands
      from jsonb_populate_recordset(null::public.detection_items, p_items) i;
  end if;

  -- Releer también en el camino nuevo conserva los valores que dejaron los
  -- defaults y triggers; el catálogo activo del siguiente intento puede cambiar.
  select coalesce(jsonb_agg(
           to_jsonb(i) || jsonb_build_object('code', m.code, 'name', m.name)
           order by i.material_id), '[]'::jsonb)
    into v_items
    from public.detection_items i
    join public.materials m on m.id = i.material_id
   where i.detection_id = v_detection.id;

  return jsonb_build_object(
    'id', v_detection.id, 'status', v_detection.status,
    'total_points', v_detection.total_points, 'total_value', v_detection.total_value,
    'items', v_items);
end;
$function$;

revoke all on function public.commit_detection(uuid,text,text,numeric,integer,numeric,jsonb,jsonb)
  from public, anon, authenticated;
grant execute on function public.commit_detection(uuid,text,text,numeric,integer,numeric,jsonb,jsonb) to service_role;

-- El esquema privado no se publica por PostgREST. No hay escrituras de clientes.
create schema if not exists ruma_analytics;
revoke all on schema ruma_analytics from public, anon, authenticated;
grant usage on schema ruma_analytics to service_role;

-- One capture per pickup, including an empty pickup; reopening must not add new rows.
create table ruma_analytics.pickup_count_captures (
  pickup_id uuid primary key references public.pickups(id) on delete cascade,
  captured_at timestamptz not null default now()
);
alter table ruma_analytics.pickup_count_captures enable row level security;
revoke all on ruma_analytics.pickup_count_captures from public, anon, authenticated, service_role;
grant select on ruma_analytics.pickup_count_captures to service_role;

create table ruma_analytics.pickup_count_samples (
  pickup_id uuid not null references public.pickups(id) on delete cascade,
  material_id uuid not null references public.materials(id),
  detection_id uuid references public.detections(id) on delete set null,
  captured_at timestamptz not null default now(),
  ai_quantity numeric,
  reported_quantity numeric,
  rider_quantity numeric,
  provenance jsonb not null,
  test_account boolean not null,
  primary key (pickup_id, material_id)
);
alter table ruma_analytics.pickup_count_samples enable row level security;
revoke all on ruma_analytics.pickup_count_samples from public, anon, authenticated, service_role;
grant select on ruma_analytics.pickup_count_samples to service_role;

create function ruma_analytics.capture_pickup_counts() returns trigger
language plpgsql security definer set search_path = '' as $capture$
declare
  v_provenance jsonb := '{}'::jsonb;
  v_test boolean;
begin
  -- Do not reconstruct completed pickups, or replace a first snapshot on retries.
  if old.status::text = 'completed' or new.status::text <> 'completed' then return new; end if;
  insert into ruma_analytics.pickup_count_captures(pickup_id) values (new.id)
    on conflict (pickup_id) do nothing;
  if not found then return new; end if;
  select d.provenance into v_provenance from public.detections d where d.id = new.detection_id;
  select exists(select 1 from auth.users u
    where u.id = new.generator_id and lower(u.email) like '%@maki.test') into v_test;

  insert into ruma_analytics.pickup_count_samples
    (pickup_id, material_id, detection_id, ai_quantity, reported_quantity, rider_quantity, provenance, test_account)
  select new.id, coalesce(ai.material_id, rider.material_id), new.detection_id,
         ai.quantity, rider.reported_quantity, rider.confirmed_quantity,
         coalesce(v_provenance, '{}'::jsonb), v_test
  from (
    select di.material_id, sum(di.quantity) as quantity
    from public.detection_items di where di.detection_id = new.detection_id group by di.material_id
  ) ai
  full join (
    select pi.material_id, pi.reported_quantity, pi.confirmed_quantity
    from public.pickup_items pi where pi.pickup_id = new.id
  ) rider on rider.material_id = ai.material_id
  on conflict (pickup_id, material_id) do nothing;
  return new;
end;
$capture$;
revoke all on function ruma_analytics.capture_pickup_counts() from public, anon, authenticated, service_role;

create trigger trg_capture_vision_counts before update of status on public.pickups
for each row when (old.status is distinct from new.status)
execute function ruma_analytics.capture_pickup_counts();

-- NULL means unobserved, never an invented zero. Keep missing rows in coverage.
create view ruma_analytics.count_samples with (security_invoker = true) as
select s.*,
  case when provenance->>'version' = '2'
         and provenance->>'attribution' = 'single'
         and jsonb_array_length(provenance->'sources') = 1
         and provenance->>'legacy_observations' = '0'
       then 'single'
       when provenance->>'version' = '2' and provenance->>'attribution' = 'mixed'
         and provenance->>'legacy_observations' = '0' then 'mixed'
       else 'unknown' end as attribution,
  provenance->'sources' as sources,
  (ai_quantity is not null and rider_quantity is not null) as comparable,
  ai_quantity - rider_quantity as signed_error,
  abs(rider_quantity - ai_quantity) as absolute_error
from ruma_analytics.pickup_count_samples s;

create view ruma_analytics.model_count_metrics with (security_invoker = true) as
select attribution, sources, material_id, test_account,
  count(*) as samples,
  count(distinct pickup_id) as pickups,
  count(*) filter (where comparable) as compared_samples,
  count(*) filter (where not comparable) as missing_samples,
  count(*) filter (where reported_quantity is distinct from ai_quantity
                   and reported_quantity is not null and ai_quantity is not null) as edited_declarations,
  avg((rider_quantity = ai_quantity)::int) filter (where comparable) as exact_match_rate,
  avg(absolute_error) filter (where comparable) as mean_absolute_error,
  avg(signed_error) filter (where comparable) as mean_signed_error,
  count(*) filter (where comparable and ai_quantity > rider_quantity) as overcounts,
  count(*) filter (where comparable and ai_quantity < rider_quantity) as undercounts,
  min(captured_at) as first_sample_at, max(captured_at) as last_sample_at
from ruma_analytics.count_samples
group by attribution, sources, material_id, test_account;

revoke all on ruma_analytics.count_samples, ruma_analytics.model_count_metrics from public, anon, authenticated;
grant select on ruma_analytics.count_samples, ruma_analytics.model_count_metrics to service_role;
commit;

-- SQL Editor / backend de confianza:
-- select * from ruma_analytics.model_count_metrics order by compared_samples desc;
-- Para comparar modelos: attribution='single' AND NOT test_account.
-- Conservar sources (incluye pipeline) y periodo: no mezclar versiones ni inferir causalidad.
