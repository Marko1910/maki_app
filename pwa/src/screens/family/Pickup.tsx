import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Empty, Failure, Icon, NavRow, Skeleton, RumaMap, StepDots, PointsRule, Stepper, TopBar, useToast, type MapPin } from "../../components/ui";
import { ACTIVE, assignNearestRider, cancelPickup, createPickup, createPickupFromDetection, humanize, loadGeneratorProfile, loadMyProfile, loadSavedPile, updateMyPhone, loadMaterials, loadPickups, loadPrimaryAddress, loadRiderPosition, loadRiderProfile, loadTrackedPickup, savePrimaryAddress, updateReportedQuantities, type Row } from "../../lib/api";
import { ago, clock, distanceKm, here, initials, int, isoDate, meters, ptsToSoles, windowText, pickupLatLng, STATUS, streetRoute, useAsync, useInterval, VEHICLE, type LatLng, type RoadRoute } from "../../lib/util";
import { DispatchScene, PinMap } from "../../components/Dispatch";
import { PushCard } from "../../components/Push";
import { pileCounts } from "../../lib/pile";
import { normalizePhone, PHONE_HINT } from "../../lib/phone";
import { useScan } from "./Scan";
import { goBack, returnHome, tick } from "../../lib/flow";

const WINDOWS = [
  { day: "Hoy", time: "3:00 - 6:00 pm", ends: 18 },
  { day: "Mañana", time: "9:00 - 12:00 m", ends: 12 },
  { day: "Mañana", time: "2:00 - 5:00 pm", ends: 17 },
];
/** A rider needs about an hour to fit a stop in: today's window closes for new requests an hour before it ends. */
const LEAD_HOURS = 1;
const limaHour = () => Number(new Date().toLocaleString("en-US", { timeZone: "America/Lima", hour: "numeric", hour12: false })) % 24;
const openWindows = () => WINDOWS.filter((w) => w.day !== "Hoy" || limaHour() < w.ends - LEAD_HOURS);

