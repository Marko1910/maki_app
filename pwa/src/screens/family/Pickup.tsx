import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Empty, Failure, Icon, Loading, RumaMap, Stepper, TopBar, useToast, type MapPin } from "../../components/ui";
import { ACTIVE, assignNearestRider, cancelPickup, createPickup, createPickupFromDetection, humanize, loadMaterials, loadPrimaryAddress, loadRiderPosition, loadRiderProfile, loadTrackedPickup, savePrimaryAddress, updateReportedQuantities, type Row } from "../../lib/api";
import { distanceKm, here, isoDate, meters, pickupLatLng, STATUS, streetRoute, useAsync, useInterval, VEHICLE, type LatLng, type RoadRoute } from "../../lib/util";
import { useScan } from "./Scan";

const WINDOWS = [
  { day: "Hoy", time: "3:00 - 6:00 pm" },
  { day: "Mañana", time: "9:00 - 12:00 m" },
  { day: "Mañana", time: "2:00 - 5:00 pm" },
];

export function PickupForm() {
  const nav = useNavigate();
  const toast = useToast();
  const { scan, setScan } = useScan();
  const { data, loading, error, reload } = useAsync(async () => {
    const [address, materials] = await Promise.all([loadPrimaryAddress(), loadMaterials()]);
    return { address, materials };
  });
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [day, setDay] = useState("Hoy");
  const [windowIx, setWindowIx] = useState(0);
  const [fix, setFix] = useState<LatLng | null>(null);
  const [gps, setGps] = useState<"busy" | "ok" | "none">("busy");
  const [editing, setEditing] = useState(false);
  const [line, setLine] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { void here().then((p) => { setFix(p); setGps(p ? "ok" : "none"); }); }, []);
  useEffect(() => {
    if (!data) return;
    setLine(data.address?.address_line ?? "");
    // Scanned items travel as the starting counts; otherwise the family picks by hand.
    const seed: Record<string, number> = {};
    for (const it of scan?.items ?? []) if (it.material_id) seed[it.material_id] = it.quantity;
    setCounts(seed);
  }, [data, scan]);

  const fromScan = !!scan?.detectionId;
  const materials: Row[] = data?.materials ?? [];
  const shown = fromScan ? materials.filter((m) => counts[m.id] != null) : materials;
  const options = WINDOWS.filter((w) => w.day === day);

  const confirm = async () => {
    const selections = Object.entries(counts).filter(([, n]) => n > 0) as [string, number][];
    if (!selections.length) return toast("Agrega al menos un material al recojo.");
    if (!line.trim()) return toast("Escribe la dirección de recojo.");
    setBusy(true);
    try {
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
      toast(assigned ? `¡Recojo solicitado! ${assigned.rider_name ?? "Un Eco-Rider"} va en camino.` : "¡Recojo solicitado! Estamos buscando un Eco-Rider.");
      nav(`/f/seguimiento${id ? `?id=${id}` : ""}`, { replace: true });
    } catch (e) { toast(humanize(e)); setBusy(false); }
  };

  return (
    <main className="screen with-bar">
      <TopBar title="Programar recojo" />
      {loading ? <Loading /> : error ? <Failure text={error} retry={() => void reload()} /> : <>
        <p className="t-over">Ítems a recoger</p>
        <div className="glass list">
          {shown.map((m) => (
            <div key={m.id}><span className="badge">{(m.code as string).slice(0, 3)}</span><span className="min0 t-row">{m.name}</span>
              <Stepper value={counts[m.id] ?? 0} label={m.name} onChange={(n) => setCounts({ ...counts, [m.id]: n })} /></div>
          ))}
          {!shown.length && <div className="muted">No hay ítems para recoger</div>}
        </div>

        <p className="t-over">Punto de recojo</p>
        {(fix || data?.address?.latitude != null) && <RumaMap pins={[fix ?? { lat: data!.address.latitude, lng: data!.address.longitude }]} />}
        <div className="glass pad stack">
          {editing ? <span className="input-wrap"><input aria-label="Dirección de recojo" placeholder="Jr. Las Flores 123, SJL" value={line} onChange={(e) => setLine(e.target.value)} /></span>
            : <p className="t-row">{line || "Aún no tienes dirección"}</p>}
          <p className="muted t-small">{gps === "busy" ? "Obteniendo tu ubicación…" : gps === "ok" ? "Ubicación GPS de tu celular · el Eco-Rider vendrá aquí" : "Sin GPS: usaremos tu dirección registrada"}</p>
          <div className="row">
            <button className="btn-text" onClick={() => { setGps("busy"); void here(0).then((p) => { setFix(p); setGps(p ? "ok" : "none"); }); }}>Usar mi ubicación</button>
            <button className="btn-text" onClick={() => setEditing(!editing)}>{editing ? "Listo" : "Cambiar dirección"}</button>
          </div>
        </div>

        <p className="t-over">Ventana horaria</p>
        <div className="grid2">{["Hoy", "Mañana"].map((d) => <button key={d} className="glass filter" aria-pressed={day === d} onClick={() => { setDay(d); setWindowIx(0); }}>{d}</button>)}</div>
        <div className="stack" role="radiogroup" aria-label="Horario">
          {options.map((w, i) => <button key={w.time} role="radio" aria-checked={windowIx === i} className="glass choice" onClick={() => setWindowIx(i)}><span className="min0 t-row">{w.time}</span><span className="radio" /></button>)}
        </div>
        <p className="muted t-small">Los puntos se acreditarán cuando el Eco-Rider confirme la recolección.</p>
        <div className="bottombar"><div><button className="btn btn-primary" disabled={busy} onClick={() => void confirm()}>{busy ? "Procesando…" : "Confirmar recojo"}</button></div></div>
      </>}
    </main>
  );
}

