import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { Icon, PointsRule, StepDots, Stepper, TopBar, useToast } from "../../components/ui";
import { commitScan, humanize, previewFrame, type Motion, type Observation, type Row } from "../../lib/api";
import { foldPhotos, type Photo } from "../../lib/fold";
import { int } from "../../lib/util";
import { goBack, markFlowStart, tick } from "../../lib/flow";

/* ---------- The scan the pickup form may link to ---------- */

export type Box = { box: number[]; label: string };
type Scan = { detectionId: string | null; items: Row[]; totalPoints: number; photo?: string; boxes?: Box[] };
const ScanCtx = createContext<{ scan: Scan | null; setScan: (s: Scan | null) => void }>({ scan: null, setScan: () => undefined });
export const useScan = () => useContext(ScanCtx);
export function ScanProvider({ children }: { children: ReactNode }) {
  const [scan, setScan] = useState<Scan | null>(null);
  return <ScanCtx.Provider value={{ scan, setScan }}>{children}</ScanCtx.Provider>;
}

/* ---------- Anti-spoof: the same gyro gate the Android app feeds ---------- */

const MOTION_MEMORY_MS = 8000;
type DME = typeof DeviceMotionEvent & { requestPermission?: () => Promise<"granted" | "denied"> };

/** iOS only grants motion from a tap, so the camera opens behind a button there. */
const motionNeedsTap = () => typeof DeviceMotionEvent !== "undefined" && typeof (DeviceMotionEvent as DME).requestPermission === "function";

class MotionSensor {
  private peak = 0; private peakAt = 0; private max = 0; private samples = 0; private start = Date.now();
  private on = (e: DeviceMotionEvent) => {
    const r = e.rotationRate;
    if (!r) return;
    // Browsers report deg/s; the server's gate is in degrees per second as well.
    const dps = Math.sqrt((r.alpha ?? 0) ** 2 + (r.beta ?? 0) ** 2 + (r.gamma ?? 0) ** 2);
    const now = Date.now();
    this.samples++;
    if (dps > this.max) this.max = dps;
    if (dps >= this.peak || now - this.peakAt > MOTION_MEMORY_MS) { this.peak = dps; this.peakAt = now; }
  };
  attach() { window.addEventListener("devicemotion", this.on); }
  detach() { window.removeEventListener("devicemotion", this.on); }
  /** A sweep is remembered: people move the phone, then hold it still to aim. */
  snapshot(): Motion {
    const now = Date.now();
    const remembered = now - this.peakAt <= MOTION_MEMORY_MS ? this.peak : 0;
    const out = { maxGyroDps: Math.max(this.max, remembered), samples: this.samples, windowMs: now - this.start };
    this.max = 0; this.samples = 0; this.start = now;
    return out;
  }
}

/* ---------- What the counter shows: mirrors foldPhotos in detect-material ---------- */

const pileTotal = (shots: Row[][]) => {
  const byCode = new Map<string, Photo[]>();
  for (const items of shots) for (const it of items) {
    const list = byCode.get(it.code) ?? [];
    list.push({ quantity: it.quantity, brands: it.brands ?? (it.brand ? { [it.brand]: it.quantity } : {}) });
    byCode.set(it.code, list);
  }
  return [...byCode.values()].reduce((s, p) => s + foldPhotos(p), 0);
};

/* ---------- Camera ---------- */

const BUSY_RETRIES = 3;
const MAX_DIM = 1024; // Labels read as well as at 1600, at half the weight (measured 2026-11-03).

