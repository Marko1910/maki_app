-- Checks supabase/sql/2026-11-18_saco_pesado_conteo_acopio.sql in STAGING with the *@ruma.test seed:
-- the rider weighs at the door, the acopio counts the labelled sack, and settle_delivery pays by that count.
-- Everything is rolled back by the final raise. Run:
--   npx supabase db query --remote=mbbdrmicjobydprvbpsj --agent no -f supabase/tests/saco_pesado_staging.sql --yes
-- Expect "RESULTADO: 8/8 PASA".
do $$
declare
  fam uuid := (select id from auth.users where email = 'generador@ruma.test');
  rid uuid := (select id from auth.users where email = 'rider@ruma.test');
  op  uuid := (select id from auth.users where email = 'operador@ruma.test');
  center uuid := (select center_id from public.operator_profiles where profile_id = (select id from auth.users where email = 'operador@ruma.test'));
  pet uuid := (select id from public.materials where code = 'PET');
  pk uuid; dl uuid; n numeric; ok int := 0; total int := 0; rep text := ''; pass boolean; msg text; code text;
begin
  select ruma_code into code from public.profiles where id = fam;
  total := total + 1; pass := code ~ '^RU-[2-9A-HJ-NP-Z]{6}$';
  ok := ok + pass::int; rep := rep || format(E'\n%s la familia tiene código de saco (%s)', case when pass then 'PASA' else 'FALLA' end, code);

  -- Family asks: declares 40 PET.
  perform set_config('request.jwt.claims', json_build_object('sub', fam, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.pickups (generator_id, scheduled_date, window_label, latitude, longitude)
    values (fam, current_date, 'Prueba saco', -7.884, -79.221) returning id into pk;
  insert into public.pickup_items (pickup_id, material_id, reported_quantity) values (pk, pet, 40);

  -- Rider: takes it, weighs 1.2 kg at the door.
  perform set_config('request.jwt.claims', json_build_object('sub', rid, 'role', 'authenticated')::text, true);
  update public.pickups set rider_id = rid, status = 'assigned' where id = pk and status = 'requested' and rider_id is null;
  update public.pickups set status = 'en_route' where id = pk;
  update public.pickups set status = 'arriving' where id = pk;
  update public.pickup_items set weight_kg = 1.2 where pickup_id = pk;
  select weight_kg into n from public.pickup_items where pickup_id = pk;
  total := total + 1; pass := n = 1.2;
  ok := ok + pass::int; rep := rep || format(E'\n%s el rider pesa en la puerta (%s kg)', case when pass then 'PASA' else 'FALLA' end, n);

  begin
    update public.pickup_items set center_quantity = 999 where pickup_id = pk;
    rep := rep || E'\nFALLA el rider pudo poner el conteo del acopio'; total := total + 1;
  exception when others then
    total := total + 1; ok := ok + 1; rep := rep || E'\nPASA el rider no puede poner el conteo del acopio';
  end;

  update public.pickups set status = 'collecting' where id = pk;
  perform public.complete_pickup(pk);
  select (public.create_delivery(center, array[pk])).id into dl;

  -- Acopio: receives, counts 47 bottles, grades them crushed, weighs the batch and settles.
  perform set_config('request.jwt.claims', json_build_object('sub', op, 'role', 'authenticated')::text, true);
  update public.deliveries set status = 'recibida', received_at = now(), received_by = op where id = dl;
  update public.pickup_items set center_quantity = 47, center_condition = 'aplastada' where pickup_id = pk;
  select center_quantity into n from public.pickup_items where pickup_id = pk;
  total := total + 1; pass := n = 47;
  ok := ok + pass::int; rep := rep || format(E'\n%s el acopio cuenta el saco (%s)', case when pass then 'PASA' else 'FALLA' end, n);

  -- The family can't touch the acopio's count.
  perform set_config('request.jwt.claims', json_build_object('sub', fam, 'role', 'authenticated')::text, true);
  begin
    update public.pickup_items set center_quantity = 500 where pickup_id = pk;
    total := total + 1; rep := rep || E'\nFALLA la familia pudo cambiar el conteo';
  exception when others then
    total := total + 1; ok := ok + 1; rep := rep || E'\nPASA la familia no puede cambiar el conteo';
  end;

  perform set_config('request.jwt.claims', json_build_object('sub', op, 'role', 'authenticated')::text, true);
  insert into public.delivery_items (delivery_id, material_id, weight_kg, quality_grade, price_per_kg, grade_factor)
    values (dl, pet, 1.3, 'B', 1.0, 1.0);
  update public.deliveries set status = 'pesada', weighed_at = now() where id = dl;
  perform public.settle_delivery(dl);
  execute 'reset role';

  select confirmed_quantity into n from public.pickup_items where pickup_id = pk;
  total := total + 1; pass := n = 47;
  ok := ok + pass::int; rep := rep || format(E'\n%s las unidades finales son las del acopio (%s)', case when pass then 'PASA' else 'FALLA' end, n);

  -- Expected: 47 x 10 pts x 1.10 (aplastada) = 517, capped by 1.3 kg x points_per_kg x 1.25; +10 % for 30+ units.
  select generator_points_final into n from public.pickups where id = pk;
  total := total + 1;
  pass := n = floor(least(47 * (select points_per_unit from public.materials where id = pet)
                            * (select condition_crushed_factor from public.platform_settings where id),
                          1.3 * (select points_per_kg from public.materials where id = pet)
                            * (select weight_cap_factor from public.platform_settings where id)) * 1.10);
  ok := ok + pass::int; rep := rep || format(E'\n%s puntos por el conteo del acopio x estado (%s)', case when pass then 'PASA' else 'FALLA' end, n);

  select center_quantity into n from ruma_analytics.pickup_count_samples where pickup_id = pk and material_id = pet;
  total := total + 1; pass := n = 47;
  ok := ok + pass::int; rep := rep || format(E'\n%s la muestra de entrenamiento guarda el conteo del acopio (%s)', case when pass then 'PASA' else 'FALLA' end, n);

  raise exception 'RESULTADO: %/% PASA (todo revertido)%', ok, total, rep;
end $$;
