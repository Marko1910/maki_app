import { useEffect, useState } from "react";
import { Navigate, Outlet, Route, Routes, useNavigate } from "react-router-dom";
import { Failure, Loading, TabBar, ToastProvider, useToast, type Tab } from "./components/ui";
import { humanize, loadMyRole, publishRiderLocation, sb, signOut } from "./lib/api";
import { IS_STAGING } from "./lib/config";
import { useWakeLock } from "./lib/util";
import { usePush } from "./components/Push";
import { useTransitionLocation } from "./lib/flow";
import { Login, Onboarding, Register, RoleSelect, Splash } from "./screens/auth/Auth";
import { Camera, Result, ScanProvider } from "./screens/family/Scan";
import { FamilyHistory, FamilyProfile, Home, Rankings, Wallet } from "./screens/family/Family";
import { Centers } from "./screens/family/Centers";
import { MyPickups, PickupForm, Tracking } from "./screens/family/Pickup";
import { Invite } from "./screens/family/Invite";
import { Plans } from "./screens/family/Plans";
import { Chat } from "./screens/family/Chat";
import { EditProfile, Market, Notifications } from "./screens/shared/Shared";
import { Board, Completed, Confirm, Dashboard, Navigation, RiderProvider, RouteScreen, useRider } from "./screens/rider/Work";
import { Delivery, Earnings, RiderHistory, RiderProfile, RiderRanking, Stats } from "./screens/rider/Records";

const FAMILY_TABS: Tab[] = [
  { to: "/f/inicio", icon: "home", label: "Inicio" },
  { to: "/f/camara", icon: "camera", label: "Cámara" },
  { to: "/f/mercado", icon: "market", label: "Mercado" },
  { to: "/f/perfil", icon: "user", label: "Perfil" },
];
const RIDER_TABS: Tab[] = [
  { to: "/r/inicio", icon: "home", label: "Inicio" },
  { to: "/r/tablero", icon: "board", label: "Tablero" },
  { to: "/r/ruta", icon: "route", label: "Ruta" },
  { to: "/r/mercado", icon: "market", label: "Mercado" },
  { to: "/r/perfil", icon: "user", label: "Perfil" },
];

/** undefined = still asking, null = signed out. The role always comes from the database. */
export type Auth = { role: string | null; failed?: string } | null | undefined;

function useAuth(): Auth {
  const [auth, setAuth] = useState<Auth>(undefined);
  useEffect(() => {
    const read = async (signedIn: boolean) => {
      if (!signedIn) return setAuth(null);
      // A role we could not read is not "no role": guessing would put a rider on family screens.
      // A re-read that fails (the app coming back to the front with no signal) keeps the one we know.
      try { const role = await loadMyRole(); setAuth({ role }); }
      catch (e) { setAuth((known) => (known && !known.failed ? known : { role: null, failed: humanize(e) })); }
    };
    void sb.auth.getSession().then(({ data }) => read(!!data.session));
    const { data } = sb.auth.onAuthStateChange((event, s) => {
      // Run outside the auth lock: supabase-js deadlocks if this callback awaits a query.
      if (event === "SIGNED_IN" || event === "SIGNED_OUT") setTimeout(() => void read(!!s), 0);
    });
    return () => data.subscription.unsubscribe();
  }, []);
  return auth;
}

export const homeFor = (role?: string | null) => (role === "eco_rider" ? "/r/inicio" : "/f/inicio");

/** Decides who may see which graph, so a family can never land on rider screens, or vice versa. */
function Gate({ auth, want }: { auth: Auth; want: "family" | "rider" }) {
  if (auth === undefined) return <Splash />;
  if (auth === null) return <Navigate to="/" replace />;
  if (auth.role === "operador") return <OperatorNotice />;
  const isRider = auth.role === "eco_rider";
  if (want === "rider" && !isRider) return <Navigate to="/f/inicio" replace />;
  if (want === "family" && isRider) return <Navigate to="/r/inicio" replace />;
  return <Outlet />;
}

function OperatorNotice() {
  return (
    <main className="screen center" style={{ justifyContent: "center" }}>
      <p className="t-card">Esta cuenta es de operador</p>
      <p className="muted">La operación se gestiona desde el panel de Ruma, no desde esta app.</p>
      <button className="btn btn-secondary" onClick={() => void signOut()}>Cerrar sesión</button>
    </main>
  );
}

const HEARTBEAT_MS = 15000;

/** Tracks a courier, not a person's day: reports while the app is in front of the rider only. */
function RiderShell() {
  const toast = useToast();
  const { onShift } = useRider();
  const [denied, setDenied] = useState(false);
  useWakeLock(onShift);
  useEffect(() => {
    if (!("geolocation" in navigator)) return;
    let last = 0;
    const id = navigator.geolocation.watchPosition(
      (p) => {
        setDenied(false);
        const now = Date.now();
        if (now - last < HEARTBEAT_MS) return;
        last = now;
        publishRiderLocation(p.coords.latitude, p.coords.longitude).catch(() => undefined);
      },
      (e) => { if (e.code === e.PERMISSION_DENIED) setDenied(true); },
      { enableHighAccuracy: true, maximumAge: 10000 },
    );
    return () => navigator.geolocation.clearWatch(id);
  }, []);
  useEffect(() => { if (denied) toast("Sin permiso de ubicación: el recolector no podrá ver dónde estás."); }, [denied, toast]);
  return <Outlet />;
}

