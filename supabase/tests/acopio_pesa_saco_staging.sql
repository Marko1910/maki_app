-- Checks supabase/sql/2026-11-18_acopio_pesa_saco.sql in STAGING with the *@ruma.test seed: the rider only
-- carries the labelled sack, the acopio weighs each one and counts by hand only what the weight doesn't back.
-- Everything is rolled back by the final raise. Run:
--   npx supabase db query --remote=mbbdrmicjobydprvbpsj --agent no -f supabase/tests/acopio_pesa_saco_staging.sql --yes
-- Expect "RESULTADO: 8/8 PASA".
do $$
declare
  fam uuid := (select id from auth.users where email = 'generador@ruma.test');
  rid uuid := (select id from auth.users where email = 'rider@ruma.test');
  op  uuid := (select id from auth.users where email = 'operador@ruma.test');
  center uuid := (select center_id from public.operator_profiles where profile_id = (select id from auth.users where email = 'operador@ruma.test'));
  pet uuid := (select id from public.materials where code = 'PET');
  a uuid; b uuid; c uuid; dl uuid; n numeric; ok int := 0; total int := 0; rep text := ''; pass boolean; audit boolean;
begin
  -- The 1-in-N draw: with N = 1 every new pickup is audited (rolled back with the rest).
  update public.platform_settings set sack_audit_every = 1 where id;
  perform set_config('request.jwt.claims', json_build_object('sub', fam, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.pickups (generator_id, scheduled_date, window_label, latitude, longitude)
    values (fam, current_date, 'Prueba A', -7.884, -79.221) returning id, sack_audit into a, audit;
  total := total + 1; pass := audit;
  ok := ok + pass::int; rep := rep || format(E'\n%s el sorteo de conteo al azar se guarda al pedir (%s)', case when pass then 'PASA' else 'FALLA' end, audit);
  execute 'reset role';
  update public.platform_settings set sack_audit_every = 1000000 where id;
  execute 'set local role authenticated';
  insert into public.pickups (generator_id, scheduled_date, window_label, latitude, longitude)
    values (fam, current_date, 'Prueba B', -7.884, -79.221) returning id into b;
  insert into public.pickups (generator_id, scheduled_date, window_label, latitude, longitude)
    values (fam, current_date, 'Prueba C', -7.884, -79.221) returning id into c;
  insert into public.pickup_items (pickup_id, material_id, reported_quantity) values (a, pet, 40), (b, pet, 100), (c, pet, 100);

  -- Rider: takes the three sacks; no scale, just "recibí el saco".
  perform set_config('request.jwt.claims', json_build_object('sub', rid, 'role', 'authenticated')::text, true);
  update public.pickups set rider_id = rid, status = 'assigned' where id in (a, b, c);
  update public.pickups set status = 'en_route' where id in (a, b, c);
  update public.pickups set status = 'arriving' where id in (a, b, c);
  begin
    update public.pickup_items set center_weight_kg = 9 where pickup_id = a;
    total := total + 1; rep := rep || E'\nFALLA el rider pudo poner el peso del acopio';
  exception when others then
    total := total + 1; ok := ok + 1; rep := rep || E'\nPASA el rider no puede poner el peso del acopio';
  end;
  update public.pickups set status = 'collecting' where id in (a, b, c);
  perform public.complete_pickup(a); perform public.complete_pickup(b); perform public.complete_pickup(c);
  select (public.create_delivery(center, array[a, b, c])).id into dl;

  -- Acopio: weighs every sack. A: 40 declared, 1.0 kg (40 x 25 g) → backed. B: 100 declared, 0.5 kg → not backed,
  -- nobody counted. C: 100 declared, 0.6 kg → not backed, counted by hand: 22.
  perform set_config('request.jwt.claims', json_build_object('sub', op, 'role', 'authenticated')::text, true);
  update public.deliveries set status = 'recibida', received_at = now(), received_by = op where id = dl;
  update public.pickup_items set center_weight_kg = 1.0, center_condition = 'limpia' where pickup_id = a;
  update public.pickup_items set center_weight_kg = 0.5, center_condition = 'limpia' where pickup_id = b;
  update public.pickup_items set center_weight_kg = 0.6, center_quantity = 22, center_condition = 'limpia' where pickup_id = c;

  perform set_config('request.jwt.claims', json_build_object('sub', fam, 'role', 'authenticated')::text, true);
  begin
    update public.pickup_items set center_weight_kg = 50 where pickup_id = b;
    total := total + 1; rep := rep || E'\nFALLA la familia pudo cambiar el peso';
  exception when others then
    total := total + 1; ok := ok + 1; rep := rep || E'\nPASA la familia no puede cambiar el peso';
  end;

  perform set_config('request.jwt.claims', json_build_object('sub', op, 'role', 'authenticated')::text, true);
  insert into public.delivery_items (delivery_id, material_id, weight_kg, quality_grade, price_per_kg, grade_factor)
    values (dl, pet, 2.1, 'B', 1.0, 1.0);
  update public.deliveries set status = 'pesada', weighed_at = now() where id = dl;
  perform public.settle_delivery(dl);
  execute 'reset role';

  select confirmed_quantity into n from public.pickup_items where pickup_id = a;
  total := total + 1; pass := n = 40;
  ok := ok + pass::int; rep := rep || format(E'\n%s saco A: el peso respalda lo declarado, se pagan 40 (%s)', case when pass then 'PASA' else 'FALLA' end, n);
  select confirmed_quantity into n from public.pickup_items where pickup_id = b;
  total := total + 1; pass := n = 20;
  ok := ok + pass::int; rep := rep || format(E'\n%s saco B: declaró 100 con 0,5 kg, se pagan 20 por el peso (%s)', case when pass then 'PASA' else 'FALLA' end, n);
  select confirmed_quantity into n from public.pickup_items where pickup_id = c;
  total := total + 1; pass := n = 22;
  ok := ok + pass::int; rep := rep || format(E'\n%s saco C: lo contado a mano manda (%s)', case when pass then 'PASA' else 'FALLA' end, n);

  select generator_points_final into n from public.pickups where id = a;
  total := total + 1; pass := n = 440;   -- 40 x 10 pts, +10 % for 30+ units; cap = 2.1 kg share x 400 x 1.25 is higher
  ok := ok + pass::int; rep := rep || format(E'\n%s saco A paga 40 x 10 pts + 10 %% por juntar (%s)', case when pass then 'PASA' else 'FALLA' end, n);

  select center_weight_kg into n from ruma_analytics.pickup_count_samples where pickup_id = c and material_id = pet;
  total := total + 1; pass := n = 0.6;
  ok := ok + pass::int; rep := rep || format(E'\n%s la muestra de entrenamiento guarda el peso del saco (%s)', case when pass then 'PASA' else 'FALLA' end, n);

  raise exception 'RESULTADO: %/% PASA (todo revertido)%', ok, total, rep;
end $$;
