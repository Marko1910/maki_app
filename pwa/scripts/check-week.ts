// Run: node scripts/check-week.ts
import assert from "node:assert/strict";
import { weekProgress } from "../src/lib/week.ts";

// Lima is UTC-5. Wednesday 2026-11-04 noon in Lima; that week runs Mon 11-02 .. Sun 11-08.
const now = new Date("2026-11-04T17:00:00Z");
const at = (iso: string) => new Date(iso);

assert.deepEqual(weekProgress([], 3, now), { days: 0, goal: 3, left: 3, streak: 0 });
// Two scans the same Lima day count once.
assert.equal(weekProgress([at("2026-11-02T15:00:00Z"), at("2026-11-02T22:00:00Z")], 3, now).days, 1);
// 03:00 UTC on the 3rd is still the 2nd in Lima: same day.
assert.equal(weekProgress([at("2026-11-02T15:00:00Z"), at("2026-11-03T03:00:00Z")], 3, now).days, 1);
// Sunday night in Lima belongs to the previous week.
assert.equal(weekProgress([at("2026-11-02T04:30:00Z")], 1, now).days, 0, "Sun 11-01 23:30 Lima is last week");

const lastTwoWeeks = ["2026-10-19", "2026-10-20", "2026-10-21", "2026-10-26", "2026-10-27", "2026-10-28"].map((d) => at(`${d}T15:00:00Z`));
// Current week not met yet: the streak keeps the past weeks, it is not broken mid-week.
assert.equal(weekProgress([...lastTwoWeeks, at("2026-11-02T15:00:00Z")], 3, now).streak, 2);
// Current week met: it joins the streak.
const met = [...lastTwoWeeks, ...["2026-11-02", "2026-11-03", "2026-11-04"].map((d) => at(`${d}T15:00:00Z`))];
assert.deepEqual(weekProgress(met, 3, now), { days: 3, goal: 3, left: 0, streak: 3 });
// A missed week ends the streak.
assert.equal(weekProgress([at("2026-10-19T15:00:00Z"), at("2026-10-20T15:00:00Z"), at("2026-10-21T15:00:00Z")], 3, now).streak, 0);
console.log("week ok");

// Tu ruma: stages by days with a scan, and the Lima month boundary.
{
  const { rumaLevel, limaMonthStart, monthDays } = await import("../src/lib/week.ts");
  assert.deepEqual(rumaLevel(0), { stage: 1, next: 2, progress: 0 });
  assert.deepEqual(rumaLevel(3), { stage: 2, next: 4, progress: 0.5 });
  assert.equal(rumaLevel(7).stage, 4);
  assert.deepEqual(rumaLevel(8), { stage: 5, next: null, progress: 1 });
  assert.deepEqual(rumaLevel(31), { stage: 5, next: null, progress: 1 });
  // 2026-12-01 03:00 UTC is still 30 Nov in Lima: November's pile.
  assert.equal(limaMonthStart(new Date("2026-12-01T03:00:00Z")).toISOString(), "2026-11-01T05:00:00.000Z");
  assert.equal(limaMonthStart(new Date("2026-12-01T06:00:00Z")).toISOString(), "2026-12-01T05:00:00.000Z");
  // Two scans on 3 Nov count once; 1 Nov 03:00 UTC is still 31 Oct in Lima.
  const nov = new Date("2026-11-20T17:00:00Z");
  assert.equal(monthDays([new Date("2026-11-03T15:00:00Z"), new Date("2026-11-03T22:00:00Z"), new Date("2026-11-10T15:00:00Z"), new Date("2026-11-01T03:00:00Z")], nov), 2);
  console.log("ruma ok");
}
