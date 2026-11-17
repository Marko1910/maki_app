-- Ruma: puntos por unidad y estado, bono por juntar, y pago mixto del Eco-Rider (2026-11-17).
-- Decidido por el dueño el 2026-11-17: docs/puntos-por-botella.md y docs/pago-rider.md.
-- APLICADO en STAGING el 2026-11-17. Verificar con: python supabase/tests/puntos_estado_staging.py
-- Falta repetirlo en producción.
--
-- * pickup_items.condition (limpia | aplastada | sucia): la marca el rider en la puerta, igual que confirmed_quantity.
-- * settle_delivery: puntos = unidades contadas x points_per_unit x factor de estado, con tope por su parte del peso
--   x points_per_kg x weight_cap_factor; luego +10 % desde 30 unidades y +20 % desde 80 en el mismo recojo.
-- * Rider: S/ rider_per_kg por kg pesado, al liquidar (ya no al completar), + rider_route_base por una entrega
--   con rider_route_min_stops o más recojos. pickups.rider_earnings es una estimación hasta el pesaje.
-- * Recojos completados antes de este script ya cobraron la tarifa vieja: settle_delivery no les paga de nuevo.
-- Todos los montos viven en platform_settings: cambiarlos es un UPDATE.

begin;

alter table public.platform_settings
  add column if not exists condition_crushed_factor numeric not null default 1.10,
  add column if not exists condition_dirty_factor   numeric not null default 0.50,
  add column if not exists weight_cap_factor        numeric not null default 1.25,
  add column if not exists bulk_tier1_units         integer not null default 30,
  add column if not exists bulk_tier1_bonus         numeric not null default 0.10,
  add column if not exists bulk_tier2_units         integer not null default 80,
  add column if not exists bulk_tier2_bonus         numeric not null default 0.20,
  add column if not exists rider_per_kg             numeric not null default 0.35,
  add column if not exists rider_route_base         numeric not null default 6.00,
  add column if not exists rider_route_min_stops    integer not null default 12;

alter table public.pickup_items
  add column if not exists condition text not null default 'limpia';
do $$ begin
  alter table public.pickup_items add constraint pickup_items_condition_check
    check (condition in ('limpia', 'aplastada', 'sucia'));
exception when duplicate_object then null; end $$;
-- La app solo actualiza columnas concedidas una a una (2026-11-06_seguridad_cantidades.sql); el guardia decide quién.
grant update (condition) on public.pickup_items to authenticated;

-- Guardia: el estado lo marca solo el rider del recojo, en la puerta; al pedir nace 'limpia'.
create or replace function public.guard_pickup_item_write()
 returns trigger
 language plpgsql
 set search_path to 'public'
as $function$
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
    new.condition := 'limpia';
    return new;
  end if;

  if (new.confirmed_quantity is distinct from old.confirmed_quantity or new.condition is distinct from old.condition)
     and (v_pickup.rider_id is distinct from auth.uid() or v_pickup.status::text not in ('arriving', 'collecting')) then
    raise exception 'Solo el Eco-Rider del recojo confirma la cantidad y el estado, en la puerta.' using errcode = '42501';
  end if;
  if new.reported_quantity is distinct from old.reported_quantity
     and (v_pickup.generator_id is distinct from auth.uid() or v_pickup.status::text not in ('requested', 'assigned', 'en_route', 'arriving')) then
    raise exception 'Solo quien pidió el recojo corrige lo declarado, antes de la recolección.' using errcode = '42501';
  end if;
  return new;
end;
$function$;

-- Al asignar: la tarifa del rider es ahora una ESTIMACIÓN (kg estimados x rider_per_kg); la real sale del pesaje.
create or replace function public.set_rider_fee()
 returns trigger
 language plpgsql
 security definer
 set search_path to 'public', 'extensions'
as $function$
declare
  v_dist numeric;
  v_kg   numeric;
begin
  if new.rider_id is not null and old.rider_id is null then
    if new.distance_m is null and new.geog is not null then
      select round(st_distance(rp.current_geog, new.geog)::numeric) into v_dist
        from public.rider_profiles rp
       where rp.profile_id = new.rider_id and rp.current_geog is not null;
      if v_dist is not null then
        new.distance_m := v_dist;
        new.eta_minutes := coalesce(new.eta_minutes, greatest(3, ceil(v_dist / 250.0))::int);
      end if;
    end if;
    v_kg := nullif(new.total_weight_kg, 0);
    if v_kg is null then
      select sum(pi.reported_quantity * m.avg_weight_kg) into v_kg
        from public.pickup_items pi join public.materials m on m.id = pi.material_id
       where pi.pickup_id = new.id;
    end if;
    new.rider_earnings := round(coalesce(v_kg, 0) * (select rider_per_kg from public.platform_settings where id), 2);
    new.assigned_at := coalesce(new.assigned_at, now());
  end if;
  return new;
end $function$;

