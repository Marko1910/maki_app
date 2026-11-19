/**
 * The rider's best visit order on street times (OSRM `table`), weighing each family's window and Ruma Plus
 * priority, not only the drive. Twin of `RouteOptimizer.kt`; same cases in `scripts/check-route.ts` and
 * `RouteOptimizerTest`. Pure: no network, so the check runs it in node.
 */
export const LATE_WEIGHT = 3;      // a late minute weighs as three minutes of driving
export const PRIORITY_WEIGHT = 1;  // each minute a Ruma Plus family waits weighs as a minute of driving
export const SERVICE_MIN = 5;      // minutes at each door
export const EXACT_MAX = 8;        // 8! orders: instant

/** dueMin: minutes from now until its window closes (negative = already late); null = no deadline. */
export type Stop = { dueMin?: number | null; priority?: number };

/** Minutes of a plan. m: index 0 the rider, 1..n the stops (row = from). order: indexes into stops. */
export function cost(m: number[][], stops: Stop[], order: number[]) {
  let t = 0, at = 0, total = 0;
  for (const s of order) {
    const drive = m[at][s + 1];
    t += drive; total += drive;
    const due = stops[s].dueMin;
    if (due != null) total += LATE_WEIGHT * Math.max(0, t - due);
    if ((stops[s].priority ?? 0) > 0) total += PRIORITY_WEIGHT * t;
    t += SERVICE_MIN; at = s + 1;
  }
  return total;
}

export function bestOrder(m: number[][], stops: Stop[]): number[] {
  const n = stops.length;
  if (n <= 1) return stops.map((_, i) => i);
  if (n <= EXACT_MAX) return exact(m, stops);
  return improve(m, stops, nearestFirst(m, n));
}

function exact(m: number[][], stops: Stop[]) {
  const perm = stops.map((_, i) => i);
  let best = [...perm], bestCost = cost(m, stops, perm);
  // Heap's algorithm: every order once.
  const c = perm.map(() => 0);
  let i = 0;
  while (i < perm.length) {
    if (c[i] < i) {
      const j = i % 2 === 0 ? 0 : c[i];
      [perm[j], perm[i]] = [perm[i], perm[j]];
      const k = cost(m, stops, perm);
      if (k < bestCost - 1e-9) { bestCost = k; best = [...perm]; }
      c[i]++; i = 0;
    } else { c[i] = 0; i++; }
  }
  return best;
}

function nearestFirst(m: number[][], n: number) {
  const left = new Set(Array.from({ length: n }, (_, i) => i));
  const out: number[] = [];
  let at = 0;
  while (left.size) {
    let next = -1;
    for (const s of left) if (next < 0 || m[at][s + 1] < m[at][next + 1]) next = s;
    out.push(next); left.delete(next); at = next + 1;
  }
  return out;
}

// ponytail: first-improvement 2-opt + relocate, O(n³) per pass; fine for a tricycle's stops.
function improve(m: number[][], stops: Stop[], start: number[]) {
  let best = start, bestCost = cost(m, stops, best), changed = true, passes = 0;
  while (changed && passes++ < 50) {
    changed = false;
    for (let i = 0; i < best.length - 1; i++) for (let j = i + 1; j < best.length; j++) {
      const moveIJ = [...best]; moveIJ.splice(j, 0, moveIJ.splice(i, 1)[0]);
      const moveJI = [...best]; moveJI.splice(i, 0, moveJI.splice(j, 1)[0]);
      for (const cand of [[...best.slice(0, i), ...best.slice(i, j + 1).reverse(), ...best.slice(j + 1)], moveIJ, moveJI]) {
        const k = cost(m, stops, cand);
        if (k < bestCost - 1e-9) { best = cand; bestCost = k; changed = true; }
      }
    }
  }
  return best;
}

/** "3:00 - 6:00 pm" → 1080; "9:00 - 12:00 m" → 720 (m = mediodía). */
export function windowEndMinute(label?: string | null) {
  const m = /(\d{1,2}):(\d{2})\s*(am|pm|m)?\s*$/i.exec((label ?? "").trim());
  if (!m) return null;
  const h = Number(m[1]), suffix = (m[3] ?? "").toLowerCase();
  const hour = suffix === "pm" ? (h === 12 ? 12 : h + 12) : suffix === "am" ? (h === 12 ? 0 : h) : h;
  return hour * 60 + Number(m[2]);
}

/** Minutes from now until the window closes; nowDate "yyyy-mm-dd" and nowMinute are the local clock. */
export function dueMinutes(label: string | null | undefined, scheduledDate: string | null | undefined, nowDate: string, nowMinute: number) {
  const end = windowEndMinute(label);
  if (end == null) return null;
  const days = scheduledDate ? Math.round((Date.parse(scheduledDate.slice(0, 10)) - Date.parse(nowDate)) / 86400000) : 0;
  return Number.isFinite(days) ? days * 1440 + end - nowMinute : null;
}