export function Camera() {
  const nav = useNavigate();
  const toast = useToast();
  const { setScan } = useScan();
  const video = useRef<HTMLVideoElement>(null);
  const stream = useRef<MediaStream | null>(null);
  const sensor = useRef(new MotionSensor());
  const session = useRef(crypto.randomUUID());
  // Where the scan → pickup flow started, so asking for the pickup can back out of it (lib/flow.ts).
  useEffect(markFlowStart, []);
  const observations = useRef<Observation[]>([]);
  const shots = useRef<Row[][]>([]);
  const [phase, setPhase] = useState<"gate" | "aim" | "analyzing" | "result" | "closing" | "blocked">(motionNeedsTap() ? "gate" : "aim");
  const [hint, setHint] = useState("Junta tus residuos con las etiquetas a la vista y toma una foto");
  const [frozen, setFrozen] = useState<string | null>(null);
  // The last accepted photo and what was framed in it, shown on the result.
  const lastShot = useRef<{ photo: string; boxes: Box[] } | null>(null);
  const [photos, setPhotos] = useState(0);
  const [howto, setHowto] = useState(false);
  const [flash, setFlash] = useState(false);
  const [canFlash, setCanFlash] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const gone = useRef(false);

  const startCamera = async () => {
    try {
      const s = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: "environment" }, width: { ideal: 1920 }, height: { ideal: 1080 } }, audio: false });
      if (gone.current) { s.getTracks().forEach((t) => t.stop()); return; }
      stream.current = s;
      setStreaming(true);
      const caps = s.getVideoTracks()[0]?.getCapabilities?.() as MediaTrackCapabilities & { torch?: boolean };
      setCanFlash(!!caps?.torch);
      sensor.current.attach();
    } catch {
      setPhase("blocked");
    }
  };
  // The stream can land before the <video> exists: on iOS it is requested from the "Permitir"
  // tap, while the gate screen is still mounted. Attaching here covers both orders.
  useEffect(() => {
    const v = video.current;
    if (!v || !stream.current || v.srcObject === stream.current) return;
    v.srcObject = stream.current;
    void v.play().catch(() => undefined);
  }, [streaming, phase]);
  useEffect(() => {
    gone.current = false;
    if (phase !== "gate") void startCamera();
    const s = sensor.current;
    return () => { gone.current = true; stream.current?.getTracks().forEach((t) => t.stop()); s.detach(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const allow = async () => {
    // Must stay inside the tap: iOS refuses motion access from anywhere else.
    const r = await (DeviceMotionEvent as DME).requestPermission!().catch(() => "denied" as const);
    if (r !== "granted") toast("Sin acceso al movimiento no podremos verificar tus fotos.");
    setPhase("aim");
    await startCamera();
  };
  const toggleFlash = async () => {
    const track = stream.current?.getVideoTracks()[0];
    if (!track) return;
    await track.applyConstraints({ advanced: [{ torch: !flash } as MediaTrackConstraintSet] }).then(() => setFlash(!flash)).catch(() => toast("No se pudo cambiar el flash."));
  };

  const shoot = async () => {
    const v = video.current;
    if (!v || !v.videoWidth) { setHint("No se pudo tomar la foto. Intenta otra vez."); return; }
    const k = Math.min(1, MAX_DIM / Math.max(v.videoWidth, v.videoHeight));
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(v.videoWidth * k); canvas.height = Math.round(v.videoHeight * k);
    canvas.getContext("2d")!.drawImage(v, 0, 0, canvas.width, canvas.height);
    const dataUrl = canvas.toDataURL("image/jpeg", 0.85);
    setFrozen(dataUrl);
    setPhase("analyzing");
    const group = photos;
    let result: Row | null = null, failure: string | null = null;
    // The free vision tiers are busy at random: wait them out on the same photo.
    for (let attempt = 0; attempt <= BUSY_RETRIES; attempt++) {
      try { result = await previewFrame(session.current, dataUrl.split(",")[1], sensor.current.snapshot(), group); }
      catch (e) { failure = humanize(e); result = null; break; }
      if (result.rejected !== "busy" || attempt === BUSY_RETRIES) break;
      await new Promise((r) => setTimeout(r, result?.retry_after_ms ?? 3000));
    }
    if (!result) setHint(failure ?? "Sin conexión con la IA.");
    else if (result.rejected) setHint(result.message ?? "No pudimos leer la foto. Intenta otra vez.");
    else {
      const items: Row[] = result.items ?? [];
      observations.current.push({ items: items.map((i) => ({ code: i.code, quantity: i.quantity, quality: i.quality, brand: i.brand })), token: result.token ?? "", group });
      const before = pileTotal(shots.current);
      shots.current.push(items);
      lastShot.current = { photo: dataUrl, boxes: items.filter((i) => Array.isArray(i.box)).map((i) => ({ box: i.box, label: `${i.code} · ${i.brand ?? i.name}` })) };
      const added = pileTotal(shots.current) - before;
      const shown = items.reduce((s, i) => s + i.quantity, 0);
      setPhotos(group + 1);
      setHint(added === shown
        ? `Encontramos ${shown} en esta foto · ≈ ${items.reduce((s, i) => s + i.points, 0)} pts`
        : added > 0 ? `Encontramos ${shown}: ${shown - added} ya estaban contadas y suma ${added} nueva${added > 1 ? "s" : ""}.`
          : "Ya estaban contadas. Si trajiste otra botella igual, súmala con + al terminar.");
    }
    setPhase("result");
  };
  const again = () => { setFrozen(null); setPhase("aim"); setHint("Toma otra foto: las marcas ya contadas no se repiten, solo suma lo nuevo"); };
  const finish = async () => {
    setPhase("closing");
    try {
      const r = await commitScan(session.current, observations.current);
      if (r.rejected || !r.items?.length) { toast(r.message ?? "No se pudo verificar el escaneo. Intenta de nuevo."); setPhase("result"); return; }
      setScan({ detectionId: r.detection_id ?? null, items: r.items, totalPoints: r.total_points ?? 0, ...lastShot.current });
      nav("/f/resultado", { replace: true });
    } catch (e) { toast(humanize(e)); setPhase("result"); }
  };

  if (phase === "gate") return (
    <main className="cam" style={{ display: "grid", placeItems: "center" }}>
      <div className="cam-ui stack center" style={{ position: "relative", maxWidth: 360, gap: 16 }}>
        <p className="t-card">Activa la cámara y el movimiento</p>
        <p style={{ opacity: 0.85 }}>Mover un poco el teléfono nos ayuda a comprobar que tus residuos son reales, y no la foto de una pantalla.</p>
        <button className="btn btn-primary" onClick={() => void allow()}>Permitir</button>
        <button className="btn cam-glass" onClick={() => goBack(nav)}>Ahora no</button>
      </div>
    </main>
  );
  if (phase === "blocked") return (
    <main className="cam" style={{ display: "grid", placeItems: "center" }}>
      <div className="cam-ui stack center" style={{ position: "relative", maxWidth: 360, gap: 16 }}>
        <p className="t-card">Necesitamos tu cámara</p>
        <p style={{ opacity: 0.85 }}>Permite el acceso a la cámara en los ajustes del navegador para detectar tus residuos.</p>
        <button className="btn cam-glass" onClick={() => goBack(nav)}>Volver</button>
      </div>
    </main>
  );

  const busy = phase === "analyzing" || phase === "closing";
  return (
    <main className="cam">
      <video ref={video} playsInline muted autoPlay />
      {frozen && <img src={frozen} alt="Foto de tus residuos" />}
      <div className="cam-ui between" style={{ top: "calc(var(--safe-t) + 12px)" }}>
        <button className="icon-btn cam-glass" aria-label="Cerrar" onClick={() => goBack(nav)}><Icon name="close" /></button>
        <h1 className="t-row">Foto de tus residuos</h1>
        {canFlash ? <button className="icon-btn cam-glass" aria-label={flash ? "Apagar flash" : "Encender flash"} aria-pressed={flash} onClick={() => void toggleFlash()}><Icon name="flash" /></button> : <span style={{ width: 48 }} />}
      </div>
      {busy && <div className="cam-ui stack center" style={{ top: "42%", alignItems: "center" }} role="status">
        <div className="spinner" /><p className="t-row">{phase === "closing" ? "Cerrando el escaneo…" : "Tu material está siendo analizado por IA"}</p>
        {phase === "analyzing" && <p className="t-small">Contamos cada envase y leemos sus marcas. Espera unos segundos…</p>}
      </div>}
      {howto && <div className="cam-ui cam-glass pad stack" style={{ top: "24%", borderRadius: 16, gap: 12 }}>
        <p className="t-card">Cómo funciona la cámara</p>
        <p><b>Junta tus residuos.</b> Ponlos cerca de la cámara, con buena luz y con las etiquetas hacia ti, sin taparse entre ellos.</p>
        <p><b>Toma la foto.</b> Mueve un poco el teléfono al apuntar y mantenlo quieto al disparar: una foto movida pierde botellas.</p>
        <p><b>La IA la analiza.</b> En unos segundos ves cuántos hay de cada marca, y puedes corregir el número. ¿Tienes más? Toma otra foto.</p>
        <button className="btn btn-primary" onClick={() => setHowto(false)}>Entendido</button>
      </div>}
      <div className="cam-ui stack" style={{ bottom: "calc(var(--safe-b) + 20px)", gap: 16 }}>
        {!busy && <p className="cam-glass pad center" style={{ borderRadius: 16 }} role="status">{hint}</p>}
        {phase === "result" ? (
          <div className="stack">
            <button className="btn cam-glass" onClick={again}><Icon name="camera" /> {photos > 0 ? "Otra foto" : "Tomar de nuevo"}</button>
            {photos > 0 && <button className="btn btn-primary" onClick={() => void finish()}>Terminar <Icon name="arrow" /></button>}
          </div>
        ) : (
          <div className="between">
            <button className="chip cam-glass" style={{ minHeight: 48, padding: "0 14px" }} onClick={() => setHowto(true)}><Icon name="info" size={16} /> Cómo funciona</button>
            <button className="shutter" aria-label="Tomar foto" disabled={busy} onClick={() => { tick(); void shoot(); }} />
            <span className="chip cam-glass" style={{ minHeight: 48, padding: "0 14px" }}>{photos} foto{photos === 1 ? "" : "s"}</span>
          </div>
        )}
      </div>
    </main>
  );
}

/* ---------- Result ---------- */

export function Result() {
  const nav = useNavigate();
  const { scan, setScan } = useScan();
  useEffect(() => { if (!scan) nav("/f/camara", { replace: true }); }, [scan, nav]);
  if (!scan) return null;

  // Per-brand rows when the server named them; else one row for the material.
  const rows = scan.items.flatMap((it) => {
    const brands = Object.entries((it.brands ?? {}) as Record<string, number>);
    const named = brands.reduce((s, [, n]) => s + n, 0);
    const out = brands.map(([brand, n]) => ({ key: `${it.code}|${brand}`, code: it.code, brand, material: it.name as string, n }));
    if (it.quantity > named) out.push({ key: `${it.code}|`, code: it.code, brand: "Sin marca", material: it.name as string, n: it.quantity - named });
    return out;
  });
  const setRow = (key: string, value: number) => {
    const [code, brand] = key.split("|");
    setScan({
      ...scan,
      items: scan.items.map((it) => {
        if (it.code !== code) return it;
        const brands = { ...(it.brands ?? {}) } as Record<string, number>;
        const named = Object.values(brands).reduce((s, n) => s + n, 0);
        if (brand) {
          const quantity = it.quantity - (brands[brand] ?? 0) + value;
          brands[brand] = value;
          return { ...it, brands, quantity };
        }
        return { ...it, quantity: named + value };
      }),
    });
  };

  const items = rows.reduce((s, r) => s + r.n, 0);
  return (
    <main className="screen with-bar">
      <TopBar title="Residuos detectados" />
      <PaySteps />
      {scan.photo && <section className="glass pad stack">
        <p className="row t-row" style={{ gap: 8 }}><Icon name="camera" size={18} /> Tu foto</p>
        <PhotoBoxes src={scan.photo} boxes={scan.boxes ?? []} />
      </section>}
      <section className="hero pop">
        <p className="t-over">Eco-puntos estimados</p>
        <p className="t-hero">+ {int(scan.totalPoints)} <span className="t-card">pts</span></p>
        <p className="soft">Se acreditan cuando el acopio pesa tu material.</p>
      </section>
      <PointsRule units={items} />
      <section className="glass pad stack">
        <div className="between"><h2 className="t-card">Lo que encontramos</h2><span className="chip chip-accent">{items} {items === 1 ? "ítem" : "ítems"}</span></div>
        <p className="muted t-small row" style={{ gap: 6 }}><Icon name="info" size={16} /> Si contamos mal, corrige con + o −.</p>
        <div className="list found">
          {rows.map((r) => (
            <div key={r.key}>
              <span className="badge"><Icon name="recycle" /></span>
              <span className="min0 stack" style={{ gap: 4 }}>
                <span className="t-row">{r.brand}</span>
                <span className="chip chip-accent" style={{ alignSelf: "flex-start" }}>{r.code} · {KIND[r.code] ?? r.material}</span>
              </span>
              <Stepper value={r.n} label={`${r.brand}, ${r.material}`} onChange={(n) => setRow(r.key, n)} />
            </div>
          ))}
        </div>
      </section>
      <button className="btn btn-secondary" onClick={() => { setScan(null); nav("/f/camara", { replace: true }); }}><Icon name="camera" /> Otra foto</button>
      <p className="muted t-small center">¿Tienes más? Empieza un escaneo nuevo; este ya quedó guardado.</p>
      <div className="bottombar"><div><button className="btn btn-primary" onClick={() => nav("/f/recojo")}>Pedir recojo <Icon name="arrow" /></button></div></div>
    </main>
  );
}

/** What each material code looks like at home, for the chip under the brand. */
const KIND: Record<string, string> = { PET: "Botella de plástico", ALU: "Lata de aluminio", VID: "Envase de vidrio", CAR: "Cartón", PIL: "Pilas" };

const PAY_STEPS: [string, string][] = [["Registraste", "tus residuos"], ["El Eco-Rider", "confirma en tu puerta"], ["El acopio", "lo pesa"], ["Puntos a", "tu saldo"]];

/** Where the payment stands, read left to right: this screen is always right after step one. */
function PaySteps({ current = 1 }: { current?: number }) {
  return (
    <section className="glass pad stack">
      <p className="t-over accent">Cómo recibes tu pago</p>
      <StepDots steps={PAY_STEPS} current={current} label={`Cómo recibes tu pago: paso ${current + 1} de ${PAY_STEPS.length}, ${PAY_STEPS[current].join(" ")}`} />
    </section>
  );
}

/** The photo with each detection framed and named, in fractions of the image. */
function PhotoBoxes({ src, boxes }: { src: string; boxes: Box[] }) {
  return (
    <div className="photo-boxes">
      <img src={src} alt={boxes.length ? `Tu foto: ${boxes.map((b) => b.label).join(", ")}` : "Tu foto"} />
      {boxes.map((b, i) => {
        const [x0, y0, x1, y1] = b.box;
        return <span key={i} className="det-box" style={{ left: `${x0 * 100}%`, top: `${y0 * 100}%`, width: `${(x1 - x0) * 100}%`, height: `${(y1 - y0) * 100}%` }}><em>{b.label}</em></span>;
      })}
    </div>
  );
}
