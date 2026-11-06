import { createContext, useCallback, useContext, useEffect, useRef, useState, type InputHTMLAttributes, type ReactNode } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

/* ---------- Icons: one stroke set, so no screen invents its own ---------- */

const PATHS: Record<string, ReactNode> = {
  back: <path d="M19 12H5m6-6-6 6 6 6" />,
  chevron: <path d="m9 6 6 6-6 6" />,
  arrow: <path d="M5 12h14m-6-6 6 6-6 6" />,
  check: <path d="m5 12 5 5L20 7" />,
  close: <path d="M6 6l12 12M18 6 6 18" />,
  home: <path d="M4 11 12 4l8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z" />,
  camera: <><path d="M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z" /><circle cx="12" cy="13" r="3.5" /></>,
  market: <><path d="M4 9 5.5 4h13L20 9" /><path d="M4 9a2.7 2.7 0 0 0 5.3 0 2.7 2.7 0 0 0 5.4 0A2.7 2.7 0 0 0 20 9M5 12v8h14v-8" /></>,
  user: <><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></>,
  bell: <path d="M6 16V11a6 6 0 1 1 12 0v5l2 2H4zm4 4a2 2 0 0 0 4 0" />,
  board: <><rect x="4" y="4" width="7" height="7" rx="1.5" /><rect x="13" y="4" width="7" height="7" rx="1.5" /><rect x="4" y="13" width="7" height="7" rx="1.5" /><rect x="13" y="13" width="7" height="7" rx="1.5" /></>,
  route: <><circle cx="6" cy="19" r="2.5" /><circle cx="18" cy="5" r="2.5" /><path d="M8.5 19H15a3.5 3.5 0 0 0 0-7H9a3.5 3.5 0 0 1 0-7h6.5" /></>,
  pin: <><path d="M12 21s7-6.2 7-11a7 7 0 1 0-14 0c0 4.8 7 11 7 11z" /><circle cx="12" cy="10" r="2.5" /></>,
  locate: <><circle cx="12" cy="12" r="7" /><circle cx="12" cy="12" r="2" /><path d="M12 2v3m0 14v3M2 12h3m14 0h3" /></>,
  clock: <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></>,
  phone: <path d="M6 3h3l2 5-2.5 1.5a11 11 0 0 0 5 5L15 12l5 2v3a3 3 0 0 1-3 3A14 14 0 0 1 3 6a3 3 0 0 1 3-3z" />,
  wallet: <><rect x="3" y="6" width="18" height="14" rx="2.5" /><path d="M16 13h2M3 10h18" /></>,
  history: <><path d="M4 12a8 8 0 1 0 2.5-5.8L4 8.5V4" /><path d="M12 8v4l3 2" /></>,
  trophy: <path d="M7 4h10v5a5 5 0 0 1-10 0zM7 6H4v1a3 3 0 0 0 3 3m10-4h3v1a3 3 0 0 1-3 3m-5 4v3m-4 3h8l-1-3H9z" />,
  chat: <path d="M4 5h16v11H9l-5 4z" />,
  chart: <path d="M5 20V10m7 10V4m7 16v-7" />,
  edit: <path d="m4 20 1-4L16 5l3 3L8 19zM14 7l3 3" />,
  truck: <><path d="M3 6h11v10H3zM14 10h4l3 3v3h-7z" /><circle cx="7.5" cy="17.5" r="1.8" /><circle cx="17" cy="17.5" r="1.8" /></>,
  box: <path d="m12 3 8 4.5v9L12 21l-8-4.5v-9zM4 7.5 12 12l8-4.5M12 12v9" />,
  flash: <path d="M13 3 5 14h6l-1 7 8-11h-6z" />,
  send: <path d="M4 12 20 4l-4 16-4-7zm8 1 8-9" />,
  eye: <><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z" /><circle cx="12" cy="12" r="3" /></>,
  lock: <><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" /></>,
  sound: <path d="M4 9v6h4l5 4V5L8 9zm12 0a4 4 0 0 1 0 6m2-9a8 8 0 0 1 0 12" />,
  info: <><circle cx="12" cy="12" r="8.5" /><path d="M12 11v5m0-8v.5" /></>,
  plus: <path d="M12 5v14M5 12h14" />,
  logout: <path d="M10 4H5v16h5m3-12 4 4-4 4m4-4H9" />,
  recycle: <path d="m8 7 4-4 3 5M16 12l3 5-2 3h-5m-4 0H6l-2-3 3-5m2-1-5 1m12 2 1 5m-5-13-3 5" />,
  bike: <><circle cx="6" cy="17" r="3.5" /><circle cx="18" cy="17" r="3.5" /><path d="m6 17 4-8h5l3 8M10 9 9 6H7m5 11 3-8" /></>,
};
export function Icon({ name, size = 22 }: { name: string; size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{PATHS[name]}</svg>;
}

export function Logo({ size = 88 }: { size?: number }) {
  return <img src="/ruma-logo.svg" width={size} height={size} alt="Ruma" />;
}
export function LogoPlate({ plate = 112 }: { plate?: number }) {
  return <div className="glass" style={{ width: plate, height: plate, borderRadius: plate * 0.25, display: "grid", placeItems: "center", margin: "0 auto", flex: "none" }}><Logo size={Math.round(plate * 0.76)} /></div>;
}

/* ---------- Feedback ---------- */

const ToastCtx = createContext<(m: string) => void>(() => undefined);
export const useToast = () => useContext(ToastCtx);
export function ToastProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState<string | null>(null);
  const t = useRef<number | undefined>(undefined);
  const show = useCallback((m: string) => {
    setMsg(m);
    clearTimeout(t.current);
    t.current = window.setTimeout(() => setMsg(null), 3600);
  }, []);
  return <ToastCtx.Provider value={show}>{children}{msg && <div className="toast" role="status">{msg}</div>}</ToastCtx.Provider>;
}

