import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import test from 'node:test';

// npm install --no-save @electric-sql/pglite (or PGLITE_MODULE=/absolute/path/dist/index.js).
const { PGlite } = await import(process.env.PGLITE_MODULE
  ? pathToFileURL(process.env.PGLITE_MODULE).href : '@electric-sql/pglite');
const migration = readFileSync(new URL('../sql/2026-11-03_vision_measurement.sql', import.meta.url), 'utf8');
const oldMigration = readFileSync(new URL('../sql/2026-10-19_cadena_entregas.sql', import.meta.url), 'utf8');
const oldFunction = oldMigration.slice(oldMigration.indexOf('create or replace function public.commit_detection('),
  oldMigration.indexOf('$function$;') + '$function$;'.length);
const id = (n) => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`;
const source = { version: 2, attribution: 'single', sources: [{ provider: 'fallback', model: 'model-b', pipeline: 'v1' }],
  signed_observations: 1, legacy_observations: 0 };

test('migration, atomic provenance, immutable completion samples, coverage and permissions', async () => {
  const db = new PGlite();
  try {
    // Minimal contract fixture: this does not pretend to replace staging's RLS/business triggers.
    await db.exec(`
      create role anon; create role authenticated; create role service_role bypassrls;
      create schema auth;
      create table auth.users(id uuid primary key, email text);
      create table public.materials(id uuid primary key, code text, name text);
      create table public.detections(id uuid primary key default gen_random_uuid(), generator_id uuid,
        session_id text, status text, model_version text, streak_multiplier numeric, total_points integer, total_value numeric);
      create unique index detections_session_id_unique on public.detections(session_id) where session_id is not null;
      create table public.detection_items(id uuid default gen_random_uuid(), detection_id uuid, material_id uuid,
        quantity integer, confidence numeric, quality_score numeric, points integer, brands jsonb);
      create table public.pickups(id uuid primary key, detection_id uuid, generator_id uuid, status text);
      create table public.pickup_items(pickup_id uuid, material_id uuid, reported_quantity integer, confirmed_quantity integer,
        primary key(pickup_id,material_id));
      insert into auth.users values ('${id(1)}','family@maki.test'), ('${id(2)}','family@example.org');
      insert into materials values ('${id(10)}','PET','Plástico'),('${id(11)}','ALU','Aluminio'),('${id(12)}','VID','Vidrio');
      ${oldFunction}
    `);
    await db.exec(migration);
    const commit = async (session, provenance, quantity = 3) => (await db.query(
      `select public.commit_detection($1,$2,$3,1,1200,1.2,$4::jsonb,$5::jsonb) as result`,
      [id(1), session, 'model-b', JSON.stringify([{ material_id: id(10), quantity, quality_score: 1, points: 1200 }]), JSON.stringify(provenance)]
    )).rows[0].result;
    const first = await commit('session-a', source);
    const retry = await commit('session-a', { version: 2, attribution: 'mixed' }, 99);
    assert.equal(retry.id, first.id);
    assert.equal(retry.items[0].quantity, 3);
    assert.deepEqual((await db.query('select provenance from detections where id=$1', [first.id])).rows[0].provenance, source);
    // Old seven-argument calls remain valid and have unknown provenance.
    await db.query(`select public.commit_detection($1,'legacy','old-model',1,0,0,$2::jsonb)`,
      [id(1), JSON.stringify([{ material_id: id(10), quantity: 1 }])]);
    assert.deepEqual((await db.query("select provenance from detections where session_id='legacy'")).rows[0].provenance, {});
    // Failed inserts roll back the detection together with its provenance.
    await assert.rejects(commit('invalid-items', source, 'not-a-number'));
    assert.equal((await db.query("select count(*)::int as n from detections where session_id='invalid-items'")).rows[0].n, 0);

    await db.query(`insert into pickups values ($1,$2,$3,'collecting')`, [id(20), first.id, id(1)]);
    await db.query(`insert into detection_items(detection_id,material_id,quantity) values ($1,$2,5)`, [first.id, id(11)]);
    await db.exec(`insert into pickup_items values ('${id(20)}','${id(10)}',4,2),('${id(20)}','${id(12)}',1,1)`);
    await db.exec(`update pickups set status='completed' where id='${id(20)}'`);
    const samples = (await db.query('select * from ruma_analytics.count_samples order by material_id')).rows;
    assert.equal(samples.length, 3);
    assert.equal(Number(samples[0].ai_quantity), 3);
    assert.equal(Number(samples[0].reported_quantity), 4);
    assert.equal(Number(samples[0].rider_quantity), 2);
    assert.equal(samples[0].test_account, true);
    assert.equal(samples[0].attribution, 'single');
    assert.equal(samples[1].rider_quantity, null);
    assert.equal(samples[1].comparable, false);
    assert.equal(samples[2].ai_quantity, null);
    assert.equal(samples[2].comparable, false);
    // The acopio may replace confirmed_quantity; the frozen rider reference survives.
    await db.exec(`update pickup_items set confirmed_quantity=42 where pickup_id='${id(20)}';
      update pickups set status='completed' where id='${id(20)}';
      update pickups set status='collecting' where id='${id(20)}';
      update pickups set status='completed' where id='${id(20)}';`);
    assert.equal(Number((await db.query(`select rider_quantity from ruma_analytics.count_samples where material_id='${id(10)}'`)).rows[0].rider_quantity), 2);
    const metric = (await db.query(`select * from ruma_analytics.model_count_metrics where material_id='${id(10)}'`)).rows[0];
    assert.equal(Number(metric.mean_absolute_error), 1);
    assert.equal(Number(metric.mean_signed_error), 1);
    assert.equal(Number(metric.exact_match_rate), 0);
    assert.equal(Number(metric.edited_declarations), 1);
    assert.equal(Number(metric.overcounts), 1);
    // A later material must not leak into the first completion's snapshot.
    await db.exec(`insert into materials values ('${id(13)}','PAP','Papel');
      insert into pickup_items values ('${id(20)}','${id(13)}',9,9);
      update pickups set status='collecting' where id='${id(20)}';
      update pickups set status='completed' where id='${id(20)}';`);
    assert.equal((await db.query(`select count(*)::int as n from ruma_analytics.count_samples where pickup_id='${id(20)}'`)).rows[0].n, 3);

    // Zero is a valid confirmation, NULL is not. Manual/no-scan pickups stay unknown.
    await db.exec(`insert into pickups values ('${id(21)}',null,'${id(2)}','collecting');
      insert into pickup_items values ('${id(21)}','${id(10)}',2,0);
      update pickups set status='completed' where id='${id(21)}';`);
    const manual = (await db.query(`select * from ruma_analytics.count_samples where pickup_id='${id(21)}'`)).rows[0];
    assert.equal(manual.attribution, 'unknown');
    assert.equal(manual.test_account, false);
    assert.equal(Number(manual.rider_quantity), 0);
    assert.equal(manual.comparable, false);

    // A later guard failure rolls back a BEFORE-trigger snapshot too.
    await db.exec(`create function reject_completion() returns trigger language plpgsql as $$
      begin raise exception 'blocked'; end $$;
      create trigger z_guard before update of status on pickups for each row execute function reject_completion();
      insert into pickups values ('${id(22)}',null,'${id(2)}','collecting');
      insert into pickup_items values ('${id(22)}','${id(10)}',1,1);`);
    await assert.rejects(db.exec(`update pickups set status='completed' where id='${id(22)}'`), /blocked/);
    assert.equal((await db.query(`select count(*)::int as n from ruma_analytics.count_samples where pickup_id='${id(22)}'`)).rows[0].n, 0);
    for (const role of ['anon', 'authenticated']) {
      await db.exec(`set role ${role}`);
      await assert.rejects(db.query('select * from ruma_analytics.model_count_metrics'), /permission denied/);
      await assert.rejects(db.query(`select public.commit_detection('${id(1)}','attack','fake',1,0,0,'[]')`), /permission denied/);
      await db.exec('reset role');
    }
    await db.exec('set role service_role');
    assert.ok((await db.query('select * from ruma_analytics.model_count_metrics')).rows.length > 0);
    await assert.rejects(db.exec('delete from ruma_analytics.pickup_count_samples'), /permission denied/);
    await db.exec('reset role');
  } finally { await db.close(); }
});
