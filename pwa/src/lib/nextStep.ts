/**
 * The home's "siguiente mejor acción": the one thing worth doing now, read from what the family
 * already has on screen. Twin of `NextStep.kt` on Android; keep both in step (scripts/check-next.ts).
 *
 * Order matters: what is happening now (a rider in flight), then what was left half done (a scan
 * with no pickup), then money that can be collected, then progress. No urgency and no guilt: every
 * line says what the step gets you, never what you lose.
 */
export type NextKind = "live" | "pickup" | "cash" | "start-week" | "near-goal" | "near-cash" | "register";
export type NextStep = { kind: NextKind; title: string; body: string; action: string; to: string };

export type NextInput = {
  /** Title/line of the pickup in flight, as the home already builds them; null with none. */
  live?: { title: string; sub: string; to: string } | null;
  /** Scans with no pickup yet (the saved pile). */
  unpickedScan?: boolean;
  /** What the saved pile holds, "7 botellas PET guardadas"; null when unknown. */
  saved?: string | null;
  balance?: number;
  /** min_points of the cheapest active cash option (Yape/Plin); null when unknown. */
  cashMin?: number | null;
  /** Days with a scan this week and days left to the goal; null when the week did not load. */
  weekDays?: number | null;
  weekLeft?: number | null;
};

/** Points worth showing "ya casi cobras": from half of the minimum up. */
const NEAR_CASH_SHARE = 0.5;
const soles = (pts: number) => `S/${(pts / 1000).toFixed(2)}`;
const int = (n: number) => n.toLocaleString("en-US");
const CAMERA = "/f/camara";

export function nextStep({ live, unpickedScan, saved, balance = 0, cashMin, weekDays, weekLeft }: NextInput): NextStep {
  if (live) return { kind: "live", title: live.title, body: live.sub, action: "Ver mi recojo", to: live.to };
  if (unpickedScan) return { kind: "pickup", title: saved ? `Tienes ${saved}` : "Pide tu recojo", body: "Puedes seguir juntando: cada escaneo se suma. Cuando quieras, pide un solo recojo para todo.", action: "Pedir recojo", to: "/f/recojo" };
  if (cashMin && cashMin > 0 && balance >= cashMin) return { kind: "cash", title: "Ya puedes cobrar", body: `Tienes ${soles(balance)} disponibles para canjear por Yape o Plin.`, action: "Ver mi wallet", to: "/f/wallet" };
  if (weekDays === 0) return { kind: "start-week", title: "Registra tus residuos", body: "Una foto de lo que ya juntaste y tu semana arranca.", action: "Registrar residuos", to: CAMERA };
  if (weekLeft === 1) return { kind: "near-goal", title: "Te falta 1 día para tu meta", body: "Registra lo que juntaste hoy y completas tu semana.", action: "Registrar residuos", to: CAMERA };
  if (cashMin && cashMin > 0 && balance >= cashMin * NEAR_CASH_SHARE) return { kind: "near-cash", title: "Tu primer cobro está cerca", body: `Te faltan ${int(cashMin - balance)} pts para cobrar ${soles(cashMin)}.`, action: "Registrar residuos", to: CAMERA };
  return {
    kind: "register", title: "Registra lo de hoy",
    body: weekLeft === 0 ? "Ya cumpliste tu meta de la semana. Si juntaste más, súmalo cuando quieras." : "Hoy puedes registrar los residuos que ya tienes.",
    action: "Registrar residuos", to: CAMERA,
  };
}
