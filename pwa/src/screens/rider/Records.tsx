import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Bars, Empty, Failure, Icon, Loading, Metric, NavRow, TopBar, useToast } from "../../components/ui";
import { createDelivery, humanize, loadCenters, loadMyAchievements, loadMyDeliveries, loadRiderEarnings, loadRiderHistory, loadUndeliveredPickups, signOut, type Row } from "../../lib/api";
import { ago, dayGroup, initials, kg, sameDay, soles, STATUS, useAsync, VEHICLE } from "../../lib/util";
import { Leaderboard } from "../family/Family";
import { useRider } from "./Work";

export const RiderRanking = () => <Leaderboard role="eco_rider" title="Ranking de riders" />;

const DELIVERY_STATUS: Record<string, string> = { en_camino: "En camino al acopio", recibida: "Recibida · falta el pesaje", pesada: "Pesada · esperando liquidación", liquidada: "Liquidada · te pagaron" };

/* ---------- Deliver to the collection center ---------- */

export function Delivery() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(async () => {
    const [load, centers, past] = await Promise.all([loadUndeliveredPickups(), loadCenters(), loadMyDeliveries()]);
    return { load, centers, past };
  });
  const [off, setOff] = useState<Set<string>>(new Set());
  const [center, setCenter] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const chosen = (data?.load ?? []).filter((p) => !off.has(p.id));
  const toggle = (id: string) => { const n = new Set(off); if (n.has(id)) n.delete(id); else n.add(id); setOff(n); };
  const send = async () => {
    if (!center) return toast("Elige a qué acopio vas.");
    if (!chosen.length) return toast("Incluye al menos un recojo.");
    setBusy(true);
    try { await createDelivery(center, chosen.map((p) => p.id)); toast("Entrega abierta: sal hacia el acopio."); setCenter(null); await reload(true); }
    catch (e) { toast(humanize(e)); } finally { setBusy(false); }
  };
  return (
    <main className="screen">
      <TopBar title="Entregar en acopio" />
      {loading ? <Loading text="Cargando tu carga…" /> : error ? <Failure text={error} retry={() => void reload()} /> : <>
        <p className="t-over">Recojos que llevas</p>
        {data?.load.length ? <div className="glass list">
          {data.load.map((p) => {
            const on = !off.has(p.id);
            return <button key={p.id} role="checkbox" aria-checked={on} aria-label={`${on ? "Quitar de" : "Incluir en"} la entrega: ${p.generator?.display_name}`} onClick={() => toggle(p.id)}>
              <span className="badge" style={on ? { background: "var(--accent)", color: "#fff", border: 0 } : undefined}>{on && <Icon name="check" size={18} />}</span>
              <span className="min0"><span className="muted t-small" style={{ display: "block" }}>{p.code}</span><span className="t-row" style={{ display: "block" }}>{p.generator?.display_name ?? "Vecino"}</span><span className="muted t-small">{p.address?.address_line}</span></span>
              <span className="money" style={{ flex: "none" }}>{soles(p.rider_earnings)}</span>
            </button>;
          })}
        </div> : <Empty title="No llevas recojos sin entregar" text="Cuando cierres un recojo aparecerá aquí para llevarlo al acopio." icon="box" />}
        {!!data?.load.length && <>
          <p className="t-over">¿A qué acopio vas?</p>
          {data.centers.length ? <div className="stack" role="radiogroup" aria-label="Acopio">
            {data.centers.map((c: Row) => <button key={c.id} role="radio" aria-checked={center === c.id} className="glass choice" onClick={() => setCenter(c.id)}>
              <span className="min0"><span className="t-row" style={{ display: "block" }}>{c.name}</span><span className="muted t-small" style={{ display: "block" }}>{c.address_line}</span></span>
              <span className={`chip ${c.is_open ? "chip-accent" : "chip-off"}`}>{c.is_open ? "Abierto" : "Cerrado"}</span>
              <span className="radio" />
            </button>)}
          </div> : <p className="muted">No hay acopios disponibles</p>}
          <button className="btn btn-primary" disabled={busy} onClick={() => void send()}>{busy ? "Abriendo la entrega…" : "Salir hacia el acopio"} <Icon name="arrow" /></button>
        </>}
        {!!data?.past.length && <>
          <p className="t-over">Entregas anteriores</p>
          <div className="glass list">{data.past.map((d: Row) => <div key={d.id}>
            <span className="min0"><span className="t-row" style={{ display: "block" }}>{d.code ?? "Entrega"}</span><span className="muted t-small">{DELIVERY_STATUS[d.status] ?? d.status}{d.center?.name ? ` · ${d.center.name}` : ""}</span></span>
            {d.rider_amount > 0 && <span className="money" style={{ flex: "none" }}>{soles(d.rider_amount)}</span>}
          </div>)}</div>
        </>}
      </>}
    </main>
  );
}

/* ---------- Earnings ---------- */

