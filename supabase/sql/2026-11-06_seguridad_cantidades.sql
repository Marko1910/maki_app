-- Ruma: seguridad de cantidades y escaneos (2026-11-06).
-- APLICADO en STAGING el 2026-11-06 y verificado con supabase/tests/seguridad_cantidades_staging.py (15/15)
-- y supabase/tests/e2e-staging-chain.mjs (cadena completa). Falta repetirlo en producción.
--
-- Cierra cuatro huecos encontrados al revisar permisos en STAGING:
--  1. La app podía BORRAR sus propios escaneos (detections, detection_items).
--  2. Al crear ítems de un recojo, la app podía escribir points, earnings, unit_buy_price y weight_kg,
--     y borrar ítems. Ahora solo inserta (pickup_id, material_id, reported_quantity) y no borra.
--  3. Quien pidió el recojo podía cambiar confirmed_quantity, que es el conteo del rider en la puerta.
--     Un disparador decide quién cambia qué: el recolector lo declarado (antes de la recolección),
--     el rider asignado lo confirmado (llegando o recogiendo). Las funciones de confianza (settle_delivery,
--     create_pickup_from_detection) corren como su dueño y no pasan por esta guardia.
--  4. settle_delivery repartía el peso del lote según lo DECLARADO, así que inflar la declaración le quitaba
--     peso a los vecinos del mismo lote. Ahora reparte según lo que contó el rider en la puerta.
--     La función es la definición viva de STAGING con solo ese cambio.
-- Las apps (Android, PWA) no cambian: solo usan las columnas y operaciones que siguen permitidas.

begin;

-- 1. Escaneos: lectura sí, borrado no (solo commit_detection, como service_role, escribe).
revoke delete on public.detections, public.detection_items from authenticated, anon;

-- 2. Ítems del recojo: columnas de dinero cerradas al cliente.
revoke insert, update, delete on public.pickup_items from authenticated, anon;
grant insert (pickup_id, material_id, reported_quantity) on public.pickup_items to authenticated;
grant update (reported_quantity, confirmed_quantity) on public.pickup_items to authenticated;

-- 3. Quién cambia cada cantidad. SECURITY INVOKER a propósito: current_user es el rol del cliente
--    en llamadas directas y el dueño de la función cuando la escritura viene de una RPC de confianza.
create or replace function public.guard_pickup_item_write()
returns trigger
language plpgsql
set search_path = public
as $guard$
declare
  v_pickup public.pickups;
begin
  if current_user not in ('authenticated', 'anon') then
    return new;
  end if;
  select * into v_pickup from public.pickups where id = new.pickup_id;
  if not found then
    raise exception 'Recojo no encontrado.' using errcode = '42501';
  end if;

  if tg_op = 'INSERT' then
    if v_pickup.generator_id is distinct from auth.uid() or v_pickup.status::text not in ('requested', 'assigned') then
      raise exception 'Solo quien pide el recojo agrega sus residuos, al pedirlo.' using errcode = '42501';
    end if;
    new.confirmed_quantity := null;
    return new;
  end if;

  if new.confirmed_quantity is distinct from old.confirmed_quantity
     and (v_pickup.rider_id is distinct from auth.uid() or v_pickup.status::text not in ('arriving', 'collecting')) then
    raise exception 'Solo el Eco-Rider del recojo confirma la cantidad, en la puerta.' using errcode = '42501';
  end if;
  if new.reported_quantity is distinct from old.reported_quantity
     and (v_pickup.generator_id is distinct from auth.uid() or v_pickup.status::text not in ('requested', 'assigned', 'en_route', 'arriving')) then
    raise exception 'Solo quien pidió el recojo corrige lo declarado, antes de la recolección.' using errcode = '42501';
  end if;
  return new;
end;
$guard$;

revoke all on function public.guard_pickup_item_write() from public, anon, authenticated;

drop trigger if exists trg_pickup_item_guard on public.pickup_items;
create trigger trg_pickup_item_guard
before insert or update on public.pickup_items
for each row execute function public.guard_pickup_item_write();

-- 4. Liquidación: reparto por el conteo del rider.
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
  -- Base del reparto: lo que el rider contó en la puerta (lo declarado solo si no contó).
  -- Se fija antes de que la siguiente sentencia reescriba confirmed_quantity desde el peso.
  -- drop + create y no DELETE: pg_safeupdate rechaza un DELETE sin WHERE dentro de una petición de la API.
  drop table if exists pg_temp.ruma_settle_basis;
  create temp table ruma_settle_basis (pickup_item_id uuid primary key, q numeric not null) on commit drop;
  insert into pg_temp.ruma_settle_basis (pickup_item_id, q)
  select pi.id, greatest(0, coalesce(pi.confirmed_quantity, pi.reported_quantity))
    from public.pickup_items pi
    join public.delivery_pickups dp on dp.pickup_id = pi.pickup_id
   where dp.delivery_id = p_delivery_id;

  update public.pickup_items pi
     set confirmed_quantity = greatest(0, round(
           di.weight_kg
           * ((select b.q from pg_temp.ruma_settle_basis b where b.pickup_item_id = pi.id)::numeric / nullif((
                select sum((select b.q from pg_temp.ruma_settle_basis b where b.pickup_item_id = pi2.id))
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
             * ((select b.q from pg_temp.ruma_settle_basis b where b.pickup_item_id = pi.id)::numeric / nullif(tot.total_qty, 0))
             * m.points_per_kg
           ), 0) as earned_points
      from public.delivery_pickups dp
      join public.pickups p        on p.id = dp.pickup_id
      left join public.pickup_items pi on pi.pickup_id = p.id
      left join public.delivery_items di
             on di.delivery_id = dp.delivery_id and di.material_id = pi.material_id
      left join public.materials m on m.id = pi.material_id
      left join lateral (
             select sum((select b.q from pg_temp.ruma_settle_basis b where b.pickup_item_id = pi2.id)) as total_qty
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

commit;
