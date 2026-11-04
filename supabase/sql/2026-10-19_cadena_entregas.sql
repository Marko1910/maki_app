-- MAKI · 2026-10-19
-- APLICADO en STAGING (mbbdrmicjobydprvbpsj) el 2026-10-19. Falta repetirlo en
-- producción (kqwyygxpcsmworvmjqdv). Verificado tras aplicar: columna y índice
-- parcial creados, INSERT directo cerrado en deliveries y delivery_pickups,
-- commit_detection solo ejecutable por service_role, create_delivery por
-- authenticated, las dos políticas de insert retiradas y la guarda presente en
-- la definición viva de settle_delivery. commit_detection probada contra la base:
-- el reintento de una sesión devuelve la detección original sin duplicar items ni
-- aceptar sus puntos nuevos, y rechaza la sesión de otro usuario.
-- Cierra el replay de escaneos y obliga a validar los recojos al abrir entregas.
-- Ejecutar como propietario del esquema; la transacción evita exponer las RPC
-- antes de retirar los permisos de ejecución predeterminados.

begin;

-- Las detecciones anteriores no tienen sesión; el índice parcial conserva
-- esas filas sin inventarles una y consume una sola vez cada sesión nueva.
alter table public.detections add column if not exists session_id text;
create unique index if not exists detections_session_id_unique
  on public.detections (session_id) where session_id is not null;