const DAY = ["Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"];
const lastWeek = () => Array.from({ length: 7 }, (_, i) => new Date(Date.now() - (6 - i) * 864e5));

export function Earnings() {
  const { profile } = useRider();
  const { data, loading, error, reload } = useAsync(() => loadRiderEarnings(300));
  const now = new Date();
  const month = (data ?? []).filter((e) => { const d = new Date(e.created_at); return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear(); }).reduce((s, e) => s + e.amount, 0);
  const days = lastWeek().map((d, i) => {
    const sum = (data ?? []).filter((e) => sameDay(new Date(e.created_at), d)).reduce((s, e) => s + e.amount, 0);
    return { label: i === 6 ? "Hoy" : i === 5 ? "Ayer" : DAY[d.getDay()], value: sum, text: sum.toFixed(2), today: i === 6 };
  });
  const goal = profile?.monthly_goal ?? 0;
  const balance = profile?.earnings_balance ?? 0;
  return (
    <main className="screen">
      <TopBar title="Mis Ganancias" />
      {loading ? <Loading text="Cargando tus ganancias…" /> : error ? <Failure text={error} retry={() => void reload()} /> : <>
        <section className="hero">
          <p className="t-over">Ganado este mes</p>
          <p className="t-hero">{soles(month)}</p>
          {goal > 0 && <div className="hero-rule stack"><div className="track"><i style={{ width: `${Math.min(100, (month / goal) * 100)}%` }} /></div>
            <p className="soft t-small">{month >= goal ? "¡Llegaste a tu meta!" : `Faltan ${soles(goal - month)} para tu meta de ${soles(goal)}`}</p></div>}
        </section>
        <section className="glass pad stack" style={{ gap: 12 }}>
          <div className="between"><p className="t-over">Últimos 7 días</p><p className="t-row">{soles(days.reduce((s, d) => s + d.value, 0))}</p></div>
          <Bars data={days} />
        </section>
        <section className="glass pad stack">
          <p className="t-over">Disponible para retiro</p>
          <p className="t-title">{soles(balance)}</p>
          <button className="btn btn-neutral" disabled>Retirar a Yape</button>
          <p className="muted t-small">Los retiros se habilitarán pronto. Tu saldo queda guardado.</p>
        </section>
        <p className="t-over">Movimientos</p>
        {data?.length ? <div className="glass list">{data.slice(0, 20).map((e, i) => <div key={i}><span className="min0"><span className="t-row" style={{ display: "block" }}>{e.description ?? "Recojo"}</span><span className="muted t-small">{ago(e.created_at)}</span></span><span className="money">+{soles(e.amount)}</span></div>)}</div> : <p className="muted">Aún no tienes ganancias.</p>}
      </>}
    </main>
  );
}

/* ---------- History ---------- */

export function RiderHistory() {
  const [filter, setFilter] = useState("Todos");
  const { data, loading, error, reload } = useAsync(() => loadRiderHistory(60));
  const shown = useMemo(() => {
    const now = Date.now();
    const limit = { Todos: Infinity, Hoy: -1, Semana: 7 * 864e5, Mes: 30 * 864e5 }[filter] ?? Infinity;
    return (data ?? []).filter((p) => {
      const at = new Date(p.completed_at ?? p.created_at);
      return limit === -1 ? sameDay(at, new Date()) : now - at.getTime() <= limit;
    });
  }, [data, filter]);
  const groups = [...new Set(shown.map((p) => dayGroup(p.completed_at ?? p.created_at)))];
  return (
    <main className="screen">
      <TopBar title="Mi Historial" />
      <div className="chips">{["Todos", "Hoy", "Semana", "Mes"].map((f) => <button key={f} className="glass filter" aria-pressed={filter === f} onClick={() => setFilter(f)}>{f}</button>)}</div>
      {loading ? <Loading text="Cargando tu historial…" /> : error ? <Failure text={error} retry={() => void reload()} /> : !shown.length ? <Empty title={data?.length ? "Sin recojos en este periodo" : "Todavía no has completado recojos"} text={data?.length ? "Prueba con otro filtro de fecha." : "Toma una solicitud del tablero y aparecerá aquí."} icon="history" /> : groups.map((g) => (
        <section className="stack" key={g}>
          <p className="t-over">{g}</p>
          <div className="glass list">{shown.filter((p) => dayGroup(p.completed_at ?? p.created_at) === g).map((p) => <div key={p.id}>
            <span className="badge"><Icon name="recycle" /></span>
            <span className="min0"><span className="t-row" style={{ display: "block" }}>{p.generator?.display_name ?? "Vecino"}</span><span className="muted t-small" style={{ display: "block" }}>{p.address?.address_line}</span>
              <span className="row" style={{ gap: 6 }}><span className={`chip ${p.status === "completed" ? "chip-accent" : "chip-off"}`}>{STATUS[p.status] ?? p.status}</span><span className="muted t-small">{ago(p.completed_at ?? p.created_at)}</span></span></span>
            {p.status === "completed" && <span className="money" style={{ flex: "none" }}>+{soles(p.rider_earnings)}</span>}
          </div>)}</div>
        </section>
      ))}
    </main>
  );
}

