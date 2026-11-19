-- Checks supabase/sql/2026-11-18_celular_y_ubicacion.sql in STAGING with a real (non-test) family account.
-- Everything is rolled back by the final raise: no pickup or phone survives. Run:
--   npx supabase db query --remote=mbbdrmicjobydprvbpsj --agent no -f supabase/tests/celular_ubicacion_staging.sql --yes
-- Expect "RESULTADO: 3/3 PASA".
do $$
declare
  fam uuid := (select p.id from profiles p join auth.users u on u.id = p.id
                where p.role = 'generador' and u.email not like '%@ruma.test' order by p.created_at limit 1);
  ok int := 0; rep text := ''; msg text; pk uuid;
begin
  update profiles set phone = null where id = fam;
  perform set_config('request.jwt.claims', json_build_object('sub', fam, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  begin
    perform public.create_pickup_from_detection(null, current_date, 'Prueba', -7.88, -79.22);
    rep := rep || E'\nFALLA sin celular se creó el recojo';
  exception when others then
    msg := sqlerrm;
    if msg like 'Agrega tu número de celular%' then ok := ok + 1; rep := rep || E'\nPASA sin celular: ' || msg;
    else rep := rep || E'\nFALLA sin celular: ' || msg; end if;
  end;
  execute 'reset role';
  update profiles set phone = '+51987654321' where id = fam;
  execute 'set local role authenticated';
  begin
    perform public.create_pickup_from_detection(null, current_date, 'Prueba', null, null);
    rep := rep || E'\nFALLA sin ubicación se creó el recojo';
  exception when others then
    msg := sqlerrm;
    if msg like 'Activa tu ubicación%' then ok := ok + 1; rep := rep || E'\nPASA sin ubicación: ' || msg;
    else rep := rep || E'\nFALLA sin ubicación: ' || msg; end if;
  end;
  pk := public.create_pickup_from_detection(null, current_date, 'Prueba', -7.88, -79.22);
  if pk is not null then ok := ok + 1; rep := rep || E'\nPASA con celular y GPS se crea'; else rep := rep || E'\nFALLA con celular y GPS'; end if;
  raise exception 'RESULTADO: %/3 PASA (todo revertido)%', ok, rep;
end $$;
