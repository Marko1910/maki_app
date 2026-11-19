import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Empty, Failure, Icon, Metric, NavRow, Skeleton, StepDots, TopBar, useToast } from "../../components/ui";
import { ACTIVE, createRedemption, humanize, loadDetections, loadGeneratorProfile, loadScanDates, loadLeaderboardEntries, loadMyAchievements, loadNotifications, loadOpenPeriod, loadPickups, loadRankingRewards, loadRedemptionOptions, loadRedemptions, loadTrackedPickup, myId, signOut, loadPrimaryAddress, loadSavedPile, type Row } from "../../lib/api";
import { ago, dayGroup, initials, int, kg, ptsToSoles, soles, STATUS, takeBalanceGain, useAsync, windowText } from "../../lib/util";
import { GOAL_DAYS, monthDays, readGoal, rumaLevel, weekProgress, writeGoal, type WeekProgress } from "../../lib/week";
import { nextStep, type NextStep } from "../../lib/nextStep";
import { tick } from "../../lib/flow";
import { useScan } from "./Scan";
import { pileCounts, pileSummary } from "../../lib/pile";
import { InvitePrompt } from "./Invite";

/* ---------- Home ---------- */

export function Home() {
  const nav = useNavigate();
  const me = myId();
  const [goal] = useState(() => readGoal(me));
  const { data, loading, error, reload } = useAsync(async () => {
    const [profile, notes, pickup, dates, options, pile] = await Promise.all([
      loadGeneratorProfile(), loadNotifications().catch(() => []), loadTrackedPickup().catch(() => null),
      // A failed read must not show as zero progress: null renders an honest "no pudimos".
      loadScanDates(new Date(Date.now() - WEEKS_BACK * 7 * 86400000)).catch(() => null),
      loadRedemptionOptions().catch(() => [] as Row[]),
      loadSavedPile().catch(() => [] as Row[]),
    ]);
    return { profile, notes, pickup, dates, options, pile };
  });
  const { scan } = useScan();
  // A pickup just asked for arrives here as router state (Pickup.tsx): a short "va en camino".
  const location = useLocation();
  const [dispatch, setDispatch] = useState<Dispatch | null>(() => (location.state as { dispatch?: Dispatch } | null)?.dispatch ?? null);
  useEffect(() => {
    if (!(location.state as { dispatch?: Dispatch } | null)?.dispatch) return;
    // Clear it from history so a reload or "atrás" does not celebrate twice.
    nav(location.pathname, { replace: true, state: null });
  }, [location.state, location.pathname, nav]);
  useEffect(() => {
    if (!dispatch) return;
    const t = window.setTimeout(() => setDispatch(null), CREDITED_MS);
    return () => window.clearTimeout(t);
  }, [dispatch]);
  // Weighed material lands in the balance while the app is closed: say so on the next open.
  const [gain, setGain] = useState(0);
  const balance: number | undefined = data?.profile?.points_balance;
  useEffect(() => {
    if (balance == null) return;
    const g = takeBalanceGain(me, balance);
    if (g > 0) setGain(g);
  }, [me, balance]);
  // A moment, not a fixture: it leaves on its own; tapping it goes to the wallet.
  useEffect(() => {
    if (!gain) return;
    const t = window.setTimeout(() => setGain(0), CREDITED_MS);
    return () => window.clearTimeout(t);
  }, [gain]);
  if (loading) return <main className="screen with-nav home"><Skeleton label="Cargando tu inicio…" blocks={[64, 56, 240, 108, 136, 52]} /></main>;
  if (error || !data) return <main className="screen with-nav"><Failure text={error ?? "No pudimos cargar tu inicio."} retry={() => void reload()} /></main>;

  const { profile: p, pickup, dates } = data;
  const name = (p?.profiles?.display_name ?? p?.profiles?.full_name)?.split(/\s+/).find((w: string) => !/^(fam\.?|familia)$/i.test(w));
  const live = pickup && ACTIVE.includes(pickup.status) ? pickup : null;
  const unread = data.notes.some((n) => !n.is_read);
  const week = dates ? weekProgress(dates, goal) : null;

  return (
    <main className="screen with-nav home">
      <header className="between">
        <div className="min0"><h1 className="t-title">{name ? `Hola, ${name}` : "Hola"}</h1><p className="muted">Cada acción cuenta.</p></div>
        <button className="icon-btn" aria-label={unread ? "Ver notificaciones sin leer" : "Ver notificaciones"} onClick={() => nav("/f/notificaciones")}><Icon name="bell" />{unread && <i className="dot" />}</button>
      </header>
      {gain > 0 && <button className="glass pad credited" onClick={() => nav("/f/wallet")} aria-label={`Se sumaron ${int(gain)} puntos a tu saldo. Ir a Mi Wallet`}>
        <span className="calm-check"><Icon name="check" size={16} /></span>
        <span className="min0"><span className="t-row" style={{ display: "block" }} role="status">¡+{int(gain)} pts ya están en tu saldo!</span><span className="muted t-small" style={{ display: "block" }}>Son {ptsToSoles(gain)} más para canjear. Toca para ver tu wallet.</span></span>
        <Icon name="chevron" size={18} />
        <i className="credited-timer" aria-hidden="true" />
      </button>}
      {dispatch && <button className="glass pad credited" onClick={() => nav("/f/seguimiento")} aria-label="Ver mi recojo">
        <span className="calm-check"><Icon name="bike" size={16} /></span>
        <span className="min0"><span className="t-row" style={{ display: "block" }} role="status">{!dispatch.rider ? "¡Recojo pedido!" : dispatch.later ? `¡${dispatch.rider} tomó tu recojo!` : `¡${dispatch.rider} va en camino!`}</span>
          <span className="muted t-small" style={{ display: "block" }}>{dispatch.rider ? `${dispatch.later ? `Pasará ${dispatch.later.toLowerCase()}. ` : dispatch.eta ? `Llega en ~${dispatch.eta} min. ` : ""}Ten listos tus materiales.` : "Buscamos al Eco-Rider más cercano. Te avisamos cuando lo tome."}</span></span>
        <Icon name="chevron" size={18} />
        <i className="credited-timer" aria-hidden="true" />
      </button>}
      <InvitePrompt joy={gain > 0 || week?.left === 0} />
      <NextStepCard step={nextStep({
        live: live ? { title: liveTitle(live), sub: live.status === "assigned" && live.window_label ? `Pasará ${String(live.window_label).toLowerCase()}` : live.eta_minutes ? `Llega en ~${live.eta_minutes} min` : "Toca para ver el seguimiento", to: `/f/seguimiento?id=${live.id}` } : null,
        unpickedScan: !!scan?.detectionId || data.pile.length > 0,
        saved: pileSummary(pileCounts(data.pile as never, scan as never), data.pile as never), balance: p?.points_balance ?? 0,
        cashMin: data.options.filter((o) => o.kind === "cash" && o.min_points > 0).reduce<number | null>((m, o) => (m == null ? o.min_points : Math.min(m, o.min_points)), null),
        weekDays: week?.days ?? null, weekLeft: week?.left ?? null,
      })} />
      {week ? <GoalRing week={week} /> : <p className="glass pad muted center">No pudimos cargar tu semana. <button className="btn-text" onClick={() => void reload()}>Reintentar</button></p>}
      <button className="glass pad stack" style={{ textAlign: "left" }} onClick={() => nav("/f/wallet")} aria-label="Ir a Mi Wallet">
        <span className="between"><span className="row t-row" style={{ gap: 10 }}><span className="accent"><Icon name="wallet" /></span>Mi Wallet</span><Icon name="chevron" size={18} /></span>
        <span className="between" style={{ alignItems: "baseline", flexWrap: "wrap", gap: 8 }}>
          <span className="t-card">{int(p?.points_balance)} puntos</span>
          <span className="accent"><b className="t-card">{ptsToSoles(p?.points_balance ?? 0)}</b> disponibles</span>
        </span>
        {!!p?.points_pending && <span className="muted t-small row" style={{ gap: 6 }}><i className="pending-dot" />+{int(p.points_pending)} pts por confirmar · llegan cuando el acopio pese tu material</span>}
      </button>
      {dates && <RumaCard days={monthDays(dates)} />}
      <div className="glass list">
        <NavRow icon="truck" title="Pedir recojo" sub="Toma una foto de tus residuos y un Eco-Rider pasa por ellos" onClick={() => nav("/f/camara")} />
        <NavRow icon="pin" title="Puntos de acopio" sub="Mira dónde reciclar y los precios" onClick={() => nav("/f/acopios")} />
        <NavRow icon="rumi" title="Pregúntale a Rumi" sub="Qué va en cada tacho y cuánto vale tu material" onClick={() => nav("/f/chat")} />
      </div>
    </main>
  );
}

