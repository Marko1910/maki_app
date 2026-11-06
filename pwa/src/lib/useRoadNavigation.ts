import { useEffect, useRef, useState } from "react";
import { NavigationRoutePolicy, routeProgress, streetRoute, validPoint, type RoadRoute, type RoutePoint } from "./navigation";

type Fix = RoutePoint & { accuracy: number; time: number };

/** Foreground navigation. An iPhone PWA cannot promise GPS/voice while suspended. */
export function useRoadNavigation(key: string, destination: RoutePoint | null, vehicle?: string | null) {
  const [fix, setFix] = useState<Fix | null>(null);
  const [locationError, setLocationError] = useState<string | null>("Buscando tu ubicación…");
  const [result, setResult] = useState<{ key: string; road: RoadRoute } | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());
  const [gpsRetry, setGpsRetry] = useState(0);
  const latest = useRef<Fix | null>(null);
  const request = useRef<(force?: boolean) => void>(() => {});
  const requestKey = `${key}:${destination?.lat}:${destination?.lng}:${vehicle}`;

  useEffect(() => {
    if (!navigator.geolocation) { setLocationError("Este navegador no ofrece ubicación."); return; }
    const watch = navigator.geolocation.watchPosition((g) => {
      const point = { lat: g.coords.latitude, lng: g.coords.longitude, accuracy: g.coords.accuracy, time: g.timestamp };
      if (!validPoint(point)) return;
      latest.current = point;
      setFix(point);
      setLocationError(point.accuracy > 60 ? "La ubicación es imprecisa. Busca un lugar con mejor señal." : null);
      request.current();
    }, (e) => {
      latest.current = null;
      setFix(null);
      setLocationError(e.code === 1 ? "Permite la ubicación precisa en los ajustes del navegador." : "No recibimos señal GPS. Comprueba que la ubicación esté activada.");
    }, { enableHighAccuracy: true, maximumAge: 3000, timeout: 12000 });
    return () => navigator.geolocation.clearWatch(watch);
  }, [gpsRetry]);

  useEffect(() => {
    const policy = new NavigationRoutePolicy();
    let disposed = false, controller: AbortController | null = null;
    let current: RoadRoute | null = null;
    let awayFixes = 0, countedFixTime = 0;
    setResult(null); setError(null); setLoading(false);
    const update = async (force = false) => {
      const point = latest.current, time = Date.now();
      setNow(time);
      if (disposed || !destination || !point || time - point.time > 15000 || point.accuracy > 60 || document.visibilityState === "hidden") return;
      const away = current ? (routeProgress(point, current.path)?.offRouteKm ?? 0) > Math.max(0.06, point.accuracy / 1000 * 1.5) : false;
      if (point.time !== countedFixTime) { awayFixes = away ? awayFixes + 1 : 0; countedFixTime = point.time; }
      const ticket = policy.begin(requestKey, time, awayFixes >= 2, force);
      if (ticket == null) return;
      controller = new AbortController();
      setLoading(true);
      const next = await streetRoute([point, destination], vehicle, controller.signal);
      if (disposed || !policy.complete(ticket, Date.now(), next != null)) return;
      setLoading(false);
      setError(next ? null : "No pudimos actualizar la ruta. Reintentaremos automáticamente.");
      if (next) { current = next; setResult({ key: requestKey, road: next }); }
    };
    request.current = (force) => { void update(force); };
    void update();
    const timer = setInterval(() => { void update(); }, 3000);
    const resume = () => { if (document.visibilityState === "visible") void update(true); };
    document.addEventListener("visibilitychange", resume);
    return () => {
      disposed = true; controller?.abort(); policy.reset(); clearInterval(timer);
      document.removeEventListener("visibilitychange", resume); request.current = () => {};
    };
    // Coordinates and profile are fully represented by requestKey.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestKey]);

  const road = result?.key === requestKey ? result.road : null;
  const fresh = fix && now - fix.time <= 15000 && fix.accuracy <= 60;
  const position = fresh ? fix : null;
  const gpsMessage = locationError ?? (!fresh ? "Esperando una ubicación reciente…" : null);
  const progress = position && road ? routeProgress(position, road.path) : null;
  const onRoute = progress && progress.offRouteKm <= 0.06;
  const travelled = onRoute && progress.totalKm > 0 && road ? progress.travelledKm / progress.totalKm * road.km : 0;
  const step = onRoute && !error ? road?.steps.find((s) => s.atKm >= travelled - 0.01 && s.atKm > 0) ?? road?.steps[road.steps.length - 1] : null;
  return {
    road, position, gpsMessage, loading, error,
    step, stepMeters: step ? Math.max(0, Math.round((step.atKm - travelled) * 1000)) : null,
    remainingKm: road ? Math.max(0, road.km - travelled) : null,
    remainingMinutes: road ? Math.max(1, Math.ceil(road.minutes * (1 - travelled / Math.max(road.km, 0.001)))) : null,
    retry: () => { if (gpsMessage) setGpsRetry((n) => n + 1); request.current(true); },
  };
}
