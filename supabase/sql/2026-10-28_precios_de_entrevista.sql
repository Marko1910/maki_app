-- Ruma · 2026-10-28
-- NO APLICADO en ningún entorno. Requiere una decisión de negocio (ver abajo).
--
-- Alinea el catálogo con los únicos precios que tenemos de un comprador real:
-- entrevista a un centro de acopio (octubre 2026) que junta 30 t de chatarra cada
-- 15 días y se las vende a Aceros Arequipa.
--
--   chatarra (ferroso)  S/0.60/kg   S/600/t
--   cartón              S/0.30/kg   S/300/t
--   botella PET         S/1.00/kg   S/1000/t
--
-- El precio no cambia con el volumen: 600 = 0.60 × 1000 exacto en los tres. Quien
-- entrega 2 toneladas cobra lo mismo por kilo que quien entrega 20.
--
-- ============================================================================
-- LO QUE ESTE SCRIPT CORRIGE Y POR QUÉ
-- ============================================================================
--
-- 1. CARTÓN: el catálogo asume S/0.60/kg y paga al hogar S/0.24. El precio real es
--    S/0.30, así que el hogar se está llevando el 80 % y no el 40 % que promete la
--    regla. Quedan S/0.06 por kilo para el rider y la plataforma: menos que lo que
--    cobra el hogar. Es el material que la bodega entrevistada produce a diario.
--
-- 2. FERROSO: no existe en el catálogo, y es el material que de verdad mueve el
--    acopio (60 t/mes). Peor: el prompt de visión dice «ALU = lata», así que una
--    lata de conservas (hojalata) se clasifica como aluminio y paga S/1.60/kg por
--    material que el acopio compra a S/0.60. Añadirlo cierra esa fuga.
--
-- 3. ALUMINIO y VIDRIO siguen SIN VERIFICAR y este script NO los toca. El S/4.00
--    del aluminio sale de LME × 35 % en 2026-10-09_precios_reales_y_arbitrios.sql,
--    no de un acopio peruano, y el centro entrevistado no cotiza ninguno de los
--    dos. Entre ambos sostienen ~31 % del margen proyectado del modelo. Antes de
--    enseñar una proyección a nadie, hay que pedirle al acopio su lista de precios.
--
-- ============================================================================
-- LA DECISIÓN QUE NO PUEDE TOMAR UN SCRIPT
-- ============================================================================
-- Bajar el cartón a su precio real reduce lo que cobra el hogar de S/0.24 a S/0.12
-- por kilo. Es la mitad, y se nota en la billetera de gente que ya está dentro.
-- La alternativa es sostener el pago actual y asumir que ese material va a pérdida
-- hasta conseguir un mejor precio de venta. Las dos son defendibles; la que no lo
-- es, es seguir creyendo que se paga el 40 %.
--
-- Este script implementa la primera. Si se elige la segunda, no se ejecuta la
-- sección de cartón y se anota la pérdida por kilo en el modelo.

begin;

-- ---------------------------------------------------------------------------
-- 1. Cartón al precio real: 40 % de S/0.30 son 120 pts/kg.
-- ---------------------------------------------------------------------------
update public.materials
   set points_per_kg = 120.0,
       -- Una caja mediana pesa ~50 g, así que la unidad baja en la misma proporción.
       points_per_unit = 6.0,
       updated_at = now()
 where code = 'CAR';

update public.center_materials cm
   set buy_price_per_kg = 0.30,
       -- El 1.8× es un supuesto heredado, no un dato: ningún acopio nos ha dado su
       -- precio de venta. Se conserva para no inventar otro, pero queda señalado.
       sell_price_per_kg = round((0.30 * 1.8)::numeric, 2),
       updated_at = now()
  from public.materials m
 where m.id = cm.material_id and m.code = 'CAR';

-- ---------------------------------------------------------------------------
-- 2. El material que mueve el acopio y que el app no tenía.
-- ---------------------------------------------------------------------------
insert into public.materials (code, name, icon, points_per_kg, points_per_unit, avg_weight_kg, is_active)
values ('FER', 'Chatarra (hojalata y fierro)', '🔩', 240.0, 12.0, 0.0500, true)
on conflict (code) do update
   set name = excluded.name,
       points_per_kg = excluded.points_per_kg,
       points_per_unit = excluded.points_per_unit,
       avg_weight_kg = excluded.avg_weight_kg,
       is_active = true,
       updated_at = now();

-- Todos los acopios que ya compran PET compran también chatarra: es su negocio.
insert into public.center_materials (center_id, material_id, buy_price_per_kg, sell_price_per_kg)
select distinct cm.center_id, (select id from public.materials where code = 'FER'),
       0.60, round((0.60 * 1.8)::numeric, 2)
  from public.center_materials cm
  join public.materials m on m.id = cm.material_id
 where m.code = 'PET'
on conflict (center_id, material_id) do nothing;

commit;

-- ============================================================================
-- FALTA FUERA DE ESTE SCRIPT (no es SQL)
-- ============================================================================
-- El prompt de `supabase/functions/detect-material/index.ts` dice «ALU=lata» y hay
-- que enseñarle a separar la lata de bebida (aluminio, liviana, se abolla con la
-- mano) de la lata de conservas (hojalata, imantada). Mientras no lo haga, añadir
-- FER al catálogo no cierra la fuga: solo le da a dónde ir.

-- Verificación posterior:
-- select code, name, points_per_kg, points_per_kg/1000.0 as paga_soles_kg,
--        (points_per_kg/1000.0)/0.40 as precio_implicito
--   from public.materials where is_active order by code;
