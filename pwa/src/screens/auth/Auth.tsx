import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Field, Icon, Logo, LogoPlate, TopBar } from "../../components/ui";
import { humanize, pendingAddress, setPendingAddress, signIn, signUp } from "../../lib/api";
import { here } from "../../lib/util";

export function Splash() {
  return (
    <main className="screen center" style={{ justifyContent: "center", alignItems: "center" }} aria-label="Cargando Ruma">
      <div className="grow" />
      <div className="glass" style={{ width: 148, height: 148, borderRadius: 36, display: "grid", placeItems: "center" }}><Logo size={112} /></div>
      <h1 className="t-hero">ruma</h1>
      <p className="muted">Tu agente de reciclaje inteligente</p>
      <div className="grow" />
      <div className="spinner" />
    </main>
  );
}

const finishOnboarding = () => { try { localStorage.setItem("ruma.onboarding", "1"); } catch { /* private mode */ } };

export function Onboarding() {
  const nav = useNavigate();
  const [page, setPage] = useState(0);
  const [address, setAddress] = useState(pendingAddress()?.line ?? "");
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [locating, setLocating] = useState<"idle" | "busy" | "found" | "denied">("idle");

  const finish = () => {
    finishOnboarding();
    setPendingAddress(address.trim() ? { line: address.trim(), lat: coords?.lat ?? null, lng: coords?.lng ?? null } : null);
    nav("/rol", { replace: true });
  };
  const locate = async () => {
    setLocating("busy");
    const fix = await here();
    if (!fix) return setLocating("denied");
    setCoords(fix);
    setLocating("found");
    if (!address.trim()) setAddress(`Mi ubicación (${fix.lat.toFixed(4)}, ${fix.lng.toFixed(4)})`);
  };
  const help = { idle: "Toca el ícono para usar tu ubicación actual.", busy: "Buscando tu ubicación…", found: "Ubicación detectada. Escribe tu dirección para que el Eco-Rider te encuentre.", denied: "Sin permiso de ubicación: escribe tu dirección." }[locating];

  return (
    <main className="screen">
      <div className="between" style={{ justifyContent: "flex-end" }}><button className="btn-text" style={{ padding: "0 8px" }} onClick={finish}>Saltar</button></div>

      {page === 0 && <>
        <div className="glass grow" style={{ display: "grid", placeItems: "center", borderRadius: 24, position: "relative", minHeight: 240 }}>
          <Logo size={190} />
          <span className="chip chip-money" style={{ position: "absolute", top: 16, right: 16 }}>+ 240 pts</span>
        </div>
        <h1 className="t-title">Hola, soy Ruma</h1>
        <p className="muted">Tu agente de reciclaje inteligente. Te ayudo a reciclar en casa y ganar puntos por cada residuo.</p>
      </>}

      {page === 1 && <>
        <h1 className="t-title">Así de fácil funciona</h1>
        <div className="stack" style={{ gap: 20 }}>
          {[["1", "Toma una foto", "Captura tus residuos en casa", "camera"], ["2", "Detectamos con IA", "Identificamos el material al instante", "recycle"], ["3", "Ganas puntos", "Canjéalos por dinero o premios", "trophy"]].map(([n, t, s, i]) => (
            <div className="row" key={n}>
              <span className="t-title" style={{ width: 24, textAlign: "center" }}>{n}</span>
              <div className="min0"><div className="t-row">{t}</div><div className="muted t-small">{s}</div></div>
              <span className="badge"><Icon name={i} /></span>
            </div>
          ))}
        </div>
        <div className="glass pad">
          <div className="between"><span className="t-row">10 botellas PET</span><span className="chip chip-money">+ 100 pts</span></div>
          <div className="muted t-small">Equivale a S/0.10</div>
        </div>
        <div className="grow" />
      </>}

      {page === 2 && <>
        <h1 className="t-title">¿Dónde vives?</h1>
        <p className="muted">Para enviarte al Eco-Rider más cercano y ubicarte en el ranking de tu cuadra.</p>
        <Field label="Tu dirección" placeholder="Jr. Las Flores 123, SJL" autoComplete="street-address" value={address} onChange={(e) => setAddress(e.target.value)}
          right={<button type="button" className="ghost-btn accent" aria-label="Usar mi ubicación" onClick={() => void locate()}><Icon name="locate" /></button>} helper={help} />
        <p className="muted t-small">Usaremos tu ubicación solo para el recojo y el ranking vecinal. Puedes cambiarla cuando quieras.</p>
        <div className="grow" />
      </>}

      <div className="steps" aria-label={`Paso ${page + 1} de 3`}>{[0, 1, 2].map((i) => <i key={i} className={i === page ? "on" : ""} />)}</div>
      <button className="btn btn-primary" onClick={() => (page < 2 ? setPage(page + 1) : finish())}>
        {["Comenzar", "Siguiente", "Continuar"][page]} <Icon name={page < 2 ? "arrow" : "check"} />
      </button>
    </main>
  );
}

