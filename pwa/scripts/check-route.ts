// Run: node scripts/check-route.ts — same cases as RouteOptimizerTest on Android.
import assert from "node:assert/strict";
import { bestOrder, cost, dueMinutes, windowEndMinute } from "../src/lib/routeOptimizer.ts";

// Rider at 0; A B C on a line, 10 min apart.
const line = [[0, 10, 20, 30], [10, 0, 10, 20], [20, 10, 0, 10], [30, 20, 10, 0]];
assert.deepEqual(bestOrder(line, [{}, {}, {}]), [0, 1, 2]);
assert.equal(bestOrder(line, [{}, {}, { dueMin: 15 }])[0], 2);              // closing window first
assert.deepEqual(bestOrder(line, [{}, {}, { dueMin: 35 }]), [0, 2, 1]);     // the cheapest compromise
const triangle = [[0, 10, 10], [10, 0, 10], [10, 10, 0]];
assert.deepEqual(bestOrder(triangle, [{}, {}]), [0, 1]);
assert.deepEqual(bestOrder(triangle, [{}, { priority: 1 }]), [1, 0]);       // Ruma Plus first, no detour

const n = 12;
const pts = Array.from({ length: n }, (_, i) => [(i * 1.7) % 7, (i * 3.1) % 5]);
const m = Array.from({ length: n + 1 }, (_, i) => Array.from({ length: n + 1 }, (_, j) => {
  const a = i ? pts[i - 1] : [0, 0], b = j ? pts[j - 1] : [0, 0];
  return Math.hypot(a[0] - b[0], a[1] - b[1]) * 3;
}));
const stops = Array.from({ length: n }, () => ({}));
const order = bestOrder(m, stops);
assert.deepEqual([...order].sort((a, b) => a - b), Array.from({ length: n }, (_, i) => i));
assert.ok(cost(m, stops, order) <= cost(m, stops, Array.from({ length: n }, (_, i) => i)));

assert.equal(windowEndMinute("Hoy 3:00 - 6:00 pm"), 18 * 60);
assert.equal(windowEndMinute("Mañana 9:00 - 12:00 m"), 12 * 60);
assert.equal(windowEndMinute("Cuando puedas"), null);
assert.equal(dueMinutes("Mañana 9:00 - 12:00 m", "2026-11-18", "2026-11-18", 600), 120);
assert.equal(dueMinutes("Mañana 9:00 - 12:00 m", "2026-12-01", "2026-11-30", 600), 1440 + 120);
console.log("check-route: ok");
