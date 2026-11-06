import test from 'node:test';
import assert from 'node:assert/strict';
import { NavigationRoutePolicy, routeProgress, parseRoad, instruction, streetRoute, streetTrip } from '../src/lib/navigation.ts';

test('stationary retry, no duplicate pending request, stale response invalidation', () => {
  const gate = new NavigationRoutePolicy();
  const a = gate.begin('a', 0, false);
  assert.equal(gate.begin('a', 1, false, true), null);
  assert.equal(gate.complete(a, 100, false), true);
  assert.equal(gate.begin('a', 3099, false), null);
  const retry = gate.begin('a', 3100, false);
  assert.notEqual(retry, null);
  const b = gate.begin('b', 3200, false);
  assert.equal(gate.complete(retry, 3300, true), false);
  assert.equal(gate.complete(b, 3400, true), true);
  gate.reset(); assert.notEqual(gate.begin('b', 3500, false), null);
});

test('successful routes refresh sparingly; failed refresh retries promptly', () => {
  const gate = new NavigationRoutePolicy();
  gate.complete(gate.begin('a', 0, false), 0, true);
  assert.equal(gate.begin('a', 5000, true), null);
  assert.equal(gate.begin('a', 30000, false), null);
  gate.complete(gate.begin('a', 30000, true), 30100, false);
  assert.notEqual(gate.begin('a', 33100, false), null);
});

test('segment projection and off-route distance', () => {
  const path = [[0, 0], [0, 0.02]];
  const p = routeProgress({ lat: 0, lng: 0.01 }, path);
  assert.equal(p.offRouteKm, 0);
  assert.ok(Math.abs(p.travelledKm / p.totalKm - 0.5) < 1e-5);
  assert.ok(routeProgress({ lat: 0.001, lng: 0.01 }, path).offRouteKm > 0.1);
  assert.equal(routeProgress({ lat: 0, lng: 0 }, []), null);
});

const fixture = { code: 'Ok', routes: [{ distance: 200, duration: 40, geometry: { coordinates: [[-77.04, -12.05], [-77.041, -12.051]] }, legs: [{ steps: [
  { distance: 100, name: 'Calle A', maneuver: { type: 'depart', location: [-77.04, -12.05] } },
  { distance: 100, name: 'Calle B', maneuver: { type: 'turn', modifier: 'right', location: [-77.041, -12.051] } },
] }] }] };
test('OSRM geometry and maneuvers retain coordinates and distance offsets', () => {
  const route = parseRoad(fixture);
  assert.deepEqual(route.path[0], [-12.05, -77.04]);
  assert.equal(route.steps[1].atKm, 0.1);
  assert.equal(route.steps[1].instruction, 'Gira a la derecha por Calle B');
  assert.equal(parseRoad({ code: 'NoRoute' }), null);
  assert.equal(parseRoad({ code: 'Ok', routes: [{ ...fixture.routes[0], geometry: { coordinates: [] } }] }), null);
  assert.equal(instruction({ maneuver: { type: 'roundabout', exit: 3 } }), 'En la rotonda, toma la salida 3');
});

test('request sends steps, accepts a single-stop trip and respects abort', async () => {
  const original = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async (url) => { calls++; assert.match(url, /steps=true/); return Response.json(fixture); };
  try {
    const start = { lat: -12.05, lng: -77.04 }, dest = { lat: -12.051, lng: -77.041 };
    assert.deepEqual((await streetTrip(start, [dest])).order, [0]);
    const c = new AbortController(); c.abort();
    assert.equal(await streetRoute([start, dest], null, c.signal), null);
    assert.equal(calls, 1);
  } finally { globalThis.fetch = original; }
});
