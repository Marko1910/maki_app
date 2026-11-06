export type RoutePoint = { lat: number; lng: number };
export type RoadStep = { atKm: number; instruction: string; key: string };
export type RoadRoute = { path: [number, number][]; km: number; minutes: number; order: number[]; steps: RoadStep[] };

export const validPoint = (p: RoutePoint) => Number.isFinite(p.lat) && Number.isFinite(p.lng) && Math.abs(p.lat) <= 90 && Math.abs(p.lng) <= 180;
const distance = (a: RoutePoint, b: RoutePoint) => {
  const rad = Math.PI / 180;
  const h = Math.sin((b.lat - a.lat) * rad / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin((b.lng - a.lng) * rad / 2) ** 2;
  return 12742 * Math.asin(Math.min(1, Math.sqrt(h)));
};

/** A failed request is retryable while stationary; destination changes invalidate old results. */
export class NavigationRoutePolicy {
  private generation = 0;
  private pending: number | null = null;
  private key = "";
  private retryAt = 0;
  private failures = 0;
  private refreshedAt: number | null = null;
  reset() { this.generation++; this.pending = null; this.key = ""; this.retryAt = 0; this.failures = 0; this.refreshedAt = null; }
  begin(key: string, now: number, offRoute: boolean, force = false): number | null {
    if (key !== this.key) { this.reset(); this.key = key; }
    if (this.pending != null || (!force && now < this.retryAt)) return null;
    if (!force && this.failures === 0 && this.refreshedAt != null && now - this.refreshedAt < (offRoute ? 10000 : 60000)) return null;
    this.pending = ++this.generation;
    return this.pending;
  }
  complete(ticket: number, now: number, success: boolean): boolean {
    if (ticket !== this.pending) return false;
    this.pending = null;
    if (success) { this.refreshedAt = now; this.retryAt = 0; this.failures = 0; }
    else this.retryAt = now + Math.min(30000, 3000 * 2 ** Math.min(this.failures++, 4));
    return true;
  }
}

export function routeProgress(here: RoutePoint, path: [number, number][]) {
  if (path.length < 2 || !validPoint(here)) return null;
  const sx = 111.32 * Math.cos(here.lat * Math.PI / 180), sy = 111.32;
  let offRouteKm = Infinity, travelledKm = 0, totalKm = 0;
  for (let i = 0; i < path.length - 1; i++) {
    const [a, b] = [path[i], path[i + 1]];
    const ax = (a[1] - here.lng) * sx, ay = (a[0] - here.lat) * sy;
    const dx = (b[1] - a[1]) * sx, dy = (b[0] - a[0]) * sy;
    const t = dx * dx + dy * dy ? Math.max(0, Math.min(1, -(ax * dx + ay * dy) / (dx * dx + dy * dy))) : 0;
    const d = Math.hypot(ax + t * dx, ay + t * dy);
    const length = distance({ lat: a[0], lng: a[1] }, { lat: b[0], lng: b[1] });
    if (d < offRouteKm) { offRouteKm = d; travelledKm = totalKm + t * length; }
    totalKm += length;
  }
  return { offRouteKm, travelledKm, totalKm };
}

type Maneuver = { type?: string; modifier?: string; exit?: number; location?: number[] };
type Step = { distance?: number; name?: string; maneuver?: Maneuver };
export function instruction(step: Step) {
  const m = step.maneuver ?? {};
  const turns: Record<string, string> = { left: "Gira a la izquierda", "sharp left": "Gira a la izquierda", right: "Gira a la derecha", "sharp right": "Gira a la derecha", "slight left": "Mantente a la izquierda", "slight right": "Mantente a la derecha", uturn: "Da la vuelta donde esté permitido" };
  const action = m.type === "arrive" ? "Llegaste al destino" : m.type === "depart" ? "Inicia el recorrido" : ["roundabout", "rotary"].includes(m.type ?? "") ? (m.exit ? `En la rotonda, toma la salida ${m.exit}` : "Entra en la rotonda") : turns[m.modifier ?? ""] ?? "Continúa recto";
  return action + (step.name && m.type !== "arrive" ? ` por ${step.name}` : "");
}

// Validate network data at the boundary. Never draw a broken or invented straight-line route.
export function parseRoad(payload: unknown, trip = false): RoadRoute | null {
  const j = payload as { code?: string; routes?: RoadPayload[]; trips?: RoadPayload[]; waypoints?: { waypoint_index: number }[] };
  const leg = trip ? j?.trips?.[0] : j?.routes?.[0];
  if (j?.code !== "Ok" || !leg || !Number.isFinite(leg.distance) || leg.distance < 0 || !Number.isFinite(leg.duration) || leg.duration < 0) return null;
  if (trip && (j.trips?.length !== 1 || !j.waypoints?.length || j.waypoints[0].waypoint_index !== 0 ||
    j.waypoints.map((w) => w.waypoint_index).sort((a, b) => a - b).some((n, i) => n !== i))) return null;
  const coords = leg.geometry?.coordinates;
  if (!Array.isArray(coords) || coords.length < 2 || coords.some((c) => !Array.isArray(c) || !validPoint({ lat: c[1], lng: c[0] }))) return null;
  let atKm = 0;
  const steps = (leg.legs ?? []).flatMap((l) => l.steps ?? []).map((s) => {
    const text = instruction(s);
    const result = { atKm, instruction: text, key: `${s.maneuver?.location?.join(",")}:${text}` };
    atKm += Math.max(0, Number(s.distance) || 0) / 1000;
    return result;
  });
  const order = trip ? (j.waypoints ?? []).slice(1).map((w, i) => ({ i, at: w.waypoint_index })).sort((a, b) => a.at - b.at).map((w) => w.i) : [];
  return { path: coords.map((c) => [c[1], c[0]]), km: leg.distance / 1000, minutes: Math.max(1, Math.ceil(leg.duration * 1.3 / 60)), order, steps };
}
type RoadPayload = { distance: number; duration: number; geometry?: { coordinates: number[][] }; legs?: { steps?: Step[] }[] };

// Share the public provider's one-request/second budget across this app instance.
// A production fleet still needs a provider with sufficient capacity or its own backend.
let slot: Promise<unknown> = Promise.resolve();
let lastRequestAt = 0;
function takeSlot(signal: AbortSignal) {
  const next = slot.then(async () => {
    if (signal.aborted) return false;
    const wait = Math.max(0, 1000 - (Date.now() - lastRequestAt));
    if (wait) await new Promise((resolve) => setTimeout(resolve, wait));
    if (signal.aborted) return false;
    lastRequestAt = Date.now();
    return true;
  });
  slot = next;
  return next;
}

async function request(service: "route" | "trip", points: RoutePoint[], vehicle?: string | null, signal?: AbortSignal) {
  if (points.length < 2 || points.some((p) => !validPoint(p))) return null;
  const controller = new AbortController();
  const abort = () => controller.abort();
  if (signal?.aborted) return null;
  signal?.addEventListener("abort", abort, { once: true });
  const timeout = setTimeout(abort, 10000);
  const profile = ["bicicleta", "bici", "triciclo"].includes(vehicle ?? "") ? "routed-bike" : vehicle === "a_pie" ? "routed-foot" : "routed-car";
  const extra = service === "trip" ? "&source=first&destination=any&roundtrip=false" : "";
  try {
    if (!await takeSlot(controller.signal)) return null;
    const coords = points.map((p) => `${p.lng},${p.lat}`).join(";");
    const response = await fetch(`https://routing.openstreetmap.de/${profile}/${service}/v1/driving/${coords}?overview=full&geometries=geojson&steps=true${extra}`, { signal: controller.signal });
    if (!response.ok) return null;
    const result = parseRoad(await response.json(), service === "trip");
    if (service === "trip" && result?.order.length !== points.length - 1) return null;
    return result;
  } catch { return null; }
  finally { clearTimeout(timeout); signal?.removeEventListener("abort", abort); }
}
export const streetRoute = (points: RoutePoint[], vehicle?: string | null, signal?: AbortSignal) => request("route", points, vehicle, signal);
export async function streetTrip(start: RoutePoint, stops: RoutePoint[], vehicle?: string | null, signal?: AbortSignal) {
  if (!stops.length) return null;
  if (stops.length === 1) {
    const route = await streetRoute([start, stops[0]], vehicle, signal);
    return route ? { ...route, order: [0] } : null;
  }
  return request("trip", [start, ...stops], vehicle, signal);
}
