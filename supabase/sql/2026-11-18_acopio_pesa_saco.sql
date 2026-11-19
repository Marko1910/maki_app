-- 2026-11-18 (tarde) · El acopio PESA cada saco; el rider solo lo recoge (decidido por el dueño tras el debate,
-- docs/debate-quien-mide.md). Reemplaza el pesaje en la puerta de 2026-11-18_saco_pesado_conteo_acopio.sql.
-- APLICADO en STAGING (mbbdrmicjobydprvbpsj) el 2026-11-18. Falta repetirlo en producción.
-- Verificar con supabase/tests/acopio_pesa_saco_staging.sql (se revierte solo; espera "8/8 PASA").
--
--   * Puerta: el rider confirma "Recibí el saco RU-XXXX" y lo lleva. Sin balanza.
--   * Acopio: pesa cada saco por material (pickup_items.center_weight_kg) y marca su estado. Si el peso respalda lo
--     declarado (declarado x avg_weight_kg, ±sack_weight_tolerance), se paga lo declarado, por unidad. Si no, o si el
--     saco salió en la muestra al azar (pickups.sack_audit, 1 de cada sack_audit_every), el panel exige contarlo a mano
--     (center_quantity), y eso se paga. La muestra disuade el fraude y da la verdad para medir a la IA.
--   * Por qué no contar todo: un 25 % de exageración en 100 botellas son ~S/ 0,25; verificar cada saco cuesta más.
-- Todos los umbrales viven en platform_settings.

begin;

alter table public.platform_settings
  add column if not exists sack_weight_tolerance numeric not null default 0.35,
  add column if not exists sack_audit_every     integer not null default 10;

alter table public.pickup_items add column if not exists center_weight_kg numeric;
do $$ begin
  alter table public.pickup_items add constraint pickup_items_center_weight_check check (center_weight_kg >= 0 and center_weight_kg <= 500);
exception when duplicate_object then null; end $$;
grant update (center_weight_kg) on public.pickup_items to authenticated;

-- 1 de cada N sacos se cuenta a mano aunque su peso cuadre. Se sortea al crear el recojo, no al pesar:
-- el operador no puede volver a tirar el dado.
alter table public.pickups add column if not exists sack_audit boolean not null default false;
create or replace function public.draw_sack_audit()
returns trigger language plpgsql security definer set search_path = public
as $$
begin
  new.sack_audit := random() < 1.0 / greatest(1, (select sack_audit_every from platform_settings where id));
  return new;
end $$;
revoke all on function public.draw_sack_audit() from public, anon, authenticated;
drop trigger if exists trg_pickup_sack_audit on public.pickups;
create trigger trg_pickup_sack_audit before insert on public.pickups
  for each row execute function public.draw_sack_audit();

-- ¿El peso del saco respalda lo declarado? La usan settle_delivery y el panel (misma regla en ambos).
create or replace function public.sack_weight_ok(p_weight numeric, p_declared integer, p_avg numeric)
returns boolean language sql stable set search_path = public
as $$
  select p_weight is not null and p_declared > 0 and p_avg > 0
     and abs(p_weight / (p_declared * p_avg) - 1) <= (select sack_weight_tolerance from platform_settings where id)
$$;
grant execute on function public.sack_weight_ok(numeric, integer, numeric) to authenticated;

-- Unidades que se pagan por un saco y material.
create or replace function public.sack_units(p_counted integer, p_weight numeric, p_declared integer, p_door integer, p_avg numeric)
returns numeric language sql stable set search_path = public
as $$
  select greatest(0, case
    when p_counted is not null then p_counted                                    -- contado a mano en el acopio
    when public.sack_weight_ok(p_weight, p_declared, p_avg) then p_declared       -- el peso respalda lo declarado
    when p_weight is not null and p_avg > 0 then round(p_weight / p_avg)          -- no cuadra y nadie contó: por el peso
    else coalesce(p_door, p_declared)                                             -- saco sin pesar (flujo viejo)
  end)
$$;

