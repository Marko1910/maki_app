-- Ruma: las cuentas de prueba pasan de *@maki.test a *@ruma.test (2026-11-16, renombrado MAKI -> Ruma).
-- APLICADO en STAGING el 2026-11-16 (19 cuentas: el seed y las ref-* que crea referidos_staging.py), verificado con un
-- login de generador@ruma.test y con supabase/tests/canje_seguro_staging.py.
-- NO aplicar en producción: allí no existen cuentas de prueba.
--
-- Qué cambia: auth.users.email y auth.identities.identity_data->>'email' (identities.email es una columna
-- generada a partir de identity_data; provider_id es el uuid del usuario, no el correo, así que no se toca).
-- La clave no cambia (Maki12345!). Los uuid tampoco: nada en public referencia el correo.
-- Además ruma_analytics.capture_pickup_counts() marcaba como prueba lo que terminara en '@maki.test'
-- (test_account); se vuelve a crear con '@ruma.test' para que las cuentas renombradas sigan contando como prueba.
-- Los datos históricos de ruma_analytics.pickup_count_samples conservan el test_account que ya tenían.
-- Idempotente: si ya no queda ninguna cuenta @maki.test, no cambia nada.

begin;

update auth.users
   set email = regexp_replace(email, '@maki\.test$', '@ruma.test')
 where email like '%@maki.test';

update auth.identities
   set identity_data = jsonb_set(identity_data, '{email}',
         to_jsonb(regexp_replace(identity_data->>'email', '@maki\.test$', '@ruma.test')))
 where identity_data->>'email' like '%@maki.test';

do $$
declare v_def text := pg_get_functiondef('ruma_analytics.capture_pickup_counts()'::regprocedure);
begin
  if v_def like '%@maki.test%' then
    execute replace(v_def, '%@maki.test', '%@ruma.test');
  end if;
end $$;

commit;