export const Loading = ({ text }: { text?: string }) => <div className="state" role="status"><div className="spinner" />{text && <p>{text}</p>}</div>;
export const Empty = ({ title, text, icon = "box" }: { title: string; text?: string; icon?: string }) => (
  <div className="state"><div className="badge" style={{ width: 56, height: 56, borderRadius: 16 }}><Icon name={icon} size={26} /></div><p className="t-card" style={{ color: "var(--ink)" }}>{title}</p>{text && <p>{text}</p>}</div>
);
export const Failure = ({ text, retry }: { text: string; retry?: () => void }) => (
  <div className="state"><p>{text}</p>{retry && <button className="btn btn-secondary btn-small" onClick={retry}>Reintentar</button>}</div>
);

/* ---------- Layout ---------- */

export function TopBar({ title, sub, right, back = true }: { title: string; sub?: string; right?: ReactNode; back?: boolean }) {
  const nav = useNavigate();
  return (
    <header className="topbar">
      {back && <button className="icon-btn" aria-label="Volver" onClick={() => nav(-1)}><Icon name="back" /></button>}
      <div className="min0"><h1 className={back ? "t-card" : "t-title"}>{title}</h1>{sub && <p className="muted t-small">{sub}</p>}</div>
      {right}
    </header>
  );
}

export type Tab = { to: string; icon: string; label: string };
export function TabBar({ tabs }: { tabs: Tab[] }) {
  return (
    <nav className="tabbar" aria-label="Principal"><div>
      {tabs.map((t) => <NavLink key={t.to} to={t.to} className={({ isActive }) => (isActive ? "active" : "")}><Icon name={t.icon} /><span>{t.label}</span></NavLink>)}
    </div></nav>
  );
}

export function NavRow({ icon, title, sub, onClick, right }: { icon?: string; title: string; sub?: string; onClick?: () => void; right?: ReactNode }) {
  const body = <>
    {icon && <span className="badge"><Icon name={icon} /></span>}
    <span className="min0"><span className="t-row" style={{ display: "block" }}>{title}</span>{sub && <span className="muted t-small" style={{ display: "block" }}>{sub}</span>}</span>
    {right}{onClick && <span className="chevron"><Icon name="chevron" size={18} /></span>}
  </>;
  return onClick ? <button onClick={onClick}>{body}</button> : <div>{body}</div>;
}

export const Metric = ({ value, label }: { value: string; label: string }) => (
  <div className="glass pad"><div className="t-card">{value}</div><div className="muted t-small">{label}</div></div>
);

export function Stepper({ value, onChange, label }: { value: number; onChange: (n: number) => void; label: string }) {
  return (
    <div className="stepper" role="group" aria-label={label}>
      <button aria-label={`Quitar uno de ${label}`} onClick={() => onChange(Math.max(0, value - 1))}>−</button>
      <span aria-live="polite">{value}</span>
      <button aria-label={`Sumar uno a ${label}`} onClick={() => onChange(value + 1)}>+</button>
    </div>
  );
}