export function PickupForm() {
  const nav = useNavigate();
  const toast = useToast();
  const { scan, setScan } = useScan();
  const { data, loading, error, reload } = useAsync(async () => {
    const [address, materials, pile, me, gp] = await Promise.all([loadPrimaryAddress(), loadMaterials(), loadSavedPile().catch(() => [] as Row[]), loadMyProfile().catch(() => null), loadGeneratorProfile().catch(() => null)]);
    return { address, materials, pile, hasPhone: !!me?.phone, sackCode: (gp?.profiles?.ruma_code as string | undefined) ?? null };
  });
  const [counts, setCounts] = useState<Record<string, number>>({});
  const windows = useMemo(openWindows, []);
  const [day, setDay] = useState(() => windows[0]?.day ?? "Mañana");
  const [windowIx, setWindowIx] = useState(0);
  const [fix, setFix] = useState<LatLng | null>(null);
  const [gps, setGps] = useState<"busy" | "ok" | "none">("busy");
  const [editing, setEditing] = useState(false);
  const [line, setLine] = useState("");
  const [busy, setBusy] = useState(false);
  const [phone, setPhone] = useState("");

  useEffect(() => { void here().then((p) => { setFix(p); setGps(p ? "ok" : "none"); }); }, []);
  useEffect(() => {
    if (!data) return;
    setLine(data.address?.address_line ?? "");
    // The whole saved pile (every scan with no pickup yet) is the starting count; otherwise the family picks by hand.
    setCounts(pileCounts(data.pile as never, scan as never));
  }, [data, scan]);

  const fromScan = !!scan?.detectionId || !!data?.pile.length;
  const materials: Row[] = data?.materials ?? [];
  const shown = fromScan ? materials.filter((m) => counts[m.id] != null) : materials;
  const options = windows.filter((w) => w.day === day);

  const confirm = async () => {
    const selections = Object.entries(counts).filter(([, n]) => n > 0) as [string, number][];
    if (!selections.length) return toast("Agrega al menos un material al recojo.");
    if (!line.trim()) return toast("Escribe la dirección de recojo.");
    // The database refuses it too (require_pickup_contact); asking here keeps the family on the form.
    const tel = data?.hasPhone ? null : normalizePhone(phone);
    if (!data?.hasPhone && !tel) return toast(PHONE_HINT);
    setBusy(true);
    try {
      if (tel) await updateMyPhone(tel);
      let addressId: string | null = data?.address?.id ?? null;
      if (line.trim() !== (data?.address?.address_line ?? "")) {
        await savePrimaryAddress(addressId, line.trim(), fix?.lat, fix?.lng);
        addressId = (await loadPrimaryAddress())?.id ?? null;
      }
      const chosen = options[windowIx] ?? options[0];
      const label = `${chosen.day} ${chosen.time}`;
      const date = new Date();
      if (chosen.day !== "Hoy") date.setDate(date.getDate() + 1);
      let id: string | null;
      if (fromScan) {
        id = await createPickupFromDetection(addressId, isoDate(date), label, fix);
        // The RPC copies the AI's counts; re-state what the family actually has.
        if (id) await updateReportedQuantities(id, selections).catch(() => undefined);
      } else {
        id = await createPickup(addressId, isoDate(date), label, selections, fix);
      }
      // Dispatch must never sink the request: unassigned, it stays on the open board.
      const assigned = id ? await assignNearestRider(id).catch(() => null) : null;
      setScan(null);
      tick();
      // Home, with the pickup on the next-step card; "atrás" no longer reopens the form or the camera.
      returnHome(nav, { dispatch: { rider: assigned ? (assigned.rider_name ?? "Un Eco-Rider") : null, eta: assigned?.eta_minutes ?? null, later: chosen.day !== "Hoy" ? label : null } });
    } catch (e) { toast(humanize(e)); setBusy(false); }
  };

  return (
    <main className="screen with-bar">
      <TopBar title="Programar recojo" />
      {loading ? <Skeleton label="Cargando tu recojo…" blocks={[20, 128, 20, 200, 96, 20, 112]} /> : error ? <Failure text={error} retry={() => void reload()} /> : <>
        <PointsRule units={shown.reduce((s, m) => s + (counts[m.id] ?? 0), 0)} />
        <p className="t-over">Ítems a recoger</p>
        <div className="glass list">
          {shown.map((m) => (
            <div key={m.id}><span className="badge">{(m.code as string).slice(0, 3)}</span><span className="min0 t-row">{m.name}</span>
              <Stepper value={counts[m.id] ?? 0} label={m.name} onChange={(n) => setCounts({ ...counts, [m.id]: n })} /></div>
          ))}
          {!shown.length && <div className="muted">No hay ítems para recoger</div>}
        </div>

        {data?.sackCode && <section className="glass pad stack" style={{ gap: 4 }}>
          <p className="t-row">Escribe {data.sackCode} en tu saco</p>
          <p className="muted t-small">El Eco-Rider recoge tu saco y el acopio lo pesa y verifica por ese código. Ahí se acreditan tus puntos.</p>
        </section>}
        <p className="t-over">Punto de recojo</p>
        {(fix || data?.address?.latitude != null) && <PinMap at={fix ?? { lat: data!.address.latitude, lng: data!.address.longitude }} />}
        <div className="glass pad stack">
          <p className="t-row">{gps === "busy" ? "Buscando tu ubicación…" : gps === "ok" ? "Tu ubicación actual" : "No pudimos usar el GPS"}</p>
          <p className="muted t-small">{gps === "ok" ? "El Eco-Rider vendrá a este punto del mapa." : gps === "none" ? "Activa la ubicación para que el Eco-Rider llegue a tu puerta. Mientras tanto usaremos tu dirección." : "Usamos el GPS de tu celular."}</p>
          {editing ? <span className="input-wrap"><input aria-label="Dirección o referencia" placeholder="Jr. Las Flores 123, puerta azul" value={line} onChange={(e) => setLine(e.target.value)} /></span>
            : <p className="t-small">{line ? `Referencia: ${line}` : "Agrega una referencia para el Eco-Rider"}</p>}
          <div className="row">
            {gps !== "ok" && <button className="btn-text" onClick={() => { setGps("busy"); void here(0).then((p) => { setFix(p); setGps(p ? "ok" : "none"); }); }}>Reintentar GPS</button>}
            <button className="btn-text" onClick={() => setEditing(!editing)}>{editing ? "Listo" : "Cambiar referencia"}</button>
          </div>
        </div>

        {data && !data.hasPhone && <>
          <p className="t-over">Tu celular</p>
          <label className="glass pad field">
            <span className="input-wrap"><input type="tel" inputMode="tel" autoComplete="tel" aria-label="Tu celular" placeholder="987 654 321" value={phone} onChange={(e) => setPhone(e.target.value)} /></span>
            <span className="muted t-small">El Eco-Rider te llamará si no encuentra tu casa. Lo guardamos en tu perfil.</span>
          </label>
        </>}
        <p className="t-over">Ventana horaria</p>
        <div className="grid2">{["Hoy", "Mañana"].map((d) => {
          const none = !windows.some((w) => w.day === d);
          return <button key={d} className="glass filter" aria-pressed={day === d} disabled={none} onClick={() => { setDay(d); setWindowIx(0); }}>{d}{none ? " · ya no hay horario" : ""}</button>;
        })}</div>
        <div className="stack" role="radiogroup" aria-label="Horario">
          {options.map((w, i) => <button key={w.time} role="radio" aria-checked={windowIx === i} className="glass choice" onClick={() => setWindowIx(i)}><span className="min0 t-row">{w.time}</span><span className="radio" /></button>)}
        </div>
        <p className="muted t-small">El acopio pesa y verifica tu saco: ahí se acreditan tus puntos y puedes cobrarlos.</p>
        <div className="bottombar"><div><button className="btn btn-primary" disabled={busy} onClick={() => void confirm()}>{busy ? "Procesando…" : "Confirmar recojo"}</button></div></div>
      </>}
    </main>
  );
}