export function RoleSelect() {
  const nav = useNavigate();
  const [role, setRole] = useState<"generador" | "eco_rider">("generador");
  const options = [
    { id: "generador" as const, icon: "home", title: "Eco-Hero", text: "Reciclo donde vivo y gano puntos" },
    { id: "eco_rider" as const, icon: "bike", title: "Eco-Rider", text: "Recojo reciclaje y gano dinero" },
  ];
  return (
    <main className="screen">
      <div className="grow" />
      <LogoPlate plate={96} />
      <div className="center"><h1 className="t-title">Bienvenido a Ruma</h1><p className="muted">Elige tu rol para crear tu cuenta</p></div>
      <p className="t-over">Tu rol</p>
      <div className="stack" role="radiogroup" aria-label="Tu rol">
        {options.map((o) => (
          <button key={o.id} role="radio" aria-checked={role === o.id} className="glass choice" onClick={() => setRole(o.id)}>
            <span className="badge"><Icon name={o.icon} /></span>
            <span className="min0"><span className="t-row" style={{ display: "block" }}>{o.title}</span><span className="muted t-small">{o.text}</span></span>
            <span className="radio" />
          </button>
        ))}
      </div>
      <div className="grow" />
      <button className="btn btn-primary" onClick={() => nav("/registro", { state: { role } })}>Continuar</button>
      <p className="center muted">Ya tengo cuenta <Link className="accent" style={{ fontWeight: 800, padding: 12 }} to="/login">Iniciar sesión</Link></p>
    </main>
  );
}

export function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // The auth gate moves the person to their role's home as soon as the session lands.
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true); setError(null);
    try { await signIn(email, password); }
    catch (err) { setError(humanize(err)); setBusy(false); }
  };
  return (
    <main className="screen">
      <div style={{ height: 24 }} />
      <LogoPlate />
      <div className="center"><h1 className="t-title">Bienvenido de vuelta</h1><p className="muted">Entra y sigue reciclando</p></div>
      <form className="stack" style={{ gap: 16 }} onSubmit={(e) => void submit(e)}>
        <div className="glass pad stack" style={{ gap: 16 }}>
          <Field label="Correo" type="email" required autoComplete="email" inputMode="email" placeholder="tucorreo@ejemplo.com" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Field label="Contraseña" required autoComplete="current-password" type={show ? "text" : "password"} placeholder="Tu contraseña" value={password} onChange={(e) => setPassword(e.target.value)}
            right={<button type="button" className="ghost-btn" aria-label={show ? "Ocultar contraseña" : "Mostrar contraseña"} onClick={() => setShow(!show)}><Icon name="eye" /></button>} />
        </div>
        {error && <p className="danger" role="alert">{error}</p>}
        <button className="btn btn-primary" disabled={busy}>{busy ? "Ingresando…" : "Iniciar sesión"}</button>
      </form>
      <p className="center muted">¿No tienes cuenta? <Link className="accent" style={{ fontWeight: 800, padding: 12 }} to="/rol">Regístrate</Link></p>
    </main>
  );
}