/* ---------- Stats ---------- */

export function Stats() {
  const { profile } = useRider();
  const { data, loading, error, reload } = useAsync(async () => {
    const [history, badges] = await Promise.all([loadRiderHistory(100), loadMyAchievements()]);
    return { history, badges };
  });
  const days = lastWeek().map((d, i) => ({
    label: DAY[d.getDay()].slice(0, 1), today: i === 6,
    value: (data?.history ?? []).filter((p) => p.status === "completed" && sameDay(new Date(p.completed_at ?? p.created_at), d)).length,
  }));
  return (
    <main className="screen">
      <TopBar title="Mis Estadísticas" />
      {loading ? <Loading text="Cargando tus estadísticas…" /> : error ? <Failure text={error} retry={() => void reload()} /> : <>
        <section className="hero"><div className="grid2">
          <div><p className="t-title">{kg(profile?.total_recycled_kg)}</p><p className="soft t-small">Reciclado</p></div>
          <div><p className="t-title">{kg(profile?.co2_avoided_kg)}</p><p className="soft t-small">CO₂ evitado</p></div>
        </div></section>
        <section className="glass pad stack" style={{ gap: 12 }}><p className="t-row">Recojos por día · última semana</p><Bars data={days} /></section>
        <p className="t-over">Insignias</p>
        {data?.badges.length ? <div className="glass pad grid3 center">{data.badges.map((b: Row, i: number) => <div className="stack" style={{ alignItems: "center" }} key={i} aria-label={`Insignia ${b.unlocked ? "conseguida" : "bloqueada"}: ${b.achievements?.name}`}>
          <span className="badge" style={{ borderRadius: "50%", width: 52, height: 52, ...(b.unlocked ? { border: "2px solid #e0a41b" } : { opacity: 0.55 }) }}><Icon name={b.unlocked ? "trophy" : "lock"} /></span>
          <span className={`t-small ${b.unlocked ? "" : "muted"}`}>{b.achievements?.name}</span>
        </div>)}</div> : <Empty title="Aún sin insignias" text="Completa recojos para desbloquear tus primeros logros." icon="trophy" />}
      </>}
    </main>
  );
}

/* ---------- Profile ---------- */

export function RiderProfile() {
  const nav = useNavigate();
  const { profile: p, name, loading, error, refresh } = useRider();
  if (loading) return <main className="screen with-nav"><Loading text="Cargando tu perfil…" /></main>;
  if (error) return <main className="screen with-nav"><Failure text={error} retry={() => void refresh()} /></main>;
  return (
    <main className="screen with-nav">
      <TopBar title="Mi perfil" back={false} />
      <section className="hero">
        <div className="row">
          <span className="chip chip-hero" style={{ width: 56, height: 56, justifyContent: "center", fontSize: 20 }}>{initials(name)}</span>
          <div className="min0"><p className="t-card">{name ?? "Eco-Rider"}</p><p className="soft t-small">Eco-Rider</p></div>
        </div>
        <div className="row hero-rule" style={{ flexWrap: "wrap", gap: 8 }}>
          {p?.vehicle_type && <span className="chip chip-hero">{VEHICLE[p.vehicle_type] ?? p.vehicle_type}</span>}
          {p?.rider_code && <span className="chip chip-hero">{p.rider_code}</span>}
          {p?.rating && <span className="chip chip-hero">★ {Number(p.rating).toFixed(1)}</span>}
        </div>
      </section>
      <p className="t-over">Tu trabajo hasta hoy</p>
      <div className="grid3"><Metric value={String(p?.total_collections ?? 0)} label="Recojos" /><Metric value={kg(p?.total_recycled_kg)} label="Reciclado" /><Metric value={kg(p?.co2_avoided_kg)} label="CO₂ evitado" /></div>
      <p className="t-over">Tu cuenta</p>
      <div className="glass list">
        <NavRow icon="wallet" title="Mis ganancias" sub="Lo cobrado y lo que falta por liquidar" onClick={() => nav("/r/ganancias")} />
        <NavRow icon="chart" title="Mis estadísticas" sub="Kilos, CO₂ evitado y tu ritmo por semana" onClick={() => nav("/r/estadisticas")} />
        <NavRow icon="trophy" title="Ranking" sub="Cómo vas frente al resto de Eco-Riders" onClick={() => nav("/r/ranking")} />
        <NavRow icon="history" title="Historial de recojos" sub="Cada parada que cerraste" onClick={() => nav("/r/historial")} />
        <NavRow icon="edit" title="Editar perfil" sub="Tu nombre y teléfono" onClick={() => nav("/r/editar")} />
      </div>
      <button className="btn btn-danger" onClick={() => void signOut()}><Icon name="logout" /> Cerrar sesión</button>
    </main>
  );
}
