import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Empty, Failure, Icon, Loading, NavRow, RumaMap, Stepper, TopBar, useToast, type MapPin } from "../../components/ui";
import { acceptPickup, completePickup, confirmQuantities, humanize, loadAvailablePickups, loadMyAssignedPickups, loadMyName, loadMyRiderProfile, loadNotifications, loadRiderEarnings, myId, releasePickup, setPickupStatus, setRiderAvailability, setRiderShiftStart, type Row } from "../../lib/api";
import { ago, clock, distanceKm, here, itemsLine, meters, pickupLatLng, sameDay, soles, streetRoute, streetTrip, useAsync, useInterval, useWakeLock, VEHICLE, type LatLng, type RoadRoute } from "../../lib/util";

/* ---------- Rider state shared by the whole graph ---------- */

type Rider = {
  profile: Row | null; name: string | null; assigned: Row[]; available: Row[]; todayEarned: number; monthEarned: number; unread: boolean;
  loading: boolean; error: string | null; onShift: boolean; origin: LatLng | null; selectedId: string | null;
  refresh: () => Promise<void>; select: (id: string | null) => void; setOnShift: (v: boolean) => Promise<void>; active: Row | null;
  completed: Row | null; setCompleted: (c: Row | null) => void;
};
const Ctx = createContext<Rider>(null as unknown as Rider);
export const useRider = () => useContext(Ctx);

const SHIFT_KEY = "ruma.shift";
type Plan = { at: number; rider: string };
const readPlan = (): Plan | null => { try { return JSON.parse(localStorage.getItem(SHIFT_KEY) ?? "null"); } catch { return null; } };
const writePlan = (p: Plan | null) => { try { if (p) localStorage.setItem(SHIFT_KEY, JSON.stringify(p)); else localStorage.removeItem(SHIFT_KEY); } catch { /* private mode */ } };

const REFRESH_MS = 20000;