CREATE OR REPLACE FUNCTION public.settle_pickup()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  if new.status = 'completed' and old.status is distinct from 'completed' then
    -- Puntos del generador: PROVISIONALES. Nada al libro mayor todavía — el
    -- asiento definitivo lo escribe settle_delivery con el monto real.
    -- points_status ya nace 'provisional' por default de columna, así que aquí
    -- no se toca: el trigger es AFTER UPDATE y una asignación a NEW se perdería.
    if coalesce(new.generator_points_awarded,0) > 0 then
      update public.generator_profiles
         set points_pending = points_pending + new.generator_points_awarded
       where profile_id = new.generator_id;
    end if;

    -- El rider ya no cobra al completar: settle_delivery le paga por kg pesado (2026-11-17).

    -- Impacto ambiental del generador
    update public.generator_profiles
       set recycled_kg        = recycled_kg    + coalesce(new.total_weight_kg,0),
           co2_avoided_kg     = co2_avoided_kg + coalesce(new.total_co2_kg,0),
           last_activity_date = current_date
     where profile_id = new.generator_id;

    -- Estadísticas del rider
    if new.rider_id is not null then
      update public.rider_profiles
         set total_collections = total_collections + 1,
             total_recycled_kg = total_recycled_kg + coalesce(new.total_weight_kg,0),
             co2_avoided_kg    = co2_avoided_kg + coalesce(new.total_co2_kg,0)
       where profile_id = new.rider_id;
    end if;

    -- Los retos YA NO avanzan aquí. advance_challenges cuenta con
    -- coalesce(confirmed_quantity, reported_quantity), y como nada escribía
    -- confirmed_quantity, siempre contaba lo que la familia declaró: bastaba
    -- declarar 500 botellas para cobrar el bono del reto en puntos canjeables
    -- sin verificación alguna. Ahora avanzan en settle_delivery, ya con las
    -- cantidades que el acopio confirmó al pesar.
  end if;
  return new;
end;
$function$;

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
  v_rider_pay     numeric(12,2);
  v_route_base    numeric(12,2) := 0;
  v_stops         integer;
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
    -- Puntos (2026-11-17): por unidad contada en la puerta x estado, nunca más que su parte del peso
    -- x points_per_kg x weight_cap_factor; luego el bono por juntar según las unidades del recojo.
    select p.id, p.code, p.generator_id, p.generator_points_awarded, p.rider_id,
           coalesce(sum(least(
             b.q * m.points_per_unit * case pi.condition
               when 'aplastada' then v_settings.condition_crushed_factor
               when 'sucia'     then v_settings.condition_dirty_factor
               else 1 end,
             di.weight_kg * (b.q / nullif(tot.total_qty, 0)) * m.points_per_kg * v_settings.weight_cap_factor
           )), 0) as base_points,
           coalesce(sum(b.q), 0) as units,
           coalesce(sum(di.weight_kg * (b.q / nullif(tot.total_qty, 0))), 0) as weighed_kg
      from public.delivery_pickups dp
      join public.pickups p        on p.id = dp.pickup_id
      left join public.pickup_items pi on pi.pickup_id = p.id
      left join pg_temp.ruma_settle_basis b on b.pickup_item_id = pi.id
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
     group by p.id, p.code, p.generator_id, p.generator_points_awarded, p.rider_id, p.created_at
     order by p.created_at
  loop
    v_points := floor(r.base_points * (1 + case
                  when r.units >= v_settings.bulk_tier2_units then v_settings.bulk_tier2_bonus
                  when r.units >= v_settings.bulk_tier1_units then v_settings.bulk_tier1_bonus
                  else 0 end))::integer;

    -- Rider: por kg pesado. Un recojo que ya cobró con la tarifa vieja (al completar) no cobra dos veces.
    if r.rider_id is not null and not exists (select 1 from public.rider_earnings e where e.pickup_id = r.id) then
      v_rider_pay := round(r.weighed_kg * v_settings.rider_per_kg, 2);
      if v_rider_pay > 0 then
        insert into public.rider_earnings (rider_id, pickup_id, amount, type, description)
        values (r.rider_id, r.id, v_rider_pay, 'pickup',
                'Recojo ' || coalesce(r.code,'') || ': ' || round(r.weighed_kg, 2) || ' kg pesados');
      end if;
      update public.pickups set rider_earnings = v_rider_pay where id = r.id;
    end if;

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

  -- Base por ruta: una entrega con al menos rider_route_min_stops recojos es una ruta completa.
  select count(*) into v_stops from public.delivery_pickups where delivery_id = p_delivery_id;
  if v_delivery.rider_id is not null and v_stops >= v_settings.rider_route_min_stops then
    v_route_base := v_settings.rider_route_base;
    insert into public.rider_earnings (rider_id, pickup_id, amount, type, description)
    values (v_delivery.rider_id, null, v_route_base, 'route',
            'Ruta ' || coalesce(v_delivery.code,'') || ': ' || v_stops || ' recojos');
  end if;

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
                                       where dp.delivery_id = p_delivery_id), 0) + v_route_base,
         settled_at       = now()
   where id = p_delivery_id
   returning * into v_delivery;

  return v_delivery;
end;
$function$;

CREATE OR REPLACE FUNCTION public.notify_pickup_change()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
                  'Recojo de ' || v_gen || ' listo. Estimado S/' || to_char(coalesce(new.rider_earnings, 0), 'FM990.00') || ': se paga cuando el acopio lo pese.',
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
$function$;

commit;
