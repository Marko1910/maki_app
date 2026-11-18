-- Checks supabase/sql/2026-11-18_ruma_guardada.sql in STAGING. Everything runs inside one DO block that ends by
-- raising its report, so Postgres rolls it all back: no detection, pickup or analytics row survives. Run:
--   npx supabase db query --remote=mbbdrmicjobydprvbpsj --agent no -f supabase/tests/ruma_guardada_staging.sql --yes
-- Expect "RESULTADO: 9/9 PASA".
do $$
declare
  fam uuid := (select id from auth.users where email = 'generador@ruma.test');
  rid uuid := (select id from auth.users where email = 'rider@ruma.test');
  pet uuid := (select id from public.materials where code = 'PET');
  base int; d1 uuid; d2 uuid; pk uuid; pk2 uuid; n int; ok int := 0; total int := 0; rep text := '';
  procedure_ok boolean;
begin
  -- Two scans hours apart: 3 bottles, then 4 more (inserted as the owner, like detect-material does).
  select coalesce(sum(di.quantity), 0) into base
    from public.detection_items di where di.material_id = pet
     and di.detection_id in (select public.saved_detection_ids(fam));
  insert into public.detections (generator_id, status, total_points, detected_at)
    values (fam, 'pending', 30, now() - interval '5 hours') returning id into d1;
  insert into public.detection_items (detection_id, material_id, quantity, points) values (d1, pet, 3, 30);
  insert into public.detections (generator_id, status, total_points) values (fam, 'pending', 40) returning id into d2;
  insert into public.detection_items (detection_id, material_id, quantity, points) values (d2, pet, 4, 40);

  -- As the family.
  perform set_config('request.jwt.claims', json_build_object('sub', fam, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';

  select quantity into n from public.saved_pile() where code = 'PET';
  total := total + 1; procedure_ok := n = base + 7;
  ok := ok + procedure_ok::int; rep := rep || format(E'\n%s pila junta 3+4 (%s, base %s)', case when procedure_ok then 'PASA' else 'FALLA' end, n, base);

  pk := public.create_pickup_from_detection(null, current_date, 'Prueba ruma guardada');
  select reported_quantity into n from public.pickup_items where pickup_id = pk and material_id = pet;
  total := total + 1; procedure_ok := n = base + 7;
  ok := ok + procedure_ok::int; rep := rep || format(E'\n%s recojo declara la suma (%s)', case when procedure_ok then 'PASA' else 'FALLA' end, n);

  select count(*) into n from public.saved_pile();
  total := total + 1; procedure_ok := n = 0;
  ok := ok + procedure_ok::int; rep := rep || format(E'\n%s pila vacía tras pedir (%s filas)', case when procedure_ok then 'PASA' else 'FALLA' end, n);

  -- Cancel: the bottles go back to the pile.
  update public.pickups set status = 'cancelled', cancel_reason = 'prueba' where id = pk;
  select quantity into n from public.saved_pile() where code = 'PET';
  total := total + 1; procedure_ok := n = base + 7;
  ok := ok + procedure_ok::int; rep := rep || format(E'\n%s cancelar devuelve a la pila (%s)', case when procedure_ok then 'PASA' else 'FALLA' end, n);

  -- Ask again and let the rider collect it, counting 6 at the door.
  pk2 := public.create_pickup_from_detection(null, current_date, 'Prueba ruma guardada');
  perform set_config('request.jwt.claims', json_build_object('sub', rid, 'role', 'authenticated')::text, true);
  update public.pickups set rider_id = rid, status = 'assigned' where id = pk2 and status = 'requested' and rider_id is null;
  update public.pickups set status = 'en_route' where id = pk2;
  update public.pickups set status = 'arriving' where id = pk2;
  update public.pickups set status = 'collecting' where id = pk2;

  select count(*) into n from public.detections where id in (d1, d2);
  total := total + 1; procedure_ok := n = 2;
  ok := ok + procedure_ok::int; rep := rep || format(E'\n%s el rider ve ambos escaneos (%s)', case when procedure_ok then 'PASA' else 'FALLA' end, n);

  update public.pickup_items set confirmed_quantity = 6, condition = 'limpia' where pickup_id = pk2 and material_id = pet;
  perform public.complete_pickup(pk2);
  execute 'reset role';

  select count(*) into n from public.detections where id in (d1, d2) and status = 'collected' and pickup_id = pk2;
  total := total + 1; procedure_ok := n = 2;
  ok := ok + procedure_ok::int; rep := rep || format(E'\n%s ambos escaneos quedan recogidos (%s)', case when procedure_ok then 'PASA' else 'FALLA' end, n);

  select ai_quantity into n from ruma_analytics.pickup_count_samples where pickup_id = pk2 and material_id = pet;
  total := total + 1; procedure_ok := n = base + 7;
  ok := ok + procedure_ok::int; rep := rep || format(E'\n%s analítica: IA suma ambos escaneos (%s)', case when procedure_ok then 'PASA' else 'FALLA' end, n);

  select rider_quantity into n from ruma_analytics.pickup_count_samples where pickup_id = pk2 and material_id = pet;
  total := total + 1; procedure_ok := n = 6;
  ok := ok + procedure_ok::int; rep := rep || format(E'\n%s analítica: conteo del rider (%s)', case when procedure_ok then 'PASA' else 'FALLA' end, n);

  -- The client still cannot write detections directly.
  total := total + 1; procedure_ok := not has_table_privilege('authenticated', 'public.detections', 'UPDATE');
  ok := ok + procedure_ok::int; rep := rep || format(E'\n%s la app no puede editar detecciones', case when procedure_ok then 'PASA' else 'FALLA' end);

  raise exception 'RESULTADO: %/% PASA (todo revertido)%', ok, total, rep;
end $$;