export function RiderProvider({ children }: { children: ReactNode }) {
  const toast = useToast();
  const load = useAsync(async () => {
    const [profile, name, assigned, available, earnings, notes] = await Promise.all([
      loadMyRiderProfile(), loadMyName(), loadMyAssignedPickups(), loadAvailablePickups().catch(() => []), loadRiderEarnings().catch(() => []), loadNotifications().catch(() => []),
    ]);
    const now = new Date();
    const inMonth = earnings.filter((e) => { const d = new Date(e.created_at); return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear(); });
    return {
      profile, name, assigned, available,
      todayEarned: inMonth.filter((e) => sameDay(new Date(e.created_at), now)).reduce((s, e) => s + e.amount, 0),
      monthEarned: inMonth.reduce((s, e) => s + e.amount, 0),
      unread: notes.some((n) => !n.is_read),
    };
  });
  const [selectedId, select] = useState<string | null>(null);
  const [origin, setOrigin] = useState<LatLng | null>(null);
  const [shift, setShift] = useState<boolean | null>(null);
  const [completed, setCompleted] = useState<Row | null>(null);
  const known = useRef<Set<string> | null>(null);
  const d = load.data;
  const onShift = shift ?? d?.profile?.is_available ?? false;

  useEffect(() => { void here().then((p) => { if (p) setOrigin(p); }); }, []);
  const refresh = useCallback(async () => { await load.reload(true); }, [load]);
  useInterval(() => { void refresh(); void here().then((p) => { if (p) setOrigin(p); }); }, REFRESH_MS);

  // A stop the dispatcher just handed over is news worth telling.
  useEffect(() => {
    if (!d) return;
    const ids = new Set(d.assigned.map((p) => p.id as string));
    if (known.current && [...ids].some((i) => !known.current!.has(i))) toast("¡Nuevo recojo asignado!");
    known.current = ids;
  }, [d, toast]);

  const setOnShift = useCallback(async (v: boolean) => {
    const prev = onShift;
    setShift(v);
    try { await setRiderAvailability(v); toast(v ? "Estás en turno: te asignarán recojos." : "Saliste de turno."); }
    catch (e) { setShift(prev); toast(humanize(e)); }
  }, [onShift, toast]);

  // iOS cannot wake a closed PWA, so a booked shift starts on the first look after its hour.
  useEffect(() => {
    if (!d) return;
    const tick = () => {
      const plan = readPlan();
      if (!plan || plan.rider !== myId() || plan.at > Date.now()) return;
      writePlan(null);
      setRiderShiftStart(null).catch(() => undefined);
      void setOnShift(true);
    };
    tick();
    const t = setInterval(tick, 30000);
    return () => clearInterval(t);
  }, [d, setOnShift]);

  const assigned = useMemo(() => d?.assigned ?? [], [d]);
  const active = assigned.find((p) => p.id === selectedId) ?? assigned[0] ?? null;
  const value: Rider = {
    profile: d?.profile ?? null, name: d?.name ?? null, assigned, available: d?.available ?? [], todayEarned: d?.todayEarned ?? 0, monthEarned: d?.monthEarned ?? 0,
    unread: d?.unread ?? false, loading: load.loading && !d, error: load.error, onShift, origin, selectedId, refresh, select, setOnShift, active, completed, setCompleted,
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

const familyOf = (p: Row) => p.generator?.display_name ?? "Vecino";
const addressOf = (p: Row) => p.address?.address_line ?? "Sin dirección";
const distanceTo = (from: LatLng | null, p: Row) => { const to = pickupLatLng(p); return from && to ? distanceKm(from, to) : null; };

/* ---------- Dashboard ---------- */

export function Dashboard() {
  const nav = useNavigate();
  const r = useRider();
  const [planning, setPlanning] = useState(false);
  const [time, setTime] = useState("15:00");
  const [plan, setPlan] = useState(readPlan());
  const ios = /iP(hone|ad|od)/.test(navigator.userAgent);
  if (r.loading) return <main className="screen with-nav"><Loading /></main>;
  if (r.error) return <main className="screen with-nav"><Failure text={r.error} retry={() => void r.refresh()} /></main>;
  const next = r.assigned[0] ?? null;
  const open = next ? null : r.available[0] ?? null;
  const stop = next ?? open;
  const goal = r.profile?.monthly_goal ?? 0;
  const km = stop ? distanceTo(r.origin, stop) : null;
  const firstName = r.name?.split(" ")[0];

  const book = () => {
    const [h, m] = time.split(":").map(Number);
    const at = new Date(); at.setHours(h, m, 0, 0);
    if (at.getTime() <= Date.now()) at.setDate(at.getDate() + 1);
    const p = { at: at.getTime(), rider: myId() ?? "" };
    writePlan(p); setPlan(p); setPlanning(false);
    setRiderShiftStart(at).catch(() => undefined);
  };
  const unbook = () => { writePlan(null); setPlan(null); setRiderShiftStart(null).catch(() => undefined); };
  const go = async () => {
    if (next) { r.select(next.id); nav("/r/navegar"); return; }
    if (open) nav("/r/tablero");
  };

  return (
    <main className="screen with-nav">
      <header className="between">
        <div className="min0"><h1 className="t-title">{firstName ? `Hola, ${firstName}` : "Hola"}</h1><p className="muted t-small">{[VEHICLE[r.profile?.vehicle_type] ?? r.profile?.vehicle_type, r.onShift ? "En turno" : "Fuera de turno"].filter(Boolean).join(" · ")}</p></div>
        <button className="icon-btn" aria-label={r.unread ? "Ver notificaciones sin leer" : "Ver notificaciones"} onClick={() => nav("/r/notificaciones")}><Icon name="bell" />{r.unread && <i className="dot" />}</button>
      </header>
      <section className="hero">
        <div className="between"><p className="t-over">Tu turno</p>
          <button className="toggle" role="switch" aria-checked={r.onShift} aria-label={r.onShift ? "En turno, tocar para salir de turno" : "Fuera de turno, tocar para entrar en turno"} onClick={() => void r.setOnShift(!r.onShift)}><i /></button></div>
        <p className="t-card">{r.onShift ? "En turno" : "Fuera de turno"}</p>
        <p className="soft t-small">{r.onShift ? "Recibes recojos del despacho automático" : "No te asignarán recojos; puedes tomarlos del tablero"}</p>
        <div className="hero-rule between"><span className="soft">Ganado hoy</span><span className="t-card">{soles(r.todayEarned)}</span></div>
        {plan && plan.at > Date.now() ? <div className="between"><span className="chip chip-hero">Sales {sameDay(new Date(plan.at), new Date()) ? "hoy" : "mañana"} {clock(new Date(plan.at))}</span><button className="btn-text" style={{ color: "#fff" }} aria-label="Quitar salida programada" onClick={unbook}>Quitar</button></div>
          : !r.onShift && (planning ? <div className="stack">
            <label className="soft t-small" htmlFor="salida">¿A qué hora sales?</label>
            <span className="input-wrap" style={{ background: "rgba(255,255,255,.9)", color: "var(--ink)" }}><input id="salida" type="time" value={time} onChange={(e) => setTime(e.target.value)} /></span>
            <p className="soft t-small">Tu turno se encenderá cuando abras Ruma después de esa hora.</p>
            <div className="grid2"><button className="btn chip-hero" onClick={() => setPlanning(false)}>Cancelar</button><button className="btn chip-hero" onClick={book}>Programar</button></div>
          </div> : <button className="btn-text" style={{ color: "#fff", textAlign: "left" }} onClick={() => setPlanning(true)}>Programar salida</button>)}
      </section>
      {ios && r.onShift && <p className="muted t-small row" style={{ gap: 6 }}><Icon name="info" size={16} /> En iPhone, mantén Ruma abierta durante tu turno para que la familia vea dónde estás.</p>}
      {stop ? <section className="glass pad stack">
        <p className="t-over">{next ? "Siguiente parada" : "Disponible ahora"}</p>
        <h2 className="t-card">{familyOf(stop)}</h2>
        <p className="muted">{addressOf(stop)}</p>
        <p className="t-small">{[km != null ? meters(km) : null, stop.window_label].filter(Boolean).join(" · ")}</p>
        <button className="btn btn-primary" onClick={() => void go()}>{next ? "Ir a la parada" : "Tomar este recojo"} <Icon name="arrow" /></button>
      </section> : <section className="glass pad stack"><h2 className="t-row">Sin recojos pendientes</h2><p className="muted">Te avisaremos cuando haya solicitudes</p></section>}
      <h2 className="t-card">Tus recojos</h2>
      <div className="glass list">
        <NavRow icon="board" title="Tablero de solicitudes" sub="Mira las solicitudes abiertas cerca de ti" onClick={() => nav("/r/tablero")} />
        <NavRow icon="route" title="Mi ruta del día" sub="Orden más rápido por calles y hora de llegada" onClick={() => nav("/r/ruta")} />
        <NavRow icon="box" title="Entregar en acopio" sub="Lleva tus recojos cerrados y cobra al pesaje" onClick={() => nav("/r/entrega")} />
      </div>
      <h2 className="t-card">Tu progreso</h2>
      <div className="glass list">
        <button onClick={() => nav("/r/ganancias")} style={{ display: "block" }}>
          <span className="between"><span className="t-row">Ganancias del mes</span><span className="t-row">{soles(r.monthEarned)}</span></span>
          {goal > 0 && <span className="stack" style={{ marginTop: 8 }}><span className="track" style={{ display: "block" }}><i style={{ width: `${Math.min(100, (r.monthEarned / goal) * 100)}%` }} /></span><span className="muted t-small">Meta: {soles(goal)}</span></span>}
        </button>
        <NavRow icon="trophy" title="Ranking de riders" sub="Mira tu posición de esta semana" onClick={() => nav("/r/ranking")} />
      </div>
    </main>
  );
}

/* ---------- Board ---------- */

export function Board() {
  const nav = useNavigate();
  const toast = useToast();
  const r = useRider();
  const [busy, setBusy] = useState<string | null>(null);
  const rows = useMemo(() => [...r.available].sort((a, b) => (distanceTo(r.origin, a) ?? 1e9) - (distanceTo(r.origin, b) ?? 1e9)), [r.available, r.origin]);
  const pins: MapPin[] = [...(r.origin ? [{ ...r.origin, label: "Tú" }] : []), ...rows.flatMap((p) => { const l = pickupLatLng(p); return l ? [{ ...l, color: "#55615A" }] : []; })];
  const claim = async (id: string) => {
    setBusy(id);
    try {
      if (await acceptPickup(id)) { toast("¡Recojo tomado! Ya está en tu ruta."); r.select(id); await r.refresh(); nav("/r/navegar"); }
      else { toast("Otro Eco-Rider tomó este recojo."); await r.refresh(); }
    } catch (e) { toast(humanize(e)); } finally { setBusy(null); }
  };
  return (
    <main className="screen with-nav">
      <TopBar title="Tablero de solicitudes" back={false} />
      {r.loading ? <Loading text="Buscando solicitudes abiertas…" /> : r.error ? <Failure text={r.error} retry={() => void r.refresh()} /> : !rows.length ? <Empty title="El tablero está vacío" text="Cuando alguien pida un recojo en tu zona aparecerá aquí y podrás tomarlo." icon="board" /> : <>
        {pins.length > 0 && <RumaMap pins={pins} />}
        <p className="t-over">Solicitudes abiertas</p>
        {rows.map((p, i) => {
          const km = distanceTo(r.origin, p);
          return <section className="glass pad stack" key={p.id}>
            <div className="between"><h2 className="t-card">{familyOf(p)}</h2>{km != null && <span className="chip">{meters(km)}</span>}</div>
            <p className="muted">{addressOf(p)}</p>
            <div className="between muted t-small"><span>{p.window_label}</span><span>{ago(p.created_at)}</span></div>
            {itemsLine(p) && <p className="t-small">{itemsLine(p)}</p>}
            <button className={`btn ${i === 0 ? "btn-primary" : "btn-secondary"}`} disabled={busy === p.id} onClick={() => void claim(p.id)}>{busy === p.id ? "Tomando…" : "Tomar este recojo"}</button>
          </section>;
        })}
      </>}
    </main>
  );
}

/* ---------- Route of the day ---------- */

export function RouteScreen() {
  const nav = useNavigate();
  const r = useRider();
  const [road, setRoad] = useState<RoadRoute | null>(null);
  const stops = r.assigned;
  const points = useMemo(() => stops.flatMap((p) => { const l = pickupLatLng(p); return l ? [{ p, l }] : []; }), [stops]);
  const key = points.map((x) => x.p.id).join() + (r.origin ? "@" : "");
  useEffect(() => {
    if (r.origin && points.length) void streetTrip(r.origin, points.map((x) => x.l), r.profile?.vehicle_type).then(setRoad);
    else setRoad(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  // Street order when OSRM answered; otherwise the order the stops came in.
  const ordered = road && road.order.length === points.length ? road.order.map((i) => points[i].p) : stops;
  const earn = stops.reduce((s, p) => s + (p.rider_earnings ?? 0), 0);
  const finish = road ? new Date(Date.now() + road.minutes * 60000) : null;
  const pins: MapPin[] = [...(r.origin ? [{ ...r.origin, label: "Tú" }] : []), ...ordered.flatMap((p, i) => { const l = pickupLatLng(p); return l ? [{ ...l, label: String(i + 1), color: "#16211C" }] : []; })];
  return (
    <main className="screen with-nav">
      <TopBar title="Ruta del Día" back={false} />
      {r.loading ? <Loading text="Cargando tu ruta…" /> : r.error ? <Failure text={r.error} retry={() => void r.refresh()} /> : !stops.length ? <Empty title="Sin paradas por ahora" text="Toma un recojo desde el tablero y aparecerá aquí, en orden de ruta." icon="route" /> : <>
        {pins.length > 0 && <RumaMap pins={pins} path={road?.path} />}
        <section className="hero">
          <p className="t-over">{road ? "Ruta más rápida por calles" : "Tu ruta"} · {stops.length} parada{stops.length > 1 ? "s" : ""}</p>
          {finish && <p className="t-title">Terminas ~{clock(finish)}</p>}
          <div className="grid2 hero-rule">
            <div><p className="soft t-small">Distancia</p><p className="t-card">{road ? `${road.km.toFixed(1)} km` : "—"}</p></div>
            <div><p className="soft t-small">Ganancia estimada</p><p className="t-card">{soles(earn)}</p></div>
          </div>
        </section>
        <p className="t-over">Paradas de tu ruta</p>
        <div className="glass list">{ordered.map((p, i) => <div key={p.id}>
          <span className="badge" style={{ borderRadius: "50%", background: "var(--accent)", color: "#fff", border: 0, width: 32, height: 32 }}>{i + 1}</span>
          <span className="min0"><span className="t-row" style={{ display: "block" }}>{familyOf(p)}</span><span className="muted t-small">{addressOf(p)}</span></span>
          <button className="btn btn-secondary btn-small" onClick={() => { r.select(p.id); nav("/r/navegar"); }}>Navegar</button>
        </div>)}</div>
        <button className="btn btn-primary" onClick={() => { r.select(ordered[0].id); nav("/r/navegar"); }}><Icon name="route" /> Iniciar navegación</button>
      </>}
    </main>
  );
}

/* ---------- Navigation ---------- */

const ARRIVING_KM = 0.15;
const REROUTE_KM = 0.04;

export function Navigation() {
  const nav = useNavigate();
  const toast = useToast();
  const r = useRider();
  const p = r.active;
  const [pos, setPos] = useState<LatLng | null>(r.origin);
  const [road, setRoad] = useState<RoadRoute | null>(null);
  const [voice, setVoice] = useState(false);
  const [busy, setBusy] = useState(false);
  const routedFrom = useRef<LatLng | null>(null);
  const flagged = useRef<Set<string>>(new Set());
  const dest = p ? pickupLatLng(p) : null;
  useWakeLock(true);

  // Pin the stop and flip it to en_route once, on entering.
  useEffect(() => {
    if (!p) return;
    r.select(p.id);
    if (p.status === "assigned" && !flagged.current.has(`route:${p.id}`)) {
      flagged.current.add(`route:${p.id}`);
      setPickupStatus(p.id, "en_route").then(() => r.refresh()).catch((e) => toast(humanize(e)));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [p?.id]);

  useEffect(() => {
    if (!("geolocation" in navigator)) return;
    const id = navigator.geolocation.watchPosition((g) => setPos({ lat: g.coords.latitude, lng: g.coords.longitude }), () => undefined, { enableHighAccuracy: true, maximumAge: 5000 });
    return () => navigator.geolocation.clearWatch(id);
  }, []);

  const km = pos && dest ? distanceKm(pos, dest) : null;
  useEffect(() => {
    if (!pos || !dest || !p) return;
    if (p.status === "en_route" && km != null && km <= ARRIVING_KM && !flagged.current.has(`near:${p.id}`)) {
      flagged.current.add(`near:${p.id}`);
      setPickupStatus(p.id, "arriving").then(() => r.refresh()).catch(() => undefined);
    }
    if (routedFrom.current && distanceKm(routedFrom.current, pos) < REROUTE_KM) return;
    routedFrom.current = pos;
    void streetRoute([pos, dest], r.profile?.vehicle_type).then((x) => { if (x) setRoad(x); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pos?.lat, pos?.lng, dest?.lat, dest?.lng, p?.status]);

  const say = (on: boolean) => {
    setVoice(on);
    if (on && "speechSynthesis" in window && p) {
      const u = new SpeechSynthesisUtterance(`Vas a ${familyOf(p)}, ${addressOf(p)}. ${road ? `Llegas en ${road.minutes} minutos.` : ""}`);
      u.lang = "es-PE";
      speechSynthesis.speak(u);
    } else if ("speechSynthesis" in window) speechSynthesis.cancel();
  };
  const arrive = async () => {
    if (!p) return;
    setBusy(true);
    try { if (p.status !== "collecting") await setPickupStatus(p.id, "collecting"); await r.refresh(); nav("/r/confirmar"); }
    catch (e) { toast(humanize(e)); setBusy(false); }
  };
  const release = async () => {
    if (!p || !window.confirm("¿Liberar este recojo? Volverá al tablero para otro Eco-Rider.")) return;
    setBusy(true);
    try { await releasePickup(p.id); toast("Liberaste el recojo: volvió al tablero para otro Eco-Rider."); r.select(null); await r.refresh(); nav("/r/inicio", { replace: true }); }
    catch (e) { toast(humanize(e)); setBusy(false); }
  };

  if (r.loading) return <main className="screen"><Loading /></main>;
  if (!p) return <main className="screen"><TopBar title="Navegación" /><Empty title="No hay un recojo en curso" text="Toma una solicitud del tablero para empezar a navegar." icon="route" /></main>;
  const index = r.assigned.findIndex((x) => x.id === p.id) + 1;
  const pins: MapPin[] = [...(pos ? [{ ...pos, label: "Tú" }] : []), ...(dest ? [{ ...dest, color: "#16211C" }] : [])];
  const eta = road ? new Date(Date.now() + road.minutes * 60000) : null;
  return (
    <main>
      <RumaMap pins={pins} path={road?.path} full followFirst />
      <div className="float">
        <button className="icon-btn" aria-label="Volver" onClick={() => nav(-1)}><Icon name="back" /></button>
        <span className="chip">Parada {index} de {r.assigned.length}</span>
        <span className="chip chip-accent" style={{ background: "rgba(255,255,255,.85)" }}><i className="pulse" /> {p.status === "arriving" ? "Llegando" : "En camino"}</span>
        <button className="chip" style={{ minHeight: 40 }} aria-pressed={voice} onClick={() => say(!voice)}><Icon name="sound" size={16} /> Guía por voz {voice ? "sí" : "no"}</button>
      </div>
      <section className="sheet">
        <div className="grab" />
        <p className="t-over">Destino</p>
        <h1 className="t-card">{familyOf(p)}</h1>
        <p>{addressOf(p)}</p>
        {(p.address?.reference || p.notes) && <p className="muted t-small">{p.address?.reference ?? p.notes}</p>}
        {!dest && <p className="danger t-small">Esta parada no tiene ubicación en el mapa. Llama a la familia.</p>}
        <div className="grid2">
          <div className="glass" style={{ padding: 12 }}><p className="muted t-small">Distancia</p><p className="t-row">{km != null ? meters(road?.km ?? km) : "—"}</p></div>
          <div className="glass" style={{ padding: 12 }}><p className="muted t-small">Llegada</p><p className="t-row">{eta ? clock(eta) : "—"}</p></div>
        </div>
        <div className="row">
          {p.generator?.phone && <a className="btn btn-secondary" style={{ width: "auto", flex: "none" }} href={`tel:${p.generator.phone}`}><Icon name="phone" /> Llamar</a>}
          <button className="btn btn-primary" disabled={busy} onClick={() => void arrive()}><Icon name="pin" /> He llegado al punto</button>
        </div>
        <button className="btn-text" style={{ color: "var(--danger)" }} disabled={busy} onClick={() => void release()}>No puedo ir · liberar este recojo</button>
      </section>
    </main>
  );
}

/* ---------- Confirm at the door ---------- */

export function Confirm() {
  const nav = useNavigate();
  const toast = useToast();
  const r = useRider();
  const p = r.active;
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!p) return;
    const seed: Record<string, number> = {};
    for (const i of p.pickup_items ?? []) if (i.materials?.id) seed[i.materials.id] = i.confirmed_quantity ?? i.reported_quantity;
    setCounts(seed);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [p?.id]);
  if (r.loading) return <main className="screen"><Loading /></main>;
  if (!p) return <main className="screen"><TopBar title="Confirmar Recolección" /><Empty title="No hay un recojo en curso" icon="box" /></main>;
  const items: Row[] = p.pickup_items ?? [];
  const confirm = async () => {
    setBusy(true);
    try {
      await confirmQuantities(p.id, Object.entries(counts));
      await completePickup(p.id);
      const remaining = r.assigned.filter((x) => x.id !== p.id).length;
      r.setCompleted({ name: familyOf(p), units: Object.values(counts).reduce((s, n) => s + n, 0), earnings: p.rider_earnings ?? 0, remaining });
      r.select(null);
      toast("¡Recolección confirmada!");
      await r.refresh();
      nav("/r/completado", { replace: true });
    } catch (e) { toast(humanize(e)); setBusy(false); }
  };
  return (
    <main className="screen with-bar">
      <TopBar title="Confirmar Recolección" />
      <section className="glass pad"><p className="t-row">{familyOf(p)}</p><p className="muted t-small">{addressOf(p)}</p><p className="muted t-small">{p.window_label}</p></section>
      <p className="t-over">Cuenta lo que recibes</p>
      {items.length ? <div className="glass list">
        {items.map((i) => <div key={i.materials?.id}>
          <span className="badge">{(i.materials?.code ?? "").slice(0, 3)}</span>
          <span className="min0"><span className="t-row" style={{ display: "block" }}>{i.materials?.name}</span><span className="muted t-small">La familia declaró {i.reported_quantity}</span></span>
          <Stepper value={counts[i.materials?.id] ?? 0} label={i.materials?.name ?? "material"} onChange={(n) => setCounts({ ...counts, [i.materials.id]: n })} />
        </div>)}
      </div> : <p className="muted">No hay materiales declarados</p>}
      <p className="muted t-small">Lo que cuentes aquí es lo que se registra: los puntos del vecino y tu pago salen de este número.</p>
      <section className="hero">
        <p className="t-over">Ganarás</p>
        <p className="t-hero">{soles(p.rider_earnings)}</p>
        {(p.total_weight_kg ?? 0) > 0 && <div className="between hero-rule"><span className="soft">Peso total</span><span style={{ fontWeight: 800 }}>{Number(p.total_weight_kg).toFixed(2)} kg</span></div>}
        <p className="soft t-small">Los puntos del generador se confirman en el acopio.</p>
      </section>
      <div className="bottombar"><div><button className="btn btn-primary" disabled={busy} onClick={() => void confirm()}>{busy ? "Confirmando…" : "Confirmar recolección"} <Icon name="check" /></button></div></div>
    </main>
  );
}

/* ---------- Completed ---------- */

export function Completed() {
  const nav = useNavigate();
  const { completed: c, assigned, select } = useRider();
  useLocation();
  useEffect(() => { if (!c) nav("/r/inicio", { replace: true }); }, [c, nav]);
  if (!c) return null;
  const left = assigned.length;
  return (
    <main className="screen">
      <div className="glass" style={{ width: 96, height: 96, borderRadius: "50%", display: "grid", placeItems: "center", margin: "24px auto 0", color: "var(--accent)" }}><Icon name="check" size={44} /></div>
      <div className="center"><h1 className="t-title">¡Recojo Completado!</h1><p className="muted">{c.name}</p></div>
      <section className="hero"><p className="t-over">Tus ganancias</p><p className="t-hero">{soles(c.earnings)}</p></section>
      <section className="glass pad stack"><h2 className="t-row">Resumen del recojo</h2>
        <div className="between"><span className="muted">Materiales</span><span className="t-row">{c.units} unidades</span></div>
        <div className="between"><span className="muted">Puntos al generador</span><span className="t-row">Se acreditan en el acopio</span></div>
      </section>
      <p className="glass pad row"><Icon name="route" /> {left === 0 ? "No te quedan paradas por ahora" : left === 1 ? "Te queda 1 parada en tu ruta" : `Te quedan ${left} paradas en tu ruta`}</p>
      <div className="grow" />
      {left > 0 && <button className="btn btn-primary" onClick={() => { select(assigned[0].id); nav("/r/navegar", { replace: true }); }}>Siguiente parada <Icon name="arrow" /></button>}
      <button className={`btn ${left > 0 ? "btn-secondary" : "btn-primary"}`} onClick={() => nav("/r/inicio", { replace: true })}>Volver al inicio</button>
    </main>
  );
}