export function Register() {
  const nav = useNavigate();
  const role = (useLocation().state as { role?: string } | null)?.role ?? "generador";
  const isRider = role === "eco_rider";
  const seed = pendingAddress();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [address, setAddress] = useState(seed?.line ?? "");
  const [coords, setCoords] = useState<{ lat: number | null; lng: number | null }>({ lat: seed?.lat ?? null, lng: seed?.lng ?? null });
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmEmail, setConfirmEmail] = useState(false);

  const locate = async () => {
    const fix = await here();
    if (fix) setCoords(fix);
    else setError("Sin permiso de ubicación: escribe tu dirección.");
  };
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      const signedIn = await signUp(email, password, role, name, isRider || !address.trim() ? null : { line: address.trim(), lat: coords.lat, lng: coords.lng });
      if (!signedIn) { setConfirmEmail(true); setBusy(false); }
    } catch (err) { setError(humanize(err)); setBusy(false); }
  };

  if (confirmEmail) return (
    <main className="screen center" style={{ justifyContent: "center" }}>
      <LogoPlate />
      <h1 className="t-title">Revisa tu correo</h1>
      <p className="muted">Te enviamos un enlace a {email}. Confírmalo y luego inicia sesión.</p>
      <button className="btn btn-primary" onClick={() => nav("/login", { replace: true })}>Iniciar sesión</button>
    </main>
  );

  return (
    <main className="screen">
      <TopBar title="" />
      <div><h1 className="t-title">{isRider ? "Crea tu cuenta de Eco-Rider" : "Crea tu cuenta"}</h1>
        <p className="muted">{isRider ? "Recolecta reciclaje y gana por cada ruta." : "Recicla desde casa y gana Eco-Puntos por cada residuo."}</p></div>
      <form className="stack" style={{ gap: 16 }} onSubmit={(e) => void submit(e)}>
        <div className="glass pad stack" style={{ gap: 16 }}>
          <Field label="Nombre" required autoComplete="name" placeholder={isRider ? "Carlos Ramos" : "Fam. Nureña"} value={name} onChange={(e) => setName(e.target.value)} />
          <Field label="Correo" type="email" required autoComplete="email" inputMode="email" placeholder="tucorreo@ejemplo.com" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Field label="Contraseña" required minLength={6} autoComplete="new-password" type={show ? "text" : "password"} placeholder="Mínimo 6 caracteres" value={password} onChange={(e) => setPassword(e.target.value)}
            right={<button type="button" className="ghost-btn" aria-label={show ? "Ocultar contraseña" : "Mostrar contraseña"} onClick={() => setShow(!show)}><Icon name="eye" /></button>} />
          {!isRider && <Field label="Dirección de recojo" required autoComplete="street-address" placeholder="Jr. Las Flores 123, SJL" value={address} onChange={(e) => setAddress(e.target.value)}
            helper={coords.lat != null ? "Ubicación detectada. Es donde el Eco-Rider recogerá tu reciclaje." : "Es donde el Eco-Rider recogerá tu reciclaje. Puedes cambiarla después."}
            right={<button type="button" className="ghost-btn accent" aria-label="Usar mi ubicación" onClick={() => void locate()}><Icon name="locate" /></button>} />}
        </div>
        {error && <p className="danger" role="alert">{error}</p>}
        <button className="btn btn-primary" disabled={busy}>{busy ? "Creando cuenta…" : "Crear cuenta"}</button>
      </form>
      <p className="center muted">¿Ya tienes cuenta? <Link className="accent" style={{ fontWeight: 800, padding: 12 }} to="/login">Iniciar sesión</Link></p>
    </main>
  );
}