-- El acopio escribe también el peso del saco (misma regla que el conteo).
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
    new.center_quantity := null;
    new.center_condition := null;
    new.center_weight_kg := null;
    return new;
  end if;

  -- Puerta: los APK viejos todavía cuentan o pesan; solo el rider del recojo, al llegar o recoger.
  if (new.weight_kg is distinct from old.weight_kg
      or new.confirmed_quantity is distinct from old.confirmed_quantity
      or new.condition is distinct from old.condition)
     and (v_pickup.rider_id is distinct from auth.uid() or v_pickup.status::text not in ('arriving', 'collecting')) then
    raise exception 'Solo el Eco-Rider del recojo registra la recolección, en la puerta.' using errcode = '42501';
  end if;
  -- Acopio: el operador del centro que recibió el saco lo pesa, cuenta y califica.
  if (new.center_quantity is distinct from old.center_quantity
      or new.center_condition is distinct from old.center_condition
      or new.center_weight_kg is distinct from old.center_weight_kg)
     and not exists (
       select 1 from public.delivery_pickups dp join public.deliveries d on d.id = dp.delivery_id
        where dp.pickup_id = new.pickup_id and d.status::text in ('recibida', 'pesada')
          and public.is_operator() and d.center_id = public.current_operator_center()) then
    raise exception 'Solo el centro de acopio que recibió el saco lo pesa y cuenta, antes de liquidar.' using errcode = '42501';
  end if;
  if new.reported_quantity is distinct from old.reported_quantity
     and (v_pickup.generator_id is distinct from auth.uid() or v_pickup.status::text not in ('requested', 'assigned', 'en_route', 'arriving')) then
    raise exception 'Solo quien pidió el recojo corrige lo declarado, antes de la recolección.' using errcode = '42501';
  end if;
  return new;
end;
$function$;

alter table ruma_analytics.pickup_count_samples add column if not exists center_weight_kg numeric;

-- ---- Liquidación ----
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
  -- Base del reparto (2026-11-18, tarde): sack_units decide por cada saco y material: lo contado a mano por el
  -- acopio; si no, lo declarado cuando el peso del saco lo respalda; si no, lo estimado por el peso.
  -- Se fija antes de que la siguiente sentencia reescriba confirmed_quantity desde el peso.
  -- drop + create y no DELETE: pg_safeupdate rechaza un DELETE sin WHERE dentro de una petición de la API.
  drop table if exists pg_temp.ruma_settle_basis;
  create temp table ruma_settle_basis (pickup_item_id uuid primary key, q numeric not null) on commit drop;
  insert into pg_temp.ruma_settle_basis (pickup_item_id, q)
  select pi.id, public.sack_units(pi.center_quantity, pi.center_weight_kg, pi.reported_quantity, pi.confirmed_quantity, m.avg_weight_kg)
    from public.pickup_items pi
    join public.delivery_pickups dp on dp.pickup_id = pi.pickup_id
    join public.materials m on m.id = pi.material_id
   where dp.delivery_id = p_delivery_id;

  -- La muestra de entrenamiento recibe el conteo del acopio: la mejor verdad para medir a la IA.
  update ruma_analytics.pickup_count_samples s
     set center_quantity = pi.center_quantity, center_weight_kg = pi.center_weight_kg
    from public.pickup_items pi
    join public.delivery_pickups dp on dp.pickup_id = pi.pickup_id
   where dp.delivery_id = p_delivery_id and (pi.center_quantity is not null or pi.center_weight_kg is not null)
     and s.pickup_id = pi.pickup_id and s.material_id = pi.material_id;

  -- confirmed_quantity queda como las unidades que se pagaron.
  update public.pickup_items pi
     set confirmed_quantity = round(b.q)::integer
    from pg_temp.ruma_settle_basis b
   where b.pickup_item_id = pi.id;

  for r in
    -- Puntos (2026-11-17): por unidad contada en la puerta x estado, nunca más que su parte del peso
    -- x points_per_kg x weight_cap_factor; luego el bono por juntar según las unidades del recojo.
    select p.id, p.code, p.generator_id, p.generator_points_awarded, p.rider_id,
           coalesce(sum(least(
             b.q * m.points_per_unit * case coalesce(pi.center_condition, pi.condition)
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

commit;
