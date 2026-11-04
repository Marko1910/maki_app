-- MAKI · 2026-10-17
-- Lo que FALTA de 2026-10-09_precios_reales_y_arbitrios.sql en PRODUCCIÓN.
--
-- Por qué existe este archivo: el 2026-10-17 se replicó el script del 09-oct a
-- producción (kqwyygxpcsmworvmjqdv) desde el MCP de Supabase y el clasificador de
-- permisos cortó a mitad. Quedó aplicado:
--
--   ✔ PASO 1  alter type redemption_kind add value 'municipal_discount'
--   ✔ PASO 2a update materials (points_per_kg, points_per_unit)
--
-- y quedó PENDIENTE todo lo de abajo. Mientras tanto producción está
-- INCONSISTENTE: materials ya paga los puntos nuevos pero center_materials sigue
-- con los precios viejos. No afecta a nadie (producción tiene 0 perfiles y 0
-- riders), pero hay que cerrarlo antes de que entre el primer usuario real.
--
-- Cómo ejecutarlo: Supabase → SQL Editor del proyecto PRODUCCIÓN
-- (kqwyygxpcsmworvmjqdv) → pegar este archivo entero → Run. Todo lo de aquí es
-- idempotente, así que volver a correrlo no rompe nada.
--
-- NO INCLUYE el PASO 5 del script original (ranking de Eco-Riders e insignias de
-- demo) y es deliberado: ese paso inserta leaderboard_entries y user_achievements
-- con los UUID de las cuentas de prueba de STAGING
-- (aaaaaaaa-…, cccccccc-…, 11111111-…), que en producción no existen. O falla por
-- clave foránea, o mete datos falsos en la base real. Los datos de demo se quedan
-- en staging, que es para lo que está.


-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 2b — Precio de referencia en cada acopio
-- ─────────────────────────────────────────────────────────────────────────────
-- Un acopio revende alrededor de 1.8× lo que paga.
with precio(code, soles_kg) as (
    values ('PET', 1.00), ('ALU', 4.00), ('CAR', 0.60), ('VID', 0.20), ('PIL', 0.00)
)
update center_materials cm
   set buy_price_per_kg  = p.soles_kg,
       sell_price_per_kg = round((p.soles_kg * 1.8)::numeric, 2),
       updated_at        = now()
  from materials m
  join precio p on p.code = m.code
 where cm.material_id = m.id;


-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 2c — Foto del cambio en el histórico (de ahí sale v_material_prices)
-- ─────────────────────────────────────────────────────────────────────────────
-- Con el `not exists` para que correr el archivo dos veces no duplique la foto
-- del mismo día; el original no lo llevaba porque se ejecutaba una sola vez.
insert into material_price_history (center_material_id, buy_price_per_kg, sell_price_per_kg, recorded_at)
select cm.id, cm.buy_price_per_kg, cm.sell_price_per_kg, now()
  from center_materials cm
 where not exists (
     select 1 from material_price_history h
      where h.center_material_id = cm.id
        and h.buy_price_per_kg   = cm.buy_price_per_kg
        and h.sell_price_per_kg  = cm.sell_price_per_kg
        and h.recorded_at::date  = current_date
 );


-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 3 — Canje: descuento en arbitrios municipales
-- ─────────────────────────────────────────────────────────────────────────────
-- Misma tasa que el efectivo (1 000 pts = S/1.00) para que el vecino no tenga
-- que comparar dos monedas.
insert into redemption_options
    (code, name, kind, description, points_per_unit, min_points, unit_value,
     partner, icon, color, is_active, sort_order)
values
    ('MUNI_ARBITRIOS', 'Descuento en Arbitrios', 'municipal_discount',
     '1,000 pts = S/1.00 de descuento en tus arbitrios municipales',
     1000, 1000, 1.00, 'Municipalidad', 'account_balance', '#185FA5', true, 2)
on conflict (code) do update
   set name            = excluded.name,
       kind            = excluded.kind,
       description     = excluded.description,
       points_per_unit = excluded.points_per_unit,
       min_points      = excluded.min_points,
       unit_value      = excluded.unit_value,
       partner         = excluded.partner,
       icon            = excluded.icon,
       color           = excluded.color,
       is_active       = true,
       sort_order      = excluded.sort_order;


-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 4 — Turno programado del Eco-Rider
-- ─────────────────────────────────────────────────────────────────────────────
-- "Salgo a las 3:00 pm": la app lo usa para encender el turno sola y para
-- decirle a la familia a qué hora saldrá su recojo.
alter table rider_profiles
    add column if not exists shift_start_at timestamptz,
    add column if not exists shift_end_at   timestamptz;

comment on column rider_profiles.shift_start_at is
    'Hora a la que el rider dijo que sale a ruta; la app enciende is_available ahí.';


-- ─────────────────────────────────────────────────────────────────────────────
-- Verificación — pegar aparte después de correr lo de arriba
-- ─────────────────────────────────────────────────────────────────────────────
-- Las cuatro consultas en una. Lo esperado:
--   materials_ok        5   (PET 400, ALU 1600, CAR 240, VID 80, PIL 250)
--   acopios_ok          5   (buy = 1.00 / 4.00 / 0.60 / 0.20 / 0.00)
--   historico          >=5
--   arbitrios_ok        1
--   columnas_turno      2
--
-- select
--   (select count(*) from materials
--     where (code,points_per_kg) in (('PET',400),('ALU',1600),('CAR',240),('VID',80),('PIL',250))) as materials_ok,
--   (select count(*) from center_materials cm join materials m on m.id = cm.material_id
--     where (m.code,cm.buy_price_per_kg) in (('PET',1.00),('ALU',4.00),('CAR',0.60),('VID',0.20),('PIL',0.00))) as acopios_ok,
--   (select count(*) from material_price_history)                                   as historico,
--   (select count(*) from redemption_options where code = 'MUNI_ARBITRIOS' and is_active) as arbitrios_ok,
--   (select count(*) from information_schema.columns
--     where table_name = 'rider_profiles'
--       and column_name in ('shift_start_at','shift_end_at'))                       as columnas_turno;
