// "Invita y gana" helpers with no React in them (node scripts/check-invite.ts runs these).
// Twin of the helpers in GeneratorInviteScreen.kt: the database decides every payout, this only words it.

const REF_KEY = "ruma.ref";
const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

/** What a person may type or a link may carry, as the database stores codes: 6 letters or digits, upper case. */
export const cleanCode = (v: string | null | undefined) => (v ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 6);

/** A `?ref=CODIGO` link is remembered until the person registers, even if they browse the onboarding first. */
export function captureRef(search: string) {
  const code = cleanCode(new URLSearchParams(search).get("ref"));
  try { if (code.length === 6) localStorage.setItem(REF_KEY, code); } catch { /* private mode */ }
}
export const savedRef = () => { try { return localStorage.getItem(REF_KEY) ?? ""; } catch { return ""; } };
export const clearRef = () => { try { localStorage.removeItem(REF_KEY); } catch { /* private mode */ } };

export const inviteDue = (lastShownMs: number, nowMs: number) => nowMs - lastShownMs >= WEEK_MS;

/** True (and remembers today) when the home card may show now: at most once a week per account. */
export function claimInvitePrompt(userId: string | null, nowMs = Date.now()) {
  const key = `ruma.invite.${userId ?? "anon"}`;
  try {
    if (!inviteDue(Number(localStorage.getItem(key) ?? 0), nowMs)) return false;
    localStorage.setItem(key, String(nowMs));
    return true;
  } catch { return false; }
}

const pts = (n: number) => Math.round(n).toLocaleString("en-US");

export const inviteLink = (code: string, staging: boolean) =>
  `${staging ? "https://ruma-staging.vercel.app" : "https://ruma-recicla.vercel.app"}/?ref=${code}`;

export const inviteMessage = (code: string, welcomePoints: number, staging: boolean) =>
  `¡Hola! Reciclo con Ruma: un Eco-Rider pasa por mi casa a recoger las botellas y el cartón, y gano puntos. ` +
  `Regístrate con mi código ${code} y te regalan ${pts(welcomePoints)} pts de bienvenida cuando pesen tu primer reciclaje. ${inviteLink(code, staging)}`;

export type Invite = { name?: string | null; status: string; reason?: string | null; points?: number | null };

/** The chip and the line of one invitation, in points only. */
export function inviteLine(i: Invite, cap: number): [chip: string, line: string] {
  if (i.status === "pagado") return ["Pagado", `¡Te pagamos ${pts(i.points ?? 0)} pts!`];
  if (i.status === "rechazado") return ["No calificó", {
    mismo_telefono: "Usa el mismo teléfono que tú",
    misma_direccion: "Vive en tu misma dirección",
    rider_vinculado: "Su recojo lo hizo una cuenta vinculada",
  }[i.reason ?? ""] ?? "No cumplió las reglas del programa"];
  if (i.reason === "tope_mensual") return ["El próximo mes", `Llegaste a ${cap} este mes: se paga en su siguiente pesaje`];
  return ["Pendiente", "Esperando su primer recojo"];
}