const FLOW = ["requested", "assigned", "en_route", "arriving", "completed"];
const FLOW_LABEL = ["Pedido", "Asignado", "En camino", "Llegando", "Recogido"];
const POLL_MS = 10000;

export function Tracking() {
  const nav = useNavigate();
  const toast = useToast();
  const [params] = useSearchParams();
  const pickupId = params.get("id");
  const { data: p, loading, error, reload } = useAsync(() => loadTrackedPickup(pickupId), [pickupId]);
  const [rider, setRider] = useState<LatLng | null>(null);
  const [road, setRoad] = useState<RoadRoute | null>(null);
  const [vehicle, setVehicle] = useState<Row | null>(null);
  const [busy, setBusy] = useState(false);
  const live = !!p && ACTIVE.includes(p.status);

  const dest = p ? pickupLatLng(p) : null;
  const readRider = async () => {
    if (!p?.rider_id) return;
    const pos = await loadRiderPosition(p.rider_id).catch(() => null);
    if (pos?.current_latitude != null) setRider({ lat: pos.current_latitude, lng: pos.current_longitude });
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

  if (loading) return <main className="screen"><TopBar title="Seguimiento" /><Loading text="Buscando tu recojo…" /></main>;
  if (error) return <main className="screen"><TopBar title="Seguimiento" /><Failure text={error} retry={() => void reload()} /></main>;
  if (!p) return <main className="screen"><TopBar title="Seguimiento" /><Empty title="Aún no tienes recojos" text="Escanea tus residuos y pide tu primer recojo." icon="truck" /></main>;

  const step = p.status === "collecting" ? 3 : FLOW.indexOf(p.status);
  const name = p.rider?.display_name;
  const km = rider && dest ? (road?.km ?? distanceKm(rider, dest)) : null;
  const cancel = async () => {
    if (!window.confirm("¿Cancelar este recojo?")) return;
    setBusy(true);
    try { await cancelPickup(p.id); toast("Recojo cancelado."); await reload(); }
    catch (e) { toast(humanize(e)); } finally { setBusy(false); }
  };
  const title = p.status === "requested" ? "Buscando tu Eco-Rider" : p.status === "completed" ? "Recojo completado" : p.status === "cancelled" ? "Recojo cancelado" : "Tu Eco-Rider está llegando";

  return (
    <main>
      {pins.length ? <RumaMap pins={pins} path={road?.path} full /> : <div className="map map-full" style={{ display: "grid", placeItems: "center" }}><p className="muted">Tu dirección no tiene ubicación en el mapa</p></div>}
      <div className="float"><button className="icon-btn" aria-label="Volver" onClick={() => nav(-1)}><Icon name="back" /></button><span className="chip" style={{ minHeight: 32 }}>{live && <i className="pulse accent" />} Seguimiento</span></div>
      <section className="sheet">
        <div className="grab" />
        <h1 className="t-card">{title}</h1>
        {name && <div className="row">
          <span className="badge" style={{ borderRadius: "50%" }}>{name[0]}</span>
          <div className="min0"><p className="t-row">{name}</p><p className="muted t-small">{[VEHICLE[vehicle?.vehicle_type] ?? vehicle?.vehicle_type, vehicle?.rating ? `★ ${Number(vehicle.rating).toFixed(1)}` : null].filter(Boolean).join(" · ")}</p></div>
          {km != null && live && <div style={{ textAlign: "right" }}><p className="t-card accent">{meters(km)}</p>{road && <p className="muted t-small">~{road.minutes} min</p>}</div>}
        </div>}
        {step >= 0 ? <div className="status" aria-label={`Estado del recojo: ${STATUS[p.status]}`}>
          {FLOW_LABEL.map((l, i) => <div key={l} className={i < step ? "done" : i === step ? "now" : ""}><i />{l}</div>)}
        </div> : <span className="chip chip-off" style={{ width: "auto", alignSelf: "flex-start" }}>{STATUS[p.status] ?? p.status}</span>}
        <p className="muted t-small">Ítems a recoger: {(p.pickup_items ?? []).map((i: Row) => `${i.materials?.name} x${i.confirmed_quantity ?? i.reported_quantity}`).join(" · ") || "—"}</p>
        {p.rider?.phone && live && <a className="btn btn-primary" href={`tel:${p.rider.phone}`}><Icon name="phone" /> Llamar al Eco-Rider</a>}
        {live && !["arriving", "collecting"].includes(p.status) && <button className="btn-text danger" disabled={busy} style={{ color: "var(--danger)" }} onClick={() => void cancel()}>{busy ? "Cancelando…" : "Cancelar recojo"}</button>}
      </section>
    </main>
  );
}
