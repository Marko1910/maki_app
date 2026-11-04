-- Ruma · 2026-10-30
-- NO APLICADO en ningún entorno. Ejecutar en el editor SQL de STAGING
-- (mbbdrmicjobydprvbpsj) y después repetir en producción.
--
-- El renombrado a Ruma llegó al código pero no a los datos: `rider_code` sigue
-- diciendo MAKI-R-0042 y es visible al usuario, en la tarjeta de su perfil.
--
-- No se pudo aplicar desde la sesión: el rider recibe 403 al intentar cambiar su
-- propio código, que es lo correcto — un identificador no es una preferencia.

begin;

update public.rider_profiles
   set rider_code = 'RUMA' || substring(rider_code from 5),
       updated_at = now()
 where rider_code like 'MAKI%';

commit;

-- ============================================================================
-- LO QUE NO SE PUDO COMPROBAR DESDE LA SESIÓN
-- ============================================================================
-- Si los códigos los acuña un DEFAULT o un trigger (como `trg_set_delivery_code`
-- hace con las entregas), este UPDATE arregla los que existen pero el próximo
-- rider volverá a nacer con MAKI. El esquema se construyó por el editor SQL y esa
-- definición no está en el repo, así que hay que mirarla antes de dar esto por
-- cerrado:
--
--   select column_default from information_schema.columns
--    where table_schema = 'public' and table_name = 'rider_profiles'
--      and column_name = 'rider_code';
--
--   select p.proname, pg_get_functiondef(p.oid)
--     from pg_proc p join pg_trigger t on t.tgfoid = p.oid
--     join pg_class c on c.oid = t.tgrelid
--    where c.relname = 'rider_profiles';
--
-- Si aparece 'MAKI' ahí dentro, hay que cambiarlo también.

-- Verificación posterior:
-- select rider_code from public.rider_profiles order by rider_code;