-- Los dos inserts separados dejaban detecciones huérfanas si fallaban los items;
-- un reintento no debe confundir ese estado parcial con un escaneo guardado.
create or replace function public.commit_detection(
  p_generator_id uuid,
  p_session_id text,
  p_model_version text,
  p_streak numeric,
  p_total_points integer,
  p_total_value numeric,
  p_items jsonb
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
    (generator_id, session_id, status, model_version, streak_multiplier, total_points, total_value)
  values
    (p_generator_id, p_session_id, 'pending', p_model_version, p_streak, p_total_points, p_total_value)
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

-- No basta omitir el grant: PostgreSQL concede EXECUTE a PUBLIC por defecto,
-- y el esquema puede tener grants predeterminados para los roles de clientes.
revoke all on function public.commit_detection(uuid, text, text, numeric, integer, numeric, jsonb)
  from public, anon, authenticated;
grant execute on function public.commit_detection(uuid, text, text, numeric, integer, numeric, jsonb)
  to service_role;

CREATE OR REPLACE FUNCTION public.settle_delivery(p_delivery_id uuid)
 RETURNS deliveries
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_uid           uuid := auth.uid();
  v_delivery      public.deliveries;
  v_settings      public.platform_settings;
  v_gross         numeric(12,2);
  v_kg            numeric(12,3);
  v_awarded       integer := 0;
  r               record;
  v_points        integer;
begin
  if v_uid is null then raise exception 'not authenticated'; end if;

  select * into v_delivery from public.deliveries where id = p_delivery_id for update;
  if not found then raise exception 'delivery not found'; end if;

  if not (public.is_operator() and v_delivery.center_id = public.current_operator_center()) then
    raise exception 'not permitted';
  end if;

  if v_delivery.status = 'liquidada' then return v_delivery; end if;
  if v_delivery.status <> 'pesada' then
    raise exception 'delivery must be weighed before settling (status=%)', v_delivery.status;
  end if;

  select * into v_settings from public.platform_settings where id;

  select coalesce(sum(subtotal),0), coalesce(sum(weight_kg),0)
    into v_gross, v_kg
    from public.delivery_items where delivery_id = p_delivery_id;

  if v_gross <= 0 then raise exception 'delivery has no weighed items'; end if;

  -- Sin recojos, el bucle no repartía nada y MAKI se quedaba con todo el bruto.
  if not exists (select 1 from public.delivery_pickups where delivery_id = p_delivery_id) then
    raise exception 'No se puede liquidar una entrega sin recojos vinculados.';
  end if;
  update public.pickup_items pi
     set confirmed_quantity = greatest(0, round(
           di.weight_kg
           * (pi.reported_quantity::numeric / nullif((
                select sum(pi2.reported_quantity)
                  from public.delivery_pickups dp2
                  join public.pickup_items pi2 on pi2.pickup_id = dp2.pickup_id
                 where dp2.delivery_id = p_delivery_id
                   and pi2.material_id = pi.material_id), 0))
           / nullif(m.avg_weight_kg, 0)
         ))::integer
    from public.delivery_pickups dp,
         public.delivery_items di,
         public.materials m
   where dp.delivery_id = p_delivery_id
     and pi.pickup_id   = dp.pickup_id
     and di.delivery_id = p_delivery_id
     and di.material_id = pi.material_id
     and m.id           = pi.material_id;

  for r in
    select p.id, p.code, p.generator_id, p.generator_points_awarded,
           coalesce(sum(
             di.weight_kg
             * (pi.reported_quantity::numeric / nullif(tot.total_qty, 0))
             * m.points_per_kg
           ), 0) as earned_points
      from public.delivery_pickups dp
      join public.pickups p        on p.id = dp.pickup_id
      left join public.pickup_items pi on pi.pickup_id = p.id
      left join public.delivery_items di
             on di.delivery_id = dp.delivery_id and di.material_id = pi.material_id
      left join public.materials m on m.id = pi.material_id
      left join lateral (
             select sum(pi2.reported_quantity) as total_qty
               from public.delivery_pickups dp2
               join public.pickup_items pi2 on pi2.pickup_id = dp2.pickup_id
              where dp2.delivery_id = dp.delivery_id
                and pi2.material_id = pi.material_id
           ) tot on true
     where dp.delivery_id = p_delivery_id
     group by p.id, p.code, p.generator_id, p.generator_points_awarded, p.created_at
     order by p.created_at
  loop
    v_points := floor(r.earned_points)::integer;

    update public.generator_profiles
       set points_pending = greatest(0, points_pending - coalesce(r.generator_points_awarded,0))
     where profile_id = r.generator_id;

    if v_points > 0 then
      insert into public.point_transactions
        (user_id, type, amount, reference_type, reference_id, description)
      values (r.generator_id, 'earn_pickup', v_points, 'pickup', r.id,
              'Recojo ' || coalesce(r.code,'') || ' verificado en acopio');
    end if;

    update public.pickups
       set generator_points_final = v_points,
           points_status = case
             when v_points = coalesce(generator_points_awarded,0) then 'confirmado'
             when v_points = 0 then 'anulado'
             else 'ajustado' end
     where id = r.id;

    perform public.advance_challenges(r.generator_id, r.id);

    v_awarded := v_awarded + v_points;
  end loop;

  -- Entrada de stock por el libro; el trigger actualiza el caché.
  insert into public.center_stock_movements
    (center_id, material_id, kind, weight_kg, delivery_id, note, created_by)
  select v_delivery.center_id, di.material_id, 'entrada', sum(di.weight_kg),
         p_delivery_id, 'Entrega ' || coalesce(v_delivery.code, ''), v_uid
    from public.delivery_items di
   where di.delivery_id = p_delivery_id
   group by di.material_id;

  update public.deliveries
     set status           = 'liquidada',
         total_kg         = v_kg,
         gross_amount     = v_gross,
         platform_fee     = v_gross - round(v_awarded::numeric / v_settings.points_per_sol, 2),
         generator_points = v_awarded,
         rider_amount     = coalesce((select sum(p.rider_earnings)
                                        from public.delivery_pickups dp
                                        join public.pickups p on p.id = dp.pickup_id
                                       where dp.delivery_id = p_delivery_id), 0),
         settled_at       = now()
   where id = p_delivery_id
   returning * into v_delivery;

  return v_delivery;
end;
$function$;

-- La PK de delivery_pickups(pickup_id) ya impide vincular dos veces una carga.
-- El hueco era otro: delivery_pickups_rider_insert solo validaba la entrega,
-- permitiendo enganchar recojos ajenos o aún no completados.
-- ADVERTENCIA: el operador también es authenticated y deliveries_operator es ALL.
-- Revocar INSERT le retira deliberadamente la creación manual de entregas.
-- El panel actual solo lee y actualiza; una futura entrega de mostrador debe
-- pasar por otra RPC, nunca por devolver este grant.
revoke insert on public.deliveries from authenticated;
revoke insert on public.delivery_pickups from authenticated;
drop policy if exists deliveries_rider_insert on public.deliveries;
drop policy if exists delivery_pickups_rider_insert on public.delivery_pickups;

create or replace function public.create_delivery(p_center_id uuid, p_pickup_ids uuid[])
returns public.deliveries
language plpgsql
security definer
set search_path = public
as $function$
declare
  v_uid uuid := auth.uid();
  v_delivery public.deliveries;
  v_pickup public.pickups;
  v_count integer := 0;
begin
  if v_uid is null then raise exception 'Se requiere una sesión autenticada.'; end if;
  if coalesce(cardinality(p_pickup_ids), 0) = 0 then
    raise exception 'La entrega debe incluir al menos un recojo.';
  end if;
  if exists (select 1 from unnest(p_pickup_ids) id where id is null)
     or cardinality(p_pickup_ids) <> (select count(distinct id) from unnest(p_pickup_ids) id) then
    raise exception 'La lista de recojos no puede contener nulos ni duplicados.';
  end if;
  if not exists (select 1 from public.collection_centers where id = p_center_id) then
    raise exception 'El centro de acopio no existe.';
  end if;

  -- El orden común evita interbloqueos entre listas que comparten recojos;
  -- los bloqueos duran hasta guardar también todos los vínculos.
  for v_pickup in
    select * from public.pickups
     where id = any(p_pickup_ids) order by id for update
  loop
    v_count := v_count + 1;
    if v_pickup.rider_id is distinct from v_uid then
      raise exception 'Todos los recojos deben pertenecer al rider que llama.';
    end if;
    if v_pickup.status is distinct from 'completed' then
      raise exception 'Todos los recojos deben estar completados.';
    end if;
    if exists (select 1 from public.delivery_pickups where pickup_id = v_pickup.id) then
      raise exception 'Uno de los recojos ya pertenece a otra entrega.';
    end if;
  end loop;

  if v_count <> cardinality(p_pickup_ids) then
    raise exception 'Uno o más recojos no existen.';
  end if;

  insert into public.deliveries (center_id, rider_id, status, departed_at)
  values (p_center_id, v_uid, 'en_camino', now())
  returning * into v_delivery;

  insert into public.delivery_pickups (delivery_id, pickup_id)
  select v_delivery.id, id from unnest(p_pickup_ids) id;

  return v_delivery;
end;
$function$;

revoke all on function public.create_delivery(uuid, uuid[]) from public, anon;
grant execute on function public.create_delivery(uuid, uuid[]) to authenticated;

commit;

-- Verificación manual posterior (no ejecutada):
-- Las columnas permiten comprobar permisos efectivos, incluidos grants por columna.
-- Esperado: session_id = text; índice parcial único; insert directo = false;
-- commit para clientes = false y para servicio = true; create autenticado = true;
-- políticas insert retiradas = 0. Revisar también la guarda en la definición.
-- select
--   (select data_type from information_schema.columns
--     where table_schema = 'public' and table_name = 'detections'
--       and column_name = 'session_id') as session_id,
--   (select indexdef from pg_indexes where schemaname = 'public'
--     and indexname = 'detections_session_id_unique') as indice,
--   has_any_column_privilege('authenticated', 'public.deliveries', 'INSERT') as insert_entregas,
--   has_any_column_privilege('authenticated', 'public.delivery_pickups', 'INSERT') as insert_vinculos,
--   has_function_privilege('anon', 'public.commit_detection(uuid,text,text,numeric,integer,numeric,jsonb)', 'EXECUTE') as commit_anon,
--   has_function_privilege('authenticated', 'public.commit_detection(uuid,text,text,numeric,integer,numeric,jsonb)', 'EXECUTE') as commit_cliente,
--   has_function_privilege('service_role', 'public.commit_detection(uuid,text,text,numeric,integer,numeric,jsonb)', 'EXECUTE') as commit_servicio,
--   has_function_privilege('authenticated', 'public.create_delivery(uuid,uuid[])', 'EXECUTE') as create_cliente,
--   (select count(*) from pg_policies where schemaname = 'public'
--     and policyname in ('deliveries_rider_insert', 'delivery_pickups_rider_insert')) as politicas_insert,
--   pg_get_functiondef('public.settle_delivery(uuid)'::regprocedure) as liquidacion;