const WEEKS_BACK = 26;
const CREDITED_MS = 7000;

type Dispatch = { rider?: string | null; eta?: number | null; later?: string | null };
const liveTitle = (live: Row) => !live.rider?.display_name ? STATUS[live.status] : live.status === "assigned" ? `${live.rider.display_name} tomó tu recojo` : `${live.rider.display_name} va en camino`;

/**
 * "Siguiente mejor acción": one card that says what is worth doing now and one button that does
 * it. It replaces the fixed "Registrar residuos" and the separate live-pickup row.
 */
/** "Tu siguiente paso" steps aside once used (it blocked the home's view) until a different step comes. */
const stepKey = (s: NextStep) => `${s.kind}|${s.title}`;
const usedStepKey = () => `ruma.step.${myId() ?? "anon"}`;
function readUsedStep() { try { return localStorage.getItem(usedStepKey()); } catch { return null; } }

function NextStepCard({ step }: { step: NextStep }) {
  const nav = useNavigate();
  const [used, setUsed] = useState(readUsedStep);
  if (used === stepKey(step)) return null;
  const go = () => {
    try { localStorage.setItem(usedStepKey(), stepKey(step)); } catch { /* private mode: it just shows again */ }
    setUsed(stepKey(step));
    nav(step.to);
  };
  const icon = step.kind === "live" || step.kind === "pickup" ? "bike" : step.kind === "cash" ? "wallet" : "camera";
  return (
    <section className="glass pad stack next-step" key={step.kind}>
      <div className="between"><p className="t-over accent">Tu siguiente paso</p>{step.kind === "live" && <span className="chip chip-live"><i />EN VIVO</span>}</div>
      <p className="t-card">{step.title}</p>
      {step.body && <p className="muted">{step.body}</p>}
      <button className="btn btn-primary" onClick={go}><Icon name={icon} /> {step.action}</button>
    </section>
  );
}