const FLOW = ["requested", "assigned", "en_route", "arriving", "completed"];
const FLOW_LABEL = ["Pedido", "Asignado", "En camino", "Llegando", "Recogido"];
const FLOW_AT = ["requested_at", "assigned_at", "started_at", "arrived_at", "completed_at"];
const POLL_MS = 10000;
/** Android's thresholds: an old fix is labelled, a lost one stops claiming a distance. */
const STALE_MS = 3 * 60000, LOST_MS = 15 * 60000;
/** What the inbox gets from trg_pickup_notify. Push with the app closed doesn't exist yet: promise only the inbox. */
const NOTIFY = ["Un Eco-Rider tome tu recojo", "Salga hacia tu casa", "Recoja tus residuos"];

const hhmm = (iso?: string | null) => (iso ? clock(new Date(iso)) : "");
const pts = (n?: number | null) => (n ? `${int(n)} pts` : "—");
const sumPoints = (p: Row) => (p.pickup_items ?? []).reduce((s: number, i: Row) => s + (i.points ?? 0), 0);


function Flow({ p }: { p: Row }) {
  const current = p.status === "completed" ? FLOW.length : p.status === "collecting" ? 3 : FLOW.indexOf(p.status);
  const steps = FLOW_LABEL.map((l, i) => [l, i < current ? hhmm(p[FLOW_AT[i]] ?? (i === 0 ? p.created_at : null)) : undefined] as [string, string?]);
  return <StepDots steps={steps} current={current} label={`Estado del recojo: ${STATUS[p.status] ?? p.status}`} />;
}

function RiderCard({ p, vehicle, call }: { p: Row; vehicle: Row | null; call?: boolean }) {
  const name: string = p.rider?.display_name;
  return (
    <section className="glass pad stack">
      <p className="t-over">Tu Eco-Rider</p>
      <div className="row">
        <span className="badge" style={{ borderRadius: "50%", width: 48, height: 48, background: "var(--accent)", color: "#fff" }}>{initials(name)}</span>
        <div className="min0"><p className="t-row">{name}</p><p className="muted t-small">{[VEHICLE[vehicle?.vehicle_type] ?? vehicle?.vehicle_type, vehicle?.rating ? `★ ${Number(vehicle.rating).toFixed(1)}` : null].filter(Boolean).join(" · ")}</p></div>
      </div>
      {call && p.rider?.phone && <a className="btn btn-secondary" href={`tel:${p.rider.phone}`}><Icon name="phone" /> Llamar al Eco-Rider</a>}
    </section>
  );
}

