-- 2026-11-18 · El rider PESA en la puerta y el acopio CUENTA cada saco (decidido por el dueño)
-- APLICADO en STAGING (mbbdrmicjobydprvbpsj) el 2026-11-18. Falta repetirlo en producción.
-- Verificar con supabase/tests/saco_pesado_staging.sql (se revierte solo; espera "8/8 PASA").
--
-- Por qué: con 100 botellas en un saco, contarlas en la puerta no es realista. Ahora:
--   * Cada persona (familia o miembro) tiene un código único de saco, profiles.ruma_code ("RU-7K3Q9A"), impreso en su
--     saco y visible en la app. La app no puede cambiarlo (no tiene permiso sobre la columna).
--   * Puerta: el rider pesa cada material del saco (pickup_items.weight_kg). Solo él, solo al llegar o recoger.
--   * Acopio: cuenta las unidades de cada saco y califica su estado (pickup_items.center_quantity y center_condition),
--     solo el operador del centro que recibió la entrega, con la entrega recibida o pesada.
--   * settle_delivery paga por lo que contó el acopio (unidades x estado, con el tope por peso y el bono por juntar de
--     siempre). Si un saco no se contó, usa lo que contó el rider (APK viejos) o lo declarado, como antes.
--   * La muestra de entrenamiento (ruma_analytics) guarda además el conteo del acopio: la verdad más fiable para
--     medir a la IA.
-- La lógica de pago del rider no cambia (kg pesados en el acopio + base por ruta).

begin;

-- ---- Código único de saco por persona ----
alter table public.profiles add column if not exists ruma_code text;
do $$ begin
  alter table public.profiles add constraint profiles_ruma_code_key unique (ruma_code);
exception when duplicate_table or duplicate_object then null; end $$;

-- Sin 0/O ni 1/I/L: se lee en voz alta y a mano en un saco sin confusiones. 31^6 ≈ 887 millones de códigos.
create or replace function public.new_ruma_code()
returns text language plpgsql volatile set search_path = public
as $$
declare
  alphabet constant text := '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
  c text;
begin
  loop
    c := 'RU-' || (select string_agg(substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1), '')
                     from generate_series(1, 6));
    exit when not exists (select 1 from profiles where ruma_code = c);
  end loop;
  return c;
end $$;
revoke all on function public.new_ruma_code() from public, anon, authenticated;

create or replace function public.set_ruma_code()
returns trigger language plpgsql security definer set search_path = public
as $$
begin
  if new.ruma_code is null then new.ruma_code := public.new_ruma_code(); end if;
  return new;
end $$;
revoke all on function public.set_ruma_code() from public, anon, authenticated;

drop trigger if exists trg_profiles_ruma_code on public.profiles;
create trigger trg_profiles_ruma_code before insert on public.profiles
  for each row execute function public.set_ruma_code();

-- Backfill fila por fila: un UPDATE masivo no ve los códigos que él mismo va asignando.
do $$ declare r record; begin
  for r in select id from public.profiles where ruma_code is null loop
    update public.profiles set ruma_code = public.new_ruma_code() where id = r.id;
  end loop;
end $$;
alter table public.profiles alter column ruma_code set not null;

-- ---- Peso en la puerta, conteo y estado en el acopio ----
alter table public.pickup_items
  add column if not exists center_quantity integer,
  add column if not exists center_condition text;
do $$ begin
  alter table public.pickup_items add constraint pickup_items_center_quantity_check check (center_quantity >= 0);
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.pickup_items add constraint pickup_items_center_condition_check
    check (center_condition in ('limpia', 'aplastada', 'sucia'));
exception when duplicate_object then null; end $$;
do $$ begin
  alter table public.pickup_items add constraint pickup_items_weight_kg_check check (weight_kg >= 0 and weight_kg <= 500);