/** The month's pile as a 3D bale that grows with each day you recycle; strapped when full. */
function RumaCard({ days }: { days: number }) {
  const { stage, next, progress } = rumaLevel(days);
  const month = new Date().toLocaleDateString("es-PE", { month: "long", timeZone: "America/Lima" });
  const left = next == null ? 0 : next - days;
  return (
    <section className="glass pad ruma-card" aria-label={`Tu ruma de ${month}: ${days} días reciclando, nivel ${stage} de 5`}>
      <div className="ruma-art"><img src={`/ruma/ruma-${stage}.webp`} alt="" decoding="async" /></div>
      <div className="stack min0" style={{ gap: 6 }}>
        <p className="t-over accent">Tu ruma de {month}</p>
        <p className="t-card">{days} {days === 1 ? "día" : "días"} reciclando</p>
        {next != null ? <>
          <div className="track" aria-hidden="true"><i style={{ width: `${Math.round(progress * 100)}%` }} /></div>
          <p className="muted t-small">{days === 0 ? "Cada día que registras tus residuos, tu ruma crece." : `${left === 1 ? "Falta 1 día" : `Faltan ${left} días`} para el nivel ${stage + 1}`}</p>
        </> : <p className="muted t-small">¡Fardo completo! Tu ruma de este mes ya está amarrada.</p>}
      </div>
    </section>
  );
}

/** The arc is this week against the goal; the streak sits under the number, never as a second percentage. */
/** 3D renders of the ring (Blender), one per fraction days/goal reachable with goals of 1..7 days, in permille. */
const RING_FRAMES = [0, 143, 167, 200, 250, 286, 333, 400, 429, 500, 571, 600, 667, 714, 750, 800, 833, 857, 1000];
/**
 * Fills the 3D ring from empty up to [target]: while it runs it walks the dense 2.5 % render sequence
 * (ringa-*), then rests on the exact frame for the fraction. Straight to it with reduced motion.
 */
