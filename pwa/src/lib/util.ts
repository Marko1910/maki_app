import { useCallback, useEffect, useRef, useState } from "react";
import { humanize, type Row } from "./api";

/* ---------- Data loading: every list resolves to loading, empty or error ---------- */

export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | undefined>();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const live = useRef(0);
  const reload = useCallback(async (quiet = false) => {
    const run = ++live.current;
    if (!quiet) setLoading(true);
    try {
      const d = await fn();
      if (run === live.current) { setData(d); setError(null); }
    } catch (e) {
      if (run === live.current && !quiet) setError(humanize(e));
    } finally {
      if (run === live.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { void reload(); }, [reload]);
  return { data, error, loading, reload };
}

export function useInterval(fn: () => void, ms: number | null) {
  const saved = useRef(fn);
  saved.current = fn;
  useEffect(() => {
    if (ms == null) return;
    const t = setInterval(() => saved.current(), ms);
    return () => clearInterval(t);
  }, [ms]);
}

/* ---------- Formats ---------- */

export const soles = (n: number | null | undefined) => `S/${(n ?? 0).toFixed(2)}`;
export const int = (n: number | null | undefined) => Math.round(n ?? 0).toLocaleString("en-US");
export const POINTS_PER_SOL = 1000;
export const ptsToSoles = (p: number) => soles(p / POINTS_PER_SOL);
export const kg = (n: number | null | undefined) => `${(n ?? 0).toLocaleString("en-US", { maximumFractionDigits: 1 })} kg`;

export function ago(iso?: string | null) {
  if (!iso) return "";
  const min = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  if (min < 1) return "Ahora";
  if (min < 60) return `Hace ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `Hace ${h}h`;
  const d = Math.floor(h / 24);
  if (d === 1) return "Ayer";
  if (d < 30) return `Hace ${d} días`;
  return new Date(iso).toLocaleDateString("es-PE", { day: "numeric", month: "short" });
}
export const clock = (d: Date) => d.toLocaleTimeString("es-PE", { hour: "numeric", minute: "2-digit" });
export const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString();
export function dayGroup(iso?: string | null) {
  if (!iso) return "ANTES";
  const d = new Date(iso), now = new Date();
  if (sameDay(d, now)) return "HOY";
  if (sameDay(d, new Date(now.getTime() - 864e5))) return "AYER";
  return d.toLocaleDateString("es-PE", { day: "numeric", month: "long" }).toUpperCase();
}
export const isoDate = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

export const initials = (name?: string | null) =>
  (name ?? "").trim().split(/\s+/).filter((w) => !/^(fam\.?|familia)$/i.test(w)).slice(0, 2).map((w) => w[0]?.toUpperCase()).join("") || "R";

export const itemsLine = (p: Row) =>
  (p.pickup_items ?? []).map((i: Row) => `${i.materials?.name ?? "Material"} x${i.confirmed_quantity ?? i.reported_quantity}`).join(" · ");

export const STATUS: Record<string, string> = {
  requested: "Buscando Eco-Rider", assigned: "Asignado", en_route: "En camino", arriving: "Llegando",
  collecting: "Recogiendo", completed: "Completado", cancelled: "Cancelado", failed: "No se pudo", no_match: "No coincidió",
};
export const VEHICLE: Record<string, string> = { moto: "Moto", bici: "Bici", bicicleta: "Bici", triciclo: "Triciclo", furgoneta: "Furgoneta", auto: "Auto" };

/* ---------- Device ---------- */

export type LatLng = { lat: number; lng: number };

export const here = (maxAgeMs = 30000) =>
  new Promise<LatLng | null>((resolve) => {
    if (!("geolocation" in navigator)) return resolve(null);
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => resolve(null),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: maxAgeMs },
    );
  });

export function distanceKm(a: LatLng, b: LatLng) {
  const rad = Math.PI / 180, dLat = (b.lat - a.lat) * rad, dLng = (b.lng - a.lng) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(h));
}
export const meters = (km: number) => (km < 1 ? `A ${Math.round(km * 1000)} m` : `A ${km.toFixed(1)} km`);

export const pickupLatLng = (p: Row): LatLng | null => {
  const lat = p.latitude ?? p.address?.latitude, lng = p.longitude ?? p.address?.longitude;
  return lat != null && lng != null ? { lat, lng } : null;
};

/** Keeps the screen on where it matters; on iOS, a sleeping PWA stops reporting location. */
export function useWakeLock(active: boolean) {
  useEffect(() => {
    if (!active || !("wakeLock" in navigator)) return;
    let lock: WakeLockSentinel | null = null, gone = false;
    const take = () => navigator.wakeLock.request("screen").then((l) => { if (gone) void l.release(); else lock = l; }).catch(() => undefined);
    const onVisible = () => { if (document.visibilityState === "visible") void take(); };
    void take();
    document.addEventListener("visibilitychange", onVisible);
    return () => { gone = true; lock?.release().catch(() => undefined); document.removeEventListener("visibilitychange", onVisible); };
  }, [active]);
}

export { streetRoute, streetTrip, type RoadRoute } from "./navigation";