exception when duplicate_object then null; end $$;
grant update (weight_kg, center_quantity, center_condition) on public.pickup_items to authenticated;

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
    -- weight_kg is NOT NULL with its default: the family has no INSERT grant on it, so it is left alone.
    new.center_quantity := null;
    new.center_condition := null;
    return new;
  end if;

  -- Puerta: el rider del recojo pesa (y los APK viejos todavía cuentan y marcan estado).
  if (new.weight_kg is distinct from old.weight_kg
      or new.confirmed_quantity is distinct from old.confirmed_quantity
      or new.condition is distinct from old.condition)
     and (v_pickup.rider_id is distinct from auth.uid() or v_pickup.status::text not in ('arriving', 'collecting')) then
    raise exception 'Solo el Eco-Rider del recojo registra el peso, en la puerta.' using errcode = '42501';
  end if;
  -- Acopio: el operador del centro que recibió el saco lo cuenta y califica.
  if (new.center_quantity is distinct from old.center_quantity or new.center_condition is distinct from old.center_condition)
     and not exists (
       select 1 from public.delivery_pickups dp join public.deliveries d on d.id = dp.delivery_id
        where dp.pickup_id = new.pickup_id and d.status::text in ('recibida', 'pesada')
          and public.is_operator() and d.center_id = public.current_operator_center()) then
    raise exception 'Solo el centro de acopio que recibió el saco lo cuenta, antes de liquidar.' using errcode = '42501';
  end if;
  if new.reported_quantity is distinct from old.reported_quantity
     and (v_pickup.generator_id is distinct from auth.uid() or v_pickup.status::text not in ('requested', 'assigned', 'en_route', 'arriving')) then
    raise exception 'Solo quien pidió el recojo corrige lo declarado, antes de la recolección.' using errcode = '42501';
  end if;
  return new;
end;
$function$;

-- ---- Muestra de entrenamiento: el conteo del acopio ----
alter table ruma_analytics.pickup_count_samples add column if not exists center_quantity integer;

-- ---- Liquidación: paga por el conteo del acopio ----
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
  -- Base del reparto (2026-11-18): lo que CONTÓ EL ACOPIO en cada saco; si no lo contó, lo que contó el rider
  -- (APK viejos) y, sin eso, lo declarado.
  -- Se fija antes de que la siguiente sentencia reescriba confirmed_quantity desde el peso.
  -- drop + create y no DELETE: pg_safeupdate rechaza un DELETE sin WHERE dentro de una petición de la API.
  drop table if exists pg_temp.ruma_settle_basis;
  create temp table ruma_settle_basis (pickup_item_id uuid primary key, q numeric not null) on commit drop;
  insert into pg_temp.ruma_settle_basis (pickup_item_id, q)
  select pi.id, greatest(0, coalesce(pi.center_quantity, pi.confirmed_quantity, pi.reported_quantity))
    from public.pickup_items pi
    join public.delivery_pickups dp on dp.pickup_id = pi.pickup_id
   where dp.delivery_id = p_delivery_id;

  -- La muestra de entrenamiento recibe el conteo del acopio: la mejor verdad para medir a la IA.
  update ruma_analytics.pickup_count_samples s
     set center_quantity = pi.center_quantity
    from public.pickup_items pi
    join public.delivery_pickups dp on dp.pickup_id = pi.pickup_id
   where dp.delivery_id = p_delivery_id and pi.center_quantity is not null
     and s.pickup_id = pi.pickup_id and s.material_id = pi.material_id;

  -- confirmed_quantity queda como las unidades finales: las contadas por el acopio, o las estimadas por el peso.
  update public.pickup_items pi
     set confirmed_quantity = coalesce(pi.center_quantity, greatest(0, round(
           di.weight_kg
           * ((select b.q from pg_temp.ruma_settle_basis b where b.pickup_item_id = pi.id)::numeric / nullif((
                select sum((select b.q from pg_temp.ruma_settle_basis b where b.pickup_item_id = pi2.id))
                  from public.delivery_pickups dp2
                  join public.pickup_items pi2 on pi2.pickup_id = dp2.pickup_id
                 where dp2.delivery_id = p_delivery_id
                   and pi2.material_id = pi.material_id), 0))
           / nullif(m.avg_weight_kg, 0)
         ))::integer)
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