function useFillUp(target: number) {
  const [state, setState] = useState({ shown: 0, done: false });
  useEffect(() => {
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) { setState({ shown: target, done: true }); return; }
    // Decode every frame on the way first, so the fill never blinks.
    for (let f = 0; f <= Math.ceil(target * 40); f++) new Image().src = `/anim/ringa-${String(f * 25).padStart(4, "0")}.webp`;
    new Image().src = ringFrame(target);
    let raf = 0, start = 0;
    const tick = (t: number) => {
      if (!start) start = t;
      const k = Math.min(1, (t - start) / RING_FILL_MS);
      const eased = 1 - Math.pow(1 - k, 3);
      setState({ shown: target * eased, done: k >= 1 });
      if (k < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target]);
  return state;
}
const RING_FILL_MS = 1200;
/** The dense render nearest to [f], for the frames in between. */
const fillFrame = (f: number) => `/anim/ringa-${String(Math.round(Math.min(1, Math.max(0, f)) * 40) * 25).padStart(4, "0")}.webp`;

const ringFrame = (filled: number) => {
  const pm = RING_FRAMES.reduce((best, f) => (Math.abs(f - filled * 1000) < Math.abs(best - filled * 1000) ? f : best), 0);
  return `/anim/ring-${String(pm).padStart(4, "0")}.webp`;
};

function GoalRing({ week }: { week: WeekProgress }) {
  const filled = Math.min(1, week.days / week.goal);
  const { shown, done } = useFillUp(filled);
  const streak = week.streak ? `Racha: ${week.streak} ${week.streak === 1 ? "semana" : "semanas"}` : "";
  return (
    <section className="stack center" style={{ alignItems: "center" }} aria-label={`${week.days} de ${week.goal} días esta semana. ${streak}`}>
      <div className="goal-ring">
        <img className="goal-3d" src={done ? ringFrame(shown) : fillFrame(shown)} alt="" aria-hidden="true" />
        <div className="goal-center">
          <p><b className="t-hero">{week.days}</b> <span className="t-card muted">de {week.goal}</span></p>
          <p className="muted">días esta semana</p>
          {streak && <span className="chip chip-accent">{streak}</span>}
        </div>
      </div>
      <p className="muted">{week.left > 0 ? `Te falta ${week.left} ${week.left === 1 ? "día" : "días"} para completar tu meta.` : "¡Cumpliste tu meta de esta semana!"}</p>
    </section>
  );
}

/* ---------- Wallet ---------- */

/** Lima's calendar year (UTC-5, no DST): the arbitrios cap resets on it. */
const limaYear = (at?: string) => new Date(new Date(at ?? Date.now()).getTime() - 5 * 3600000).getUTCFullYear();
/** Arbitrios are a share of the yearly fee: their metadata, or null for any other option. */
const percentOf = (o: Row): Row | null => (o.metadata?.unit === "percent" && o.points_per_unit > 0 ? o.metadata : null);
/** What a redemption was worth: arbitrios in %, money in soles, nothing otherwise. */
const redeemAmount = (r: Row) => r.kind === "municipal_discount" || r.redemption_options?.code === "MUNI_ARBITRIOS" ? ` · ${Number(r.quantity ?? 0)} %` : r.monetary_value > 0 ? ` · ${soles(r.monetary_value)}` : "";

export function Wallet() {
  const toast = useToast();
  const nav = useNavigate();
  const { data, loading, error, reload } = useAsync(async () => {
    const [profile, options, history] = await Promise.all([loadGeneratorProfile(), loadRedemptionOptions(), loadRedemptions()]);
    return { profile, options, history };
  });
  const [busy, setBusy] = useState<string | null>(null);
  // What was just redeemed, for the "¿y ahora?" card on top.
  const [done, setDone] = useState<string | null>(null);
  if (loading) return <main className="screen"><TopBar title="Mi Wallet" /><Skeleton label="Cargando tu wallet…" blocks={[168, 20, 72, 72, 72]} /></main>;
  if (error || !data) return <main className="screen"><TopBar title="Mi Wallet" /><Failure text={error ?? "No pudimos cargar tu wallet."} retry={() => void reload()} /></main>;

  const balance: number = data.profile?.points_balance ?? 0;
  const pending: number = data.profile?.points_pending ?? 0;
  const value = (o: Row) => (o.kind === "cash" && o.points_per_unit > 0 ? (o.min_points / o.points_per_unit) * o.unit_value : 0);
  const usedPct = (o: Row, m: Row) => data.history
    .filter((h) => h.redemption_options?.code === o.code && !["cancelled", "failed"].includes(h.status) && limaYear(h.created_at) === limaYear())
    .reduce((n, h) => n + Number(h.quantity ?? 0), 0) * (m.percent_per_unit ?? 1);
  const next = [...data.options].filter((o) => o.min_points > balance).sort((a, b) => a.min_points - b.min_points)[0];
  const redeem = async (o: Row) => {
    if (!window.confirm(`¿Canjear ${int(o.min_points)} pts por ${o.name}?`)) return;
    setBusy(o.id);
    try {
      await createRedemption(o.id, o.kind, o.min_points, value(o));
      tick(); setDone(o.name); window.scrollTo({ top: 0, behavior: "smooth" });
      toast("¡Canje realizado con éxito!"); await reload(true);
    }
    catch (e) { toast(humanize(e)); } finally { setBusy(null); }
  };

  return (
    <main className="screen">
      <TopBar title="Mi Wallet" />
      {done && <section className="glass pad stack redeem-done" role="status">
        <p className="t-card">¡Canje hecho!</p>
        <p className="muted">“{done}” quedó registrado; lo ves abajo, en tu historial de canjes. ¿Y ahora? Cada recojo suma para el próximo.</p>
        <button className="btn btn-primary" onClick={() => nav("/f/camara")}><Icon name="camera" /> Registrar residuos</button>
        <button className="btn btn-secondary" onClick={() => nav("/f/inicio")}>Volver al inicio</button>
      </section>}
      <section className="hero">
        <p className="t-over">Saldo disponible</p>
        <div className="between"><p className="t-hero">{int(balance)}</p><p className="t-card">= {ptsToSoles(balance)}</p></div>
        <p className="soft">Eco-Puntos{data.profile?.points_pending ? ` · +${int(data.profile.points_pending)} por confirmar` : ""}</p>
        {next && <div className="hero-rule stack"><div className="track"><i style={{ width: `${Math.min(100, (balance / next.min_points) * 100)}%` }} /></div><p className="soft t-small">Faltan {int(next.min_points - balance)} pts para {next.name}</p></div>}
      </section>
      {pending > 0 && <section className="glass pad stack">
        <div className="between"><p className="t-over">Por confirmar</p><span className="chip chip-money">+{int(pending)} pts</span></div>
        <StepDots steps={[["Recogido", "en tu puerta"], ["Pesado", "en el acopio"], ["Disponible", "en tu saldo"]]} current={1} label="Tus puntos por confirmar: falta el pesaje en el acopio" />
        <p className="muted t-small">Pasan a tu saldo cuando el acopio pese el material; no tienes que hacer nada. El monto final puede ajustarse según el peso.</p>
      </section>}
      {balance === 0 && pending === 0 && <div className="glass list"><NavRow icon="camera" title="Empieza a sumar puntos" sub="Registra tus residuos y pide un recojo" onClick={() => nav("/f/camara")} /></div>}
      <p className="t-over">Opciones de canje</p>
      {data.options.length ? <div className="glass list">
        {data.options.map((o, i) => {
          const m = percentOf(o);
          const minPct = m ? (o.min_points / o.points_per_unit) * (m.percent_per_unit ?? 1) : 0;
          const used = m ? usedPct(o, m) : 0;
          const capped = !!m?.max_percent_year && used + minPct > m.max_percent_year;
          const can = balance >= o.min_points && !capped;
          return <div key={o.id}>
            <span className="badge"><Icon name={o.kind === "cash" ? "wallet" : "box"} /></span>
            <span className="min0"><span className="t-row" style={{ display: "block" }}>{o.name}</span>
              {m ? <>
                <span className="muted t-small" style={{ display: "block" }}>{minPct} % de tu arbitrio anual · {int(o.min_points)} pts{o.partner ? ` · ${o.partner}` : ""}</span>
                <span className="muted t-small" style={{ display: "block" }}>Hasta {m.max_percent_year} % al año · este año llevas {used} %</span>
              </> : <span className="muted t-small" style={{ display: "block" }}>{[`${int(o.min_points)} pts`, o.partner].filter(Boolean).join(" · ")}</span>}
              {i === 0 && can && <span className="chip chip-money">Sugerido</span>}</span>
            {can ? <button className={`btn btn-small ${i === 0 ? "btn-primary" : "btn-secondary"}`} disabled={busy === o.id} onClick={() => void redeem(o)}>{busy === o.id ? "Procesando…" : "Canjear"}</button>
              : capped ? <span className="muted t-small" style={{ flex: "none", textAlign: "right" }}>Ya usaste<br /><b>{used} % este año</b></span>
              : <span className="muted t-small" style={{ flex: "none", textAlign: "right" }}>Te faltan<br /><b>{int(o.min_points - balance)} pts</b></span>}
          </div>;
        })}
      </div> : <Empty title="Aún no hay opciones de canje" icon="wallet" />}
      <p className="t-over">Historial de canjes</p>
      {data.history.length ? <div className="glass list">
        {data.history.map((h, i) => <div key={i}>
          <span className="min0"><span className="t-row" style={{ display: "block" }}>{h.redemption_options?.name ?? h.kind}{redeemAmount(h)}</span><span className="muted t-small">{ago(h.created_at)}</span></span>
          <span className="money">− {int(h.points_spent)} pts</span>
        </div>)}
      </div> : <p className="muted">Aún no tienes canjes. Cuando hagas uno, aquí verás su fecha y su monto.</p>}
    </main>
  );
}

/* ---------- History ---------- */

/** Where a pickup's points stand, in the history row. A cancelled pickup earns nothing, so it shows none. */
function pickupPoints(p: Row) {
  if (p.generator_points_final != null) return `+${int(p.generator_points_final)} pts acreditados`;
  if (p.status === "completed" && p.generator_points_awarded) return `+${int(p.generator_points_awarded)} pts por confirmar`;
  return "";
}

export function FamilyHistory() {
  const nav = useNavigate();
  const [filter, setFilter] = useState("Todo");
  const { data, loading, error, reload } = useAsync(async () => {
    const [pickups, detections, redemptions] = await Promise.all([loadPickups(30), loadDetections(30), loadRedemptions()]);
    return [
      ...pickups.map((p) => ({ type: "Recojos", at: p.created_at as string, id: p.id as string, title: `Recojo · ${(p.pickup_items ?? []).map((i: Row) => `${i.confirmed_quantity ?? i.reported_quantity} ${i.materials?.code}`).join(" + ") || p.code}`, sub: (ACTIVE.includes(p.status) ? [windowText(p), p.rider?.display_name] : [p.rider?.display_name, pickupPoints(p)]).filter(Boolean).join(" · "), status: STATUS[p.status] ?? p.status, right: "", icon: "truck", live: ACTIVE.includes(p.status) })),
      ...detections.map((d) => ({ type: "Detecciones", at: d.detected_at as string, id: "", title: `Foto · ${(d.detection_items ?? []).map((i: Row) => `${i.materials?.code} x${i.quantity}`).join(" · ") || "Sin ítems"}`, sub: "Detección", status: "", right: `+${int(d.total_points)} pts estimados`, icon: "camera", live: false })),
      ...redemptions.map((r) => ({ type: "Canjes", at: r.created_at as string, id: "", title: `${r.redemption_options?.name ?? "Canje"}${redeemAmount(r)}`, sub: ago(r.created_at), status: "", right: `− ${int(r.points_spent)} pts`, icon: "wallet", live: false })),
    ].sort((a, b) => b.at.localeCompare(a.at));
  });
  const shown = (data ?? []).filter((r) => filter === "Todo" || r.type === filter);
  const groups = [...new Set(shown.map((r) => dayGroup(r.at)))];
  return (
    <main className="screen">
      <TopBar title="Mi Historial" />
      <div className="chips">{["Todo", "Recojos", "Detecciones", "Canjes"].map((f) => <button key={f} className="glass filter" aria-pressed={filter === f} onClick={() => setFilter(f)}>{f}</button>)}</div>
      {loading ? <Skeleton label="Cargando tu historial…" blocks={[20, 72, 72, 72]} /> : error ? <Failure text={error} retry={() => void reload()} /> : !shown.length ? (filter === "Canjes"
        ? <Empty title="Aún no tienes canjes" text="Cuando tengas saldo disponible podrás canjearlo desde tu wallet." icon="wallet" action={{ label: "Ver mi wallet", onClick: () => nav("/f/wallet") }} />
        : filter === "Recojos" ? <Empty title="Aún no tienes recojos" text="Pide uno y aquí verás su estado y sus puntos, paso a paso." icon="truck" action={{ label: "Pedir recojo", icon: "camera", onClick: () => nav("/f/camara") }} />
        : <Empty title="Nada por aquí todavía" text="Toma una foto de tus residuos: es el primer paso para ganar puntos." icon="history" action={{ label: "Registrar residuos", icon: "camera", onClick: () => nav("/f/camara") }} />) : groups.map((g) => (
        <section className="stack" key={g}>
          <p className="t-over">{g}</p>
          <div className="glass list">{shown.filter((r) => dayGroup(r.at) === g).map((r, i) => (
            <NavRow key={i} icon={r.icon} title={r.title} sub={r.sub} onClick={r.type === "Recojos" ? () => nav(`/f/seguimiento?id=${r.id}`) : undefined}
              right={<span style={{ textAlign: "right", flex: "none", maxWidth: "42%" }}>{r.status && <span className={`chip ${r.live ? "chip-accent" : ""}`} style={{ whiteSpace: "normal" }}>{r.status}</span>}{r.right && <span className="money t-small" style={{ display: "block" }}>{r.right}</span>}</span>} />
          ))}</div>
        </section>
      ))}
    </main>
  );
}

/* ---------- Collection centers ---------- */

export { Centers } from "./Centers";

/* ---------- Rankings: shared by both roles ---------- */

export function Leaderboard({ role, title }: { role: "generador" | "eco_rider"; title: string }) {
  const { data, loading, error, reload } = useAsync(async () => {
    const period = await loadOpenPeriod(role);
    if (!period) return null;
    const [entries, rewards] = await Promise.all([loadLeaderboardEntries(period.id), loadRankingRewards(period.scope, period.period_type).catch(() => [])]);
    return { period, entries, rewards };
  }, [role]);
  const [showRewards, setShowRewards] = useState(false);
  const nav = useNavigate();
  // The ranking ends on what moves it: the family's next scan. Riders move it from their route.
  const scan = role === "generador" ? { label: "Registrar residuos", icon: "camera", onClick: () => nav("/f/camara") } : undefined;
  const me = data?.entries.find((e) => e.user_id === myId());
  const leader = data?.entries[0];
  const MEDAL = ["linear-gradient(135deg,#f6d365,#e0a41b)", "linear-gradient(135deg,#e5e7eb,#9ca3af)", "linear-gradient(135deg,#e6b98f,#c58b5b)"];
  return (
    <main className="screen with-nav">
      <TopBar title={title} sub={data?.period.block_label ?? undefined} />
      {loading ? <Skeleton label="Cargando el ranking…" blocks={[140, 64, 64, 64, 64]} /> : error ? <Failure text={error} retry={() => void reload()} /> : !data ? <Empty title="Todavía no hay ranking abierto" text="Cuando arranque el periodo verás tu posición." icon="trophy" action={scan} /> : <>
        {me && <section className="hero">
          <p className="t-over">Tu posición</p>
          <div className="row"><p className="t-hero">#{me.rank}</p><p className="t-card">{me.display_name}</p></div>
          <div className="between hero-rule">
            {leader && leader.user_id !== me.user_id ? <span className="chip chip-hero">A {int(leader.points - me.points)} pts del 1° lugar</span> : <span className="chip chip-hero">Vas primero</span>}
            <span style={{ fontWeight: 800 }}>{int(me.points)} pts</span>
          </div>
        </section>}
        {data.entries.length ? <div className="glass list">
          {data.entries.map((e) => <div key={e.user_id} style={e.user_id === myId() ? { background: "var(--accent-tint)" } : undefined}>
            <span className="badge" style={{ borderRadius: "50%", width: 32, height: 32, background: MEDAL[e.rank - 1] }}>{e.rank}</span>
            <span className="badge" style={{ borderRadius: "50%" }}>{initials(e.display_name)}</span>
            <span className="min0"><span className="t-row" style={{ display: "block" }}>{e.display_name ?? "Vecino"} {e.user_id === myId() && <span className="chip chip-accent">Tú</span>}</span>{e.streak_days > 0 && <span className="muted t-small">Racha {e.streak_days} días</span>}</span>
            <span className="t-row">{int(e.points)} <span className="muted t-small">pts</span></span>
          </div>)}
        </div> : <Empty title="Aún no hay posiciones" icon="trophy" />}
        {data.rewards.length > 0 && <div className="glass list">
          <NavRow icon="trophy" title="Premios del ranking" onClick={() => setShowRewards(!showRewards)} />
          {showRewards && data.rewards.map((r) => <div key={r.rank}><span className="t-row">#{r.rank}</span><span className="min0 muted">{r.description ?? (r.reward_points ? `${int(r.reward_points)} pts` : `x${r.reward_multiplier}`)}</span></div>)}
        </div>}
        {scan && <>
          <p className="muted t-small center">Tu posición se mueve con cada recojo.</p>
          <button className="btn btn-secondary" onClick={scan.onClick}><Icon name="camera" /> {scan.label}</button>
        </>}
      </>}
    </main>
  );
}
export const Rankings = () => <Leaderboard role="generador" title="Ranking de tu cuadra" />;

/* ---------- Profile ---------- */

export function FamilyProfile() {
  const nav = useNavigate();
  const { data, loading, error, reload } = useAsync(async () => {
    const [p, address, badges] = await Promise.all([loadGeneratorProfile(), loadPrimaryAddress().catch(() => null), loadMyAchievements().catch(() => [])]);
    return { p, address, badges };
  });
  if (loading) return <main className="screen with-nav"><TopBar title="Mi perfil" back={false} /><Skeleton label="Cargando tu perfil…" blocks={[120, 20, 88, 20, 96, 64, 280]} /></main>;
  if (error || !data) return <main className="screen with-nav"><Failure text={error ?? "No pudimos cargar tu perfil."} retry={() => void reload()} /></main>;
  const { p, address, badges } = data;
  const name = p?.profiles?.display_name ?? p?.profiles?.full_name ?? "Mi perfil";
  return (
    <main className="screen with-nav">
      <TopBar title="Mi perfil" back={false} />
      <section className="hero">
        <div className="row">
          <span className="chip-hero chip" style={{ width: 56, height: 56, justifyContent: "center", fontSize: 20 }}>{initials(name)}</span>
          <div className="min0"><p className="t-card">{name}</p>{address && <p className="soft t-small">{address.address_line}</p>}</div>
        </div>
        {p?.profiles?.ruma_code && <div className="hero-rule"><p className="t-over">Tu código de saco</p><p className="t-hero" style={{ letterSpacing: 1 }}>{p.profiles.ruma_code}</p><p className="soft t-small">Escríbelo en tu saco con plumón. El acopio pesa y verifica tu saco por este código.</p></div>}
        <div className="hero-rule" style={{ display: "flex", justifyContent: "flex-end" }}><button className="chip chip-hero" style={{ minHeight: 48 }} onClick={() => nav("/f/editar")}><Icon name="edit" size={16} /> Editar perfil</button></div>
      </section>
      <p className="t-over">Tu cuenta en números</p>
      <div className="grid3"><Metric value={String(p?.current_streak_days ?? 0)} label="Racha" /><Metric value={int(p?.points_balance)} label="Eco-Puntos" /><Metric value={soles(p?.cash_earned_total)} label="Ganados" /></div>
      <p className="t-over">Tu impacto ambiental</p>
      <section className="glass pad stack">
        <div className="grid3 center">
          <div><p className="t-row">{kg(p?.co2_avoided_kg)}</p><p className="muted t-small">CO₂ evitado</p></div>
          <div><p className="t-row">{kg(p?.recycled_kg)}</p><p className="muted t-small">Reciclado</p></div>
          <div><p className="t-row">{int(p?.water_saved_l)} L</p><p className="muted t-small">Agua</p></div>
        </div>
        {(p?.trees_equivalent ?? 0) > 0 && <p className="muted t-small center">Equivalente a plantar {Number(p!.trees_equivalent).toFixed(1)} árboles</p>}
      </section>
      {badges.length > 0 && <>
        <p className="t-over">Logros</p>
        <div className="glass pad grid3 center">{badges.map((b, i) => <div className="stack" style={{ alignItems: "center" }} key={i}><span className="badge" style={{ borderRadius: "50%", width: 52, height: 52, ...(b.unlocked ? { border: "2px solid #e0a41b" } : { opacity: 0.55 }) }}><Icon name={b.unlocked ? "trophy" : "lock"} /></span><span className={`t-small ${b.unlocked ? "" : "muted"}`}>{b.achievements?.name}</span></div>)}</div>
      </>}
      <WeeklyGoalButton />
      <p className="t-over">Cuenta</p>
      <div className="glass list">
        <NavRow icon="wallet" title="Mi billetera" sub="Puntos, canjes y descuento de arbitrios" onClick={() => nav("/f/wallet")} />
        <NavRow icon="history" title="Mi historial" sub="Tus escaneos y recojos, uno por uno" onClick={() => nav("/f/historial")} />
        <NavRow icon="trophy" title="Rankings" sub="Cómo va tu barrio frente a los demás" onClick={() => nav("/f/ranking")} />
        <NavRow icon="send" title="Invita y gana" sub="Comparte tu código y ganen puntos los dos" onClick={() => nav("/f/invita")} />
        <NavRow icon="flash" title="Planes" sub="Ruma Plus: cobra desde S/ 1 y tu recojo primero" onClick={() => nav("/f/planes")} />
        <NavRow icon="bell" title="Notificaciones" sub="Avisos de tus recojos y de tus puntos" onClick={() => nav("/f/notificaciones")} />
        <NavRow icon="rumi" title="Pregúntale a Rumi" sub="Qué va en cada tacho y cuánto vale tu material" onClick={() => nav("/f/chat")} />
      </div>
      <button className="btn btn-danger" onClick={() => void signOut()}><Icon name="logout" /> Cerrar sesión</button>
    </main>
  );
}

/** The weekly goal, moved from the home: a button that opens the question and the seven days. */
function WeeklyGoalButton() {
  const me = myId();
  const [goal, setGoal] = useState(() => readGoal(me));
  const [open, setOpen] = useState(false);
  const toast = useToast();
  const choose = (n: number) => { writeGoal(me, n); setGoal(n); setOpen(false); tick(); toast(`Meta actualizada: ${n} ${n === 1 ? "día" : "días"} por semana.`); };
  return (
    <section className="glass list">
      <NavRow icon="trophy" title="Meta semanal" sub={`Participar ${goal} ${goal === 1 ? "día" : "días"} por semana`} onClick={() => setOpen(!open)} />
      {open && <div className="stack" style={{ flexDirection: "column", alignItems: "stretch" }}>
        <p className="muted t-small">¿Cuántos días por semana quieres registrar residuos?</p>
        <div className="goal-days" role="radiogroup" aria-label="Días por semana">{GOAL_DAYS.map((n) =>
          <button key={n} role="radio" aria-checked={goal === n} aria-label={`${n} ${n === 1 ? "día" : "días"}`} className="glass filter" aria-pressed={goal === n} onClick={() => choose(n)}>{n}</button>)}</div>
      </div>}
    </section>
  );
}