export function Field({ label, helper, right, ...input }: { label: string; helper?: string; right?: ReactNode } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="field">
      <span className="t-over">{label}</span>
      <span className="input-wrap"><input {...input} />{right}</span>
      {helper && <span className="muted t-small">{helper}</span>}
    </label>
  );
}

export function Bars({ data }: { data: { label: string; value: number; text?: string; today?: boolean }[] }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  return (
    <div className="bars" role="img" aria-label={data.map((d) => `${d.label}: ${d.text ?? d.value}`).join(", ")}>
      {data.map((d, i) => <div key={i} className={d.today ? "today" : ""}><span className="t-small" style={{ fontWeight: 800 }}>{d.text ?? d.value}</span><i style={{ height: `${(d.value / max) * 70}%` }} /><span className="muted t-small">{d.label}</span></div>)}
    </div>
  );
}

/* ---------- Map: Leaflet over OSM, no key ---------- */

export type MapPin = { lat: number; lng: number; label?: string; color?: string; current?: boolean };

export function RumaMap({ pins, path, full, followFirst, recenter = 0 }: { pins: MapPin[]; path?: [number, number][]; full?: boolean; followFirst?: boolean; recenter?: number }) {
  const el = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const layer = useRef<L.LayerGroup | null>(null);
  const framed = useRef(false);
  const following = useRef(true);
  const centered = useRef(recenter);
  const routeFramed = useRef(false);
  useEffect(() => {
    if (!el.current || map.current) return;
    map.current = L.map(el.current, { zoomControl: false }).setView([-12.05, -77.04], 13);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 19, attribution: '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>' }).addTo(map.current);
    layer.current = L.layerGroup().addTo(map.current);
    const manual = () => { following.current = false; };
    const pinch = (e: TouchEvent) => { if (e.touches.length > 1) manual(); };
    map.current.on("dragstart", manual);
    el.current.addEventListener("wheel", manual, { passive: true });
    el.current.addEventListener("touchstart", pinch, { passive: true });
    const host = el.current;
    return () => { host.removeEventListener("wheel", manual); host.removeEventListener("touchstart", pinch); map.current?.remove(); map.current = null; framed.current = false; };
  }, []);
  const key = JSON.stringify([pins, path]);
  useEffect(() => {
    const m = map.current, g = layer.current;
    if (!m || !g) return;
    g.clearLayers();
    const accent = getComputedStyle(el.current!).getPropertyValue("--accent").trim() || "#0F6E56";
    if (path?.length) {
      L.polyline(path, { color: "white", weight: 9, opacity: 1, interactive: false }).addTo(g);
      L.polyline(path, { color: accent, weight: 5, opacity: 1, interactive: false }).addTo(g);
    }
    for (const p of pins) {
      const node = document.createElement("div");
      node.className = p.current ? "pin pin-current" : "pin";
      node.style.background = p.color ?? accent;
      node.textContent = p.current ? "" : p.label && p.label.length <= 3 ? p.label : "•";
      const icon = L.divIcon({ className: "map-marker", iconSize: [34, 34], iconAnchor: [17, 17], html: node });
      const tooltip = document.createElement("span"); tooltip.textContent = p.label ?? "Destino";
      L.marker([p.lat, p.lng], { icon, title: p.label ?? "Destino", alt: p.label ?? "Destino", keyboard: true }).bindTooltip(tooltip).addTo(g);
    }
    const pts: [number, number][] = [...pins.map((p) => [p.lat, p.lng] as [number, number]), ...(path ?? [])];
    if (centered.current !== recenter) { following.current = true; centered.current = recenter; framed.current = false; }
    const current = pins.find((p) => p.current) ?? pins[0];
    if (followFirst && following.current && current) {
      if (!framed.current) m.setView([current.lat, current.lng], 16);
      else m.panTo([current.lat, current.lng], { animate: false });
      framed.current = true;
    } else if (pts.length && (!framed.current || (!routeFramed.current && path?.length && following.current))) {
      if (pts.length === 1) m.setView(pts[0], 15);
      else m.fitBounds(L.latLngBounds(pts), { padding: [36, 36], maxZoom: 16 });
      framed.current = true;
    }
    routeFramed.current = !!path?.length;
    const resize = setTimeout(() => m.invalidateSize(), 60); // Leaving the screen first must not touch a removed map.
    return () => clearTimeout(resize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, recenter, followFirst]);
  return <div ref={el} className={`map ${full ? "map-full" : ""}`} />;
}
