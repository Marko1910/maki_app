-- MAKI · 2026-10-09
-- Precios reales de mercado, economía de puntos, canje de arbitrios municipales,
-- ranking de Eco-Riders e insignias de demo.
--
-- Estado: APLICADO en STAGING el 2026-10-09.
--         PRODUCCIÓN, el 2026-10-17: aplicados el PASO 1 (enum) y el PASO 2 sobre
--         `materials`. El resto quedó pendiente — se continúa en
--         `2026-10-17_produccion_pendiente.sql`, que es el que hay que correr.
--         El PASO 5 NO va a producción nunca: inserta ranking e insignias con los
--         UUID de las cuentas de prueba de staging, que allí no existen.
--
-- Cómo ejecutarlo: Supabase → SQL Editor del proyecto STAGING (mbbdrmicjobydprvbpsj),
-- primero el PASO 1 solo (ALTER TYPE no puede ir en la misma transacción que su uso),
-- luego el resto. Verificado en staging antes de repetirlo en producción
-- (kqwyygxpcsmworvmjqdv).
--
-- Nada de esto se puede hacer desde la app: RLS cierra redemption_options,
-- materials, center_materials, leaderboard_* y user_achievements a escritura del
-- cliente, que es justamente lo que protege el dinero y los puntos.


-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 1 — ejecutar SOLO esta línea, luego el resto del archivo
-- ─────────────────────────────────────────────────────────────────────────────
alter type redemption_kind add value if not exists 'municipal_discount';


-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 2 — Precios reales (S/ por kg) y los puntos que pagan
-- ─────────────────────────────────────────────────────────────────────────────
-- Perú no publica un índice oficial de precios de reciclables: cada acopio pone
-- el suyo. Estas son las mejores referencias disponibles (2026):
--
--   PET  S/1.00/kg  La República (sep-2024) cita S/0.70/kg pagado informalmente
--                   por botellas; un acopio formal paga 0.90–1.30 por PET limpio.
--   ALU  S/4.00/kg  Aluminio LME = S/11 588/t (IndexMundi, mar-2026) = S/11.59/kg;
--                   un acopio peruano paga ~35 % del valor del metal por latas limpias.
--   CAR  S/0.60/kg  Mercado local de cartón (la misma nota cita S/1.70/kg como caso puntual).
--   VID  S/0.20/kg  El flujo de menor valor (informe de precios Colombia, feb-2025).
--   PIL  S/0.00/kg  Nadie compra pilas: se reciben para disposición segura.
--
-- Puntos: la familia se queda con el 40 % del valor del material y 1 000 pts = S/1.00
-- (la opción Yape/Plin de redemption_options), así que
--
--     points_per_kg = precio_por_kg × 0.40 × 1000 = precio_por_kg × 400
--
-- Eso deja PET en los 400 pts/kg que ya tenía; los demás materiales estaban
-- descuadrados (una lata pagaba menos que una botella aunque el aluminio vale 4×).
-- Las pilas reciben un incentivo cívico fijo porque no tienen valor de reventa.

with precio(code, soles_kg, pts_kg) as (
    values ('PET', 1.00, 400.0),
           ('ALU', 4.00, 1600.0),
           ('CAR', 0.60, 240.0),
           ('VID', 0.20, 80.0),
           ('PIL', 0.00, 250.0)   -- incentivo, no precio de mercado
)
update materials m
   set points_per_kg   = p.pts_kg,
       -- Lo que ve la familia al escanear: puntos por unidad según su peso medio.
       points_per_unit = greatest(1, round(p.pts_kg * coalesce(m.avg_weight_kg, 0))),
       updated_at      = now()
  from precio p
 where m.code = p.code;

-- Mismo precio de referencia en cada acopio que acepta el material. Un acopio
-- revende alrededor de 1.8× lo que paga.
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

-- Deja la foto del cambio en el histórico, que es de donde sale v_material_prices.
insert into material_price_history (center_material_id, buy_price_per_kg, sell_price_per_kg, recorded_at)
select cm.id, cm.buy_price_per_kg, cm.sell_price_per_kg, now()
  from center_materials cm;


-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 3 — Canje: descuento en arbitrios municipales
-- ─────────────────────────────────────────────────────────────────────────────
-- MAKI trabaja con la municipalidad: los Eco-Puntos también bajan el recibo de
-- arbitrios. Misma tasa que el efectivo (1 000 pts = S/1.00) para que el vecino
-- no tenga que comparar dos monedas.

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
-- PASO 5 — Datos de demo: ranking de Eco-Riders e insignias
-- ─────────────────────────────────────────────────────────────────────────────
insert into leaderboard_periods
    (scope, period_type, participant_role, block_label, period_start, period_end, status)
select 'district', 'weekly', 'eco_rider', 'Trujillo',
       date_trunc('week', now())::date,
       (date_trunc('week', now()) + interval '6 days')::date,
       'open'
 where not exists (
     select 1 from leaderboard_periods
      where participant_role = 'eco_rider' and status = 'open'
 );

-- Solo riders reales: leaderboard_entries.user_id es NOT NULL, así que el tablero
-- no admite nombres de relleno (lo aprendí intentándolo).
insert into leaderboard_entries (period_id, rank, display_name, points, streak_days, user_id)
select p.id, v.rank, v.name, v.points, v.streak, v.uid::uuid
  from leaderboard_periods p
  cross join (values
        (1, 'Lucía',  1420, 9, 'cccccccc-cccc-cccc-cccc-cccccccccccc'),
        (2, 'Carlos', 1180, 6, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')
      ) as v(rank, name, points, streak, uid)
 where p.participant_role = 'eco_rider' and p.status = 'open'
   and not exists (select 1 from leaderboard_entries e where e.period_id = p.id)
;

insert into user_achievements (user_id, achievement_id, unlocked, progress, unlocked_at)
select v.uid::uuid, a.id, v.unlocked, v.progress,
       case when v.unlocked then now() end
  from (values
        ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'FIRST_PICKUP', true, 100),
        ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'KG_10',        true, 100),
        ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'DEFENSOR',     false, 60),
        ('11111111-1111-1111-1111-111111111111', 'FIRST_PICKUP', true, 100),
        ('11111111-1111-1111-1111-111111111111', 'STREAK_21',    true, 100)
      ) as v(uid, code, unlocked, progress)
  join achievements a on a.code = v.code
on conflict do nothing;


-- ─────────────────────────────────────────────────────────────────────────────
-- Verificación
-- ─────────────────────────────────────────────────────────────────────────────
-- select code, points_per_unit, points_per_kg from materials order by sort_order;
-- select * from v_material_prices;
-- select code, name, kind, partner from redemption_options where is_active;
-- select rank, display_name, points from leaderboard_entries
--   join leaderboard_periods p on p.id = period_id
--  where p.participant_role = 'eco_rider' order by rank;