export function Tracking() {
  const nav = useNavigate();
  const toast = useToast();
  const [params] = useSearchParams();
  const pickupId = params.get("id");
  const { data: p, loading, error, reload } = useAsync(() => loadTrackedPickup(pickupId), [pickupId]);
  const [rider, setRider] = useState<LatLng | null>(null);
  const [seenAt, setSeenAt] = useState<string | null>(null);
  const [road, setRoad] = useState<RoadRoute | null>(null);
  const [vehicle, setVehicle] = useState<Row | null>(null);
  const [busy, setBusy] = useState(false);
  const live = !!p && ACTIVE.includes(p.status);

  const dest = p ? pickupLatLng(p) : null;
  const readRider = async () => {
    if (!p?.rider_id) return;
    const pos = await loadRiderPosition(p.rider_id).catch(() => null);
    if (pos?.current_latitude != null) setRider({ lat: pos.current_latitude, lng: pos.current_longitude });
    setSeenAt(pos?.last_location_at ?? null);
  };
  useEffect(() => {
    void readRider();
    if (p?.rider_id) void loadRiderProfile(p.rider_id).then(setVehicle).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [p?.rider_id]);
  useEffect(() => {
    if (rider && dest) void streetRoute([rider, dest], vehicle?.vehicle_type).then(setRoad);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rider?.lat, rider?.lng, dest?.lat, dest?.lng]);
  useInterval(() => { void reload(true); void readRider(); }, live ? POLL_MS : null);

  const pins = useMemo(() => {
    const out: MapPin[] = [];
    if (dest) out.push({ ...dest, label: "Tú", color: "#16211C" });
    if (rider) out.push({ ...rider, label: "ER" });
    return out;
  }, [dest, rider]);

  if (loading) return <main className="screen"><TopBar title="Tu recojo" /><Skeleton label="Buscando tu recojo…" blocks={[180, 120, 96, 140]} /></main>;
  if (error) return <main className="screen"><TopBar title="Tu recojo" /><Failure text={error} retry={() => void reload()} /></main>;
  if (!p) return <main className="screen"><TopBar title="Tu recojo" /><Empty title="Aún no tienes recojos" text="Escanea tus residuos y pide tu primer recojo." icon="truck" action={{ label: "Registrar residuos", icon: "camera", onClick: () => nav("/f/camara") }} /></main>;

  const cancel = async () => {
    if (!window.confirm("¿Cancelar este recojo?")) return;
    setBusy(true);
    try { await cancelPickup(p.id); toast("Recojo cancelado."); await reload(); }
    catch (e) { toast(humanize(e)); } finally { setBusy(false); }
  };
  const cancelButton = !["arriving", "collecting"].includes(p.status) &&
    <button className="btn-text" disabled={busy} style={{ color: "var(--danger)", alignSelf: "center" }} onClick={() => void cancel()}>{busy ? "Cancelando…" : "Cancelar recojo"}</button>;

  if (!live) return <PickupDetail p={p} vehicle={vehicle} />;
  if (p.status === "requested" || p.status === "assigned") return <Waiting p={p} vehicle={vehicle} cancelButton={cancelButton} />;

  const name = p.rider?.display_name;
  const age = seenAt ? Date.now() - new Date(seenAt).getTime() : null;
  const lost = age != null && age > LOST_MS;
  const km = rider && dest && !lost ? (road?.km ?? distanceKm(rider, dest)) : null;
  const title = p.status === "collecting" ? "Recogiendo tus residuos" : p.status === "arriving" ? "Tu Eco-Rider está llegando" : "Tu Eco-Rider va en camino";

  return (
    <main>
      {pins.length ? <RumaMap pins={pins} path={lost ? undefined : road?.path} full /> : <div className="map map-full" style={{ display: "grid", placeItems: "center" }}><p className="muted">Tu dirección no tiene ubicación en el mapa</p></div>}
      <div className="float"><button className="icon-btn" aria-label="Volver" onClick={() => goBack(nav)}><Icon name="back" /></button><span className="chip chip-live" style={{ minHeight: 32 }}><i /> En vivo</span></div>
      <section className="sheet">
        <div className="grab" />
        <h1 className="t-card">{title}</h1>
        {name && <div className="row">
          <span className="badge" style={{ borderRadius: "50%" }}>{initials(name)}</span>
          <div className="min0"><p className="t-row">{name}</p><p className="muted t-small">{[VEHICLE[vehicle?.vehicle_type] ?? vehicle?.vehicle_type, vehicle?.rating ? `★ ${Number(vehicle.rating).toFixed(1)}` : null].filter(Boolean).join(" · ")}</p></div>
          {km != null && <div style={{ textAlign: "right" }}><p className="t-card accent">{meters(km)}</p>{road && <p className="muted t-small">~{road.minutes} min</p>}</div>}
        </div>}
        {age != null && age > STALE_MS && <p className="muted t-small">{lost ? "El Eco-Rider no está compartiendo su ubicación ahora. Sigue asignado a tu recojo; llámalo si lo necesitas." : `Ubicación de ${ago(seenAt).toLowerCase()}`}</p>}
        <Flow p={p} />
        <p className="muted t-small">Ítems a recoger: {(p.pickup_items ?? []).map((i: Row) => `${i.materials?.name} x${i.confirmed_quantity ?? i.reported_quantity}`).join(" · ") || "—"}</p>
        {p.rider?.phone && <a className="btn btn-primary" href={`tel:${p.rider.phone}`}><Icon name="phone" /> Llamar al Eco-Rider</a>}
        {cancelButton}
      </section>
    </main>
  );
}

/** Requested or assigned: no map yet, just what is happening, what comes next and that the phone can be put down. */
function Waiting({ p, vehicle, cancelButton }: { p: Row; vehicle: Row | null; cancelButton: ReactNode }) {
  const name: string | undefined = p.rider?.display_name;
  const est = sumPoints(p);
  return (
    <main className="screen">
      <TopBar title="Tu recojo" right={<span className="row" style={{ gap: 8 }}><span className="chip chip-live"><i /> {name ? "Asignado" : "En espera"}</span><MyPickupsButton /></span>} />
      <DispatchScene key={name ?? "searching"} riderName={name} at={pickupLatLng(p)} />
      <section className="hero">
        <p className="t-over">Ventana de recojo</p>
        <p className="t-title">{windowText(p)}</p>
        <p className="soft t-small hero-rule">{[p.code, p.address?.address_line].filter(Boolean).join(" · ")}</p>
      </section>
      <section className="glass pad"><Flow p={p} /></section>
      <section className="glass pad stack">
        <div className="row" style={{ alignItems: "flex-start" }}><span className="accent"><Icon name={name ? "bike" : "pin"} /></span>
          <div className="min0"><p className="t-over accent">Qué pasa ahora</p><p className="t-row">{name ? `${name} tomó tu recojo.` : "Buscamos al Eco-Rider más cercano."}</p></div></div>
        <div className="row" style={{ alignItems: "flex-start" }}><span className="money"><Icon name="bell" /></span>
          <div className="min0"><p className="t-over">Qué sigue</p><p>{name ? "Saldrá hacia ti dentro de tu ventana. Cuando esté en camino verás su ubicación aquí." : "Cuando uno lo tome verás su nombre aquí y en tus notificaciones."}</p></div></div>
      </section>
      {name && <RiderCard p={p} vehicle={vehicle} call />}
      <PushCard quiet />
      <section className="glass pad stack">
        <p className="t-row">Te avisamos cuando</p>
        {NOTIFY.map((t) => <p key={t} className="row" style={{ gap: 10 }}><span className="accent"><Icon name="check" size={18} /></span>{t}</p>)}
      </section>
      <p className="glass pad calm"><span className="calm-check"><Icon name="check" size={16} /></span>Puedes cerrar la app. Cada cambio queda en tus notificaciones.</p>
      <div className="glass list">
        <div className="between"><span className="t-row">Lo que entregarás</span>{est > 0 && <span className="chip chip-money">~{int(est)} pts por confirmar</span>}</div>
        {(p.pickup_items ?? []).map((i: Row, k: number) => <div key={k}><span className="badge">{String(i.materials?.code ?? "").slice(0, 3)}</span><span className="min0 t-row">{i.materials?.name}</span><span className="t-row">x{i.reported_quantity}</span></div>)}
      </div>
      {cancelButton}
      <p className="muted t-small center">Puedes cancelarlo mientras el Eco-Rider no haya llegado.</p>
    </main>
  );
}

/** Completed, cancelled or failed: what was handed over, where the points stand, and when each step happened. */
function PickupDetail({ p, vehicle }: { p: Row; vehicle: Row | null }) {
  const nav = useNavigate();
  const done = p.status === "completed";
  const awarded: number = p.generator_points_awarded ?? 0, final: number | null = p.generator_points_final;
  const credited = final != null;
  const date = new Date(p.completed_at ?? p.cancelled_at ?? p.created_at).toLocaleDateString("es-PE", { weekday: "short", day: "numeric", month: "short" });
  const timeline = FLOW_LABEL.map((l, i) => [i === 3 ? "Llegó" : l, p[FLOW_AT[i]] ?? (i === 0 ? p.created_at : null)] as [string, string | null]).filter(([, t]) => t);
  if (!done && p.cancelled_at) timeline.push([p.status === "cancelled" ? "Cancelado" : "No se pudo recoger", p.cancelled_at]);
  return (
    <main className="screen">
      <TopBar title="Detalle del recojo" right={<span className="row" style={{ gap: 8 }}><span className={`chip ${done ? "chip-accent" : "chip-off"}`}>{STATUS[p.status] ?? p.status}</span><MyPickupsButton /></span>} />
      <section className="hero">
        <p className="t-over">{[p.code, date].filter(Boolean).join(" · ")}</p>
        {done ? <>
          <div className="between" style={{ alignItems: "baseline" }}><p><b className="t-hero">{int(credited ? final : awarded)}</b> <span className="t-card">pts</span></p>{credited && <span className="chip chip-hero">= {ptsToSoles(final!)}</span>}</div>
          <p className="soft">{credited ? "Acreditados · ya puedes canjearlos" : "Por confirmar · se acreditan cuando el acopio pese tu material"}</p>
        </> : <>
          <p className="t-title">{p.status === "cancelled" ? "Recojo cancelado" : "No se pudo recoger"}</p>
          {p.cancel_reason && <p className="soft">{p.cancel_reason}</p>}
        </>}
      </section>
      {done && <section className="glass pad stack">
        <p className="t-over">Evolución de tus puntos</p>
        <StepDots steps={[["Recogido", awarded ? `${int(awarded)} por confirmar` : undefined], ["Pesado", "en el acopio"], ["Acreditado", credited ? pts(final) : undefined]]} current={credited ? 3 : 1} label={credited ? `Puntos acreditados: ${int(final!)}` : "Puntos por confirmar: falta el pesaje en el acopio"} />
        <p className="muted t-small">{!credited ? "Se acreditan cuando el acopio pese el material del lote." : awarded && final !== awarded ? "El acopio pesó tu material y ajustó los puntos." : "El acopio pesó tu material y confirmó los puntos."}</p>
      </section>}
      <section className="glass pad stack">
        <p className="t-row">{done ? "Lo que entregaste" : "Lo que ibas a entregar"}</p>
        <table className="qty-table">
          <thead><tr><th>Material</th><th>Declaraste</th>{done && <th>Confirmado</th>}</tr></thead>
          <tbody>{(p.pickup_items ?? []).map((i: Row, k: number) => <tr key={k}><td>{i.materials?.name}</td><td>{i.reported_quantity}</td>{done && <td><b>{i.confirmed_quantity ?? "—"}</b></td>}</tr>)}</tbody>
        </table>
        {done && <p className="muted t-small">Lo confirmado lo cuenta el Eco-Rider en tu puerta y lo ajusta el peso en el acopio. Tus puntos salen de ahí, no de lo declarado.</p>}
      </section>
      {p.rider?.display_name && <RiderCard p={p} vehicle={vehicle} />}
      <section className="glass pad stack">
        <p className="t-row">Línea de tiempo</p>
        <ol className="timeline">{timeline.map(([l, t]) => <li key={l}><span>{l}</span><span>{hhmm(t)}</span></li>)}</ol>
      </section>
      {/* A closed pickup always ends on the next step: the next scan, and the money once it is in. */}
      <button className="btn btn-primary" onClick={() => nav("/f/camara")}><Icon name="camera" /> Escanear de nuevo</button>
      {credited && <button className="btn btn-secondary" onClick={() => nav("/f/wallet")}><Icon name="wallet" /> Ver mi wallet</button>}
    </main>
  );
}

function MyPickupsButton() {
  const nav = useNavigate();
  return <button className="icon-btn" aria-label="Ver mis recojos" onClick={() => nav("/f/recojos")}><Icon name="truck" /></button>;
}

/** "Mis recojos": every pickup, the newest as the big card on top and the rest as a list; each opens its own screen. */
export function MyPickups() {
  const nav = useNavigate();
  const { data, loading, error, reload } = useAsync(() => loadPickups(30), []);
  // The big card is the pickup still in flight (newest first); with none in flight, the newest one.
  const all: Row[] = data ?? [];
  const latest = all.find((x) => ACTIVE.includes(x.status)) ?? all[0];
  const rest = all.filter((x) => x !== latest);
  const title = (x: Row) => `Recojo · ${(x.pickup_items ?? []).map((i: Row) => `${i.confirmed_quantity ?? i.reported_quantity} ${i.materials?.code}`).join(" + ") || x.code}`;
  const sub = (x: Row) => (ACTIVE.includes(x.status) ? windowText(x) : dayLabel(x.completed_at ?? x.cancelled_at ?? x.created_at));
  const chip = (x: Row) => <span className={`chip ${ACTIVE.includes(x.status) ? "chip-live" : x.status === "completed" ? "chip-accent" : "chip-off"}`}>{STATUS[x.status] ?? x.status}</span>;
  return (
    <main className="screen">
      <TopBar title="Mis recojos" />
      {loading ? <Skeleton label="Cargando tus recojos…" blocks={[132, 20, 72, 72, 72]} /> : error ? <Failure text={error} retry={() => void reload()} />
        : !latest ? <Empty title="Aún no tienes recojos" text="Toma una foto de tus residuos y pide tu primer recojo." icon="truck" action={{ label: "Registrar residuos", icon: "camera", onClick: () => nav("/f/camara") }} />
        : <>
          <button className="hero" style={{ textAlign: "left", border: 0 }} onClick={() => nav(`/f/seguimiento?id=${latest.id}`)}>
            <p className="t-over">{ACTIVE.includes(latest.status) ? "Tu recojo en curso" : "Tu último recojo"}</p>
            <p className="t-title">{title(latest)}</p>
            <p className="soft">{STATUS[latest.status] ?? latest.status} · {sub(latest)}</p>
            <p className="soft t-small">{ACTIVE.includes(latest.status) ? "Toca para ver cómo va" : "Toca para ver el detalle"}</p>
          </button>
          {rest.length > 0 && <>
            <p className="t-over">Anteriores</p>
            <div className="glass list">
              {rest.map((x) => <NavRow key={x.id} icon="truck" title={title(x)} sub={sub(x)} right={chip(x)} onClick={() => nav(`/f/seguimiento?id=${x.id}`)} />)}
            </div>
          </>}
        </>}
    </main>
  );
}

const dayLabel = (iso?: string | null) => (iso ? new Date(iso).toLocaleDateString("es-PE", { weekday: "short", day: "numeric", month: "short" }) : "");
