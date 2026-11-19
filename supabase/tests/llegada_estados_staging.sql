-- Checks supabase/sql/2026-11-19_llegada_y_estados_entrega.sql in STAGING. Uses a real (non-test) rider account
-- inside one DO block that ends by raising its report, so everything is rolled back. Run:
--   npx supabase db query --remote=mbbdrmicjobydprvbpsj --agent no -f supabase/tests/llegada_estados_staging.sql --yes
-- Expect "RESULTADO: 7/7 PASA".
do $$
declare
  fam uuid := (select id from auth.users where email = 'quispe@ruma.test');
  rid uuid := (select p.id from profiles p join auth.users u on u.id = p.id
                where p.role = 'eco_rider' and u.email not like '%@ruma.test' order by p.created_at limit 1);
  op  uuid := (select id from auth.users where email = 'operador@ruma.test');
  center uuid := (select center_id from public.operator_profiles where profile_id = (select id from auth.users where email = 'operador@ruma.test'));
  pet uuid := (select id from public.materials where code = 'PET');
  pk uuid; dl uuid; ok int := 0; total int := 0; rep text := ''; msg text; n int; ts timestamptz;
begin
  -- A family pickup at a known point; the real rider takes it (set up as the owner).
  insert into public.pickups (generator_id, scheduled_date, window_label, latitude, longitude, status, rider_id)
    values (fam, current_date, 'Prueba llegada', -7.8840, -79.2210, 'arriving', rid) returning id into pk;
  insert into public.pickup_items (pickup_id, material_id, reported_quantity) values (pk, pet, 10);

  -- Rider 1 km away with a fresh fix: refused.
  update public.rider_profiles set current_latitude = -7.8930, current_longitude = -79.2210, last_location_at = now() where profile_id = rid;
  perform set_config('request.jwt.claims', json_build_object('sub', rid, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  begin
    update public.pickups set status = 'collecting' where id = pk;
    total := total + 1; rep := rep || E'\nFALLA a 1 km pudo marcar "He llegado"';
  exception when others then
    total := total + 1; msg := sqlerrm;
    if msg like 'Aún estás a % m de la casa%' then ok := ok + 1; rep := rep || E'\nPASA a 1 km: ' || msg; else rep := rep || E'\nFALLA a 1 km: ' || msg; end if;
  end;
  execute 'reset role';

  -- Old fix (10 min): refused, asks for GPS.
  update public.rider_profiles set current_latitude = -7.8841, current_longitude = -79.2211, last_location_at = now() - interval '10 minutes' where profile_id = rid;
  execute 'set local role authenticated';
  begin
    update public.pickups set status = 'collecting' where id = pk;
    total := total + 1; rep := rep || E'\nFALLA con GPS viejo pudo marcar';
  exception when others then
    total := total + 1; msg := sqlerrm;
    if msg like 'Activa tu GPS%' then ok := ok + 1; rep := rep || E'\nPASA GPS viejo: ' || msg; else rep := rep || E'\nFALLA GPS viejo: ' || msg; end if;
  end;
  execute 'reset role';

  -- 15 m away, fresh: allowed.
  update public.rider_profiles set last_location_at = now() where profile_id = rid;
  execute 'set local role authenticated';
  update public.pickups set status = 'collecting' where id = pk;
  select count(*) into n from public.pickups where id = pk and status = 'collecting';
  total := total + 1; ok := ok + (n = 1)::int;
  rep := rep || format(E'\n%s a 15 m marca "He llegado"', case when n = 1 then 'PASA' else 'FALLA' end);

  -- Delivery: the rider marks "Llegué al acopio".
  perform public.complete_pickup(pk);
  select (public.create_delivery(center, array[pk])).id into dl;
  perform public.mark_delivery_arrived(dl);
  select arrived_at into ts from public.deliveries where id = dl;
  total := total + 1; ok := ok + (ts is not null)::int;
  rep := rep || format(E'\n%s el rider marca la llegada al acopio', case when ts is not null then 'PASA' else 'FALLA' end);

  -- A family can't mark it.
  perform set_config('request.jwt.claims', json_build_object('sub', fam, 'role', 'authenticated')::text, true);
  begin
    perform public.mark_delivery_arrived(dl);
    total := total + 1; rep := rep || E'\nFALLA la familia pudo marcar la entrega';
  exception when others then
    total := total + 1; ok := ok + 1; rep := rep || E'\nPASA la familia no puede marcar la entrega';
  end;

  -- Operator starts counting: the rider is told.
  perform set_config('request.jwt.claims', json_build_object('sub', op, 'role', 'authenticated')::text, true);
  update public.deliveries set status = 'recibida', received_at = now(), received_by = op where id = dl;
  execute 'reset role';
  select count(*) into n from public.notifications where user_id = rid and reference_id = dl and title = 'Están contando tu carga';
  total := total + 1; ok := ok + (n = 1)::int;
  rep := rep || format(E'\n%s aviso "Están contando tu carga" al rider', case when n = 1 then 'PASA' else 'FALLA' end);

  -- Weighed and settled: "Terminado: pago en proceso".
  update public.pickup_items set center_weight_kg = 0.25, center_condition = 'limpia' where pickup_id = pk;
  insert into public.delivery_items (delivery_id, material_id, weight_kg, quality_grade, price_per_kg, grade_factor) values (dl, pet, 0.25, 'B', 1.0, 1.0);
  update public.deliveries set status = 'pesada', weighed_at = now() where id = dl;
  perform set_config('request.jwt.claims', json_build_object('sub', op, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  perform public.settle_delivery(dl);
  execute 'reset role';
  select count(*) into n from public.notifications where user_id = rid and reference_id = dl and title = 'Terminado: pago en proceso';
  total := total + 1; ok := ok + (n = 1)::int;
  rep := rep || format(E'\n%s aviso "Terminado: pago en proceso" al rider', case when n = 1 then 'PASA' else 'FALLA' end);

  raise exception 'RESULTADO: %/% PASA (todo revertido)%', ok, total, rep;
end $$;