const withTabs = (tabs: Tab[]) => () => <><Outlet /><TabBar tabs={tabs} /></>;
const FamilyTabs = withTabs(FAMILY_TABS);
const RiderTabs = withTabs(RIDER_TABS);

function Entry({ auth }: { auth: Auth }) {
  const nav = useNavigate();
  useEffect(() => {
    if (auth === undefined) return;
    const t = setTimeout(() => {
      if (auth) nav(homeFor(auth.role), { replace: true });
      else nav(seenOnboarding() ? "/login" : "/bienvenida", { replace: true });
    }, 900);
    return () => clearTimeout(t);
  }, [auth, nav]);
  return <Splash />;
}

export const seenOnboarding = () => { try { return localStorage.getItem("ruma.onboarding") === "1"; } catch { return false; } };

function SignedOutOnly({ auth }: { auth: Auth }) {
  if (auth === undefined) return <Loading />;
  if (auth) return <Navigate to={homeFor(auth.role)} replace />;
  return <Outlet />;
}

export function App() {
  const auth = useAuth();
  // Routes draw this lagging copy so screen changes can run as a View Transition (lib/flow.ts).
  const shown = useTransitionLocation();
  const isRider = !!auth && auth.role === "eco_rider";
  usePush(!!auth && !auth.failed);
  useEffect(() => {
    // Unhandled failures would otherwise be silent on a phone with no console.
    const onError = (e: PromiseRejectionEvent) => console.error(humanize(e.reason));
    window.addEventListener("unhandledrejection", onError);
    return () => window.removeEventListener("unhandledrejection", onError);
  }, []);
  useEffect(() => {
    if (!auth?.failed) return;
    const retry = () => location.reload();
    window.addEventListener("online", retry);
    return () => window.removeEventListener("online", retry);
  }, [auth?.failed]);
  if (auth?.failed) return <div className="app"><main className="screen center" style={{ justifyContent: "center" }}><Failure text={auth.failed} retry={() => location.reload()} /></main></div>;
  return (
    <div className="app" data-role={isRider ? "rider" : "family"}>
      {IS_STAGING && <div className="env-tag">Staging · datos de prueba</div>}
      <ToastProvider>
        <Routes location={shown}>
          <Route path="/" element={<Entry auth={auth} />} />
          <Route element={<SignedOutOnly auth={auth} />}>
            <Route path="/bienvenida" element={<Onboarding />} />
            <Route path="/login" element={<Login />} />
            <Route path="/rol" element={<RoleSelect />} />
            <Route path="/registro" element={<Register />} />
          </Route>

          <Route element={<Gate auth={auth} want="family" />}>
            <Route element={<ScanProvider><Outlet /></ScanProvider>}>
              <Route element={<FamilyTabs />}>
                <Route path="/f/inicio" element={<Home />} />
                <Route path="/f/mercado" element={<Market />} />
                <Route path="/f/perfil" element={<FamilyProfile />} />
              </Route>
              <Route path="/f/camara" element={<Camera />} />
              <Route path="/f/resultado" element={<Result />} />
              <Route path="/f/recojo" element={<PickupForm />} />
              <Route path="/f/seguimiento" element={<Tracking />} />
              <Route path="/f/recojos" element={<MyPickups />} />
              <Route path="/f/wallet" element={<Wallet />} />
              <Route path="/f/historial" element={<FamilyHistory />} />
              <Route path="/f/acopios" element={<Centers />} />
              <Route path="/f/ranking" element={<Rankings />} />
              <Route path="/f/invita" element={<Invite />} />
              <Route path="/f/planes" element={<Plans />} />
              <Route path="/f/chat" element={<Chat />} />
              <Route path="/f/notificaciones" element={<Notifications />} />
              <Route path="/f/editar" element={<EditProfile />} />
            </Route>
          </Route>

          <Route element={<Gate auth={auth} want="rider" />}>
            <Route element={<RiderProvider><RiderShell /></RiderProvider>}>
              <Route element={<RiderTabs />}>
                <Route path="/r/inicio" element={<Dashboard />} />
                <Route path="/r/tablero" element={<Board />} />
                <Route path="/r/ruta" element={<RouteScreen />} />
                <Route path="/r/mercado" element={<Market />} />
                <Route path="/r/perfil" element={<RiderProfile />} />
                <Route path="/r/ranking" element={<RiderRanking />} />
              </Route>
              <Route path="/r/navegar" element={<Navigation />} />
              <Route path="/r/confirmar" element={<Confirm />} />
              <Route path="/r/completado" element={<Completed />} />
              <Route path="/r/entrega" element={<Delivery />} />
              <Route path="/r/ganancias" element={<Earnings />} />
              <Route path="/r/historial" element={<RiderHistory />} />
              <Route path="/r/estadisticas" element={<Stats />} />
              <Route path="/r/notificaciones" element={<Notifications />} />
              <Route path="/r/editar" element={<EditProfile />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </ToastProvider>
    </div>
  );
}
