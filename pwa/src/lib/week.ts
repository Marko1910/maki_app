// Weekly goal for the home ring: days with a scan the server saved, counted once per Lima date.
// Participation only: it never pays, and the server's own current_streak_days is left alone.

const LIMA = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Lima", year: "numeric", month: "2-digit", day: "2-digit" });

/** Days since 1970-01-01 of the Lima calendar date of [at]. */
export const limaDay = (at: Date) => {
  const [y, m, d] = LIMA.format(at).split("-").map(Number);
  return Math.floor(Date.UTC(y, m - 1, d) / 86400000);
};
/** Monday-based week number (1970-01-01 was a Thursday). */
const weekOf = (day: number) => Math.floor((day + 3) / 7);

export type WeekProgress = { days: number; goal: number; left: number; streak: number };

/** [dates]: when each saved scan happened. [streak] = consecutive weeks that met the goal, the current one only once met. */
export function weekProgress(dates: Date[], goal: number, now = new Date()): WeekProgress {
  const byWeek = new Map<number, Set<number>>();
  for (const at of dates) {
    const day = limaDay(at);
    if (!byWeek.has(weekOf(day))) byWeek.set(weekOf(day), new Set());
    byWeek.get(weekOf(day))!.add(day);
  }
  const current = weekOf(limaDay(now));
  const met = (w: number) => (byWeek.get(w)?.size ?? 0) >= goal;
  const days = byWeek.get(current)?.size ?? 0;
  let streak = met(current) ? 1 : 0;
  for (let w = current - 1; met(w); w--) streak++;
  return { days, goal, left: Math.max(0, goal - days), streak };
}

export const GOAL_DAYS = [1, 2, 3, 4, 5, 6, 7];
const goalKey = (user: string | null) => `ruma.goal.${user ?? "anon"}`;
/** One day a week by default: a low bar keeps more streaks alive (docs/retencion-incentivos-research.md, rec. 1). */
export const DEFAULT_GOAL = 1;
export const readGoal = (user: string | null) => {
  try { const n = Number(localStorage.getItem(goalKey(user))); return GOAL_DAYS.includes(n) ? n : DEFAULT_GOAL; } catch { return DEFAULT_GOAL; }
};
export const writeGoal = (user: string | null, n: number) => { try { localStorage.setItem(goalKey(user), String(n)); } catch { /* private mode */ } };

/* ---------- Tu ruma: the month's pile, drawn as one of five 3D renders ---------- */
// It grows with constancy (days with a scan), never with volume: counting containers would reward
// producing more waste. docs/retencion-incentivos-research.md, recommendation 4.

/** Days of the month with a scan needed for each stage; the last one is the strapped bale (about two a week). */
export const RUMA_STEPS = [0, 2, 4, 6, 8];

/** 00:00 in Lima on the first day of [now]'s Lima month. */
export const limaMonthStart = (now = new Date()) => new Date(`${LIMA.format(now).slice(0, 7)}-01T05:00:00Z`);

export type RumaLevel = { stage: number; next: number | null; progress: number };

/** Stage 1..5 for [days] with a scan this month, the days for the next stage, and the fraction of the way there. */
export function rumaLevel(days: number): RumaLevel {
  const i = RUMA_STEPS.reduce((at, t, k) => (days >= t ? k : at), 0);
  const next = RUMA_STEPS[i + 1] ?? null;
  const progress = next == null ? 1 : (days - RUMA_STEPS[i]) / (next - RUMA_STEPS[i]);
  return { stage: i + 1, next, progress };
}

/** Distinct Lima dates in [now]'s Lima month with at least one saved scan. */
export function monthDays(dates: Date[], now = new Date()): number {
  const month = LIMA.format(now).slice(0, 7);
  return new Set(dates.filter((at) => LIMA.format(at).slice(0, 7) === month).map((at) => limaDay(at))).size;
}
