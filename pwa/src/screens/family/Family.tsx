import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Empty, Failure, Icon, Loading, Metric, NavRow, RumaMap, TopBar, useToast } from "../../components/ui";
import { ACTIVE, createRedemption, humanize, loadChallenges, loadCenters, loadDetections, loadGeneratorProfile, loadLeaderboardEntries, loadMaterialDemand, loadMyAchievements, loadNotifications, loadOpenPeriod, loadPickups, loadPrices, loadRankingRewards, loadRedemptionOptions, loadRedemptions, loadTrackedPickup, myId, signOut, loadPrimaryAddress, type Row } from "../../lib/api";
import { ago, dayGroup, initials, int, kg, ptsToSoles, soles, STATUS, useAsync } from "../../lib/util";

/* ---------- Home ---------- */

export function Home() {
  const nav = useNavigate();
  const { data, loading, error, reload } = useAsync(async () => {
    const [profile, notes, pickup, period, challenges, demand] = await Promise.all([
      loadGeneratorProfile(), loadNotifications().catch(() => []), loadTrackedPickup().catch(() => null),
      loadOpenPeriod("generador").catch(() => null), loadChallenges().catch(() => []), loadMaterialDemand().catch(() => []),
    ]);
    const entries = period ? await loadLeaderboardEntries(period.id).catch(() => []) : [];
    return { profile, notes, pickup, entries, challenges, demand };
  });
  if (loading) return <main className="screen with-nav"><Loading /></main>;
  if (error || !data) return <main className="screen with-nav"><Failure text={error ?? "No pudimos cargar tu inicio."} retry={() => void reload()} /></main>;

  const { profile: p, pickup, entries, challenges, demand } = data;
  const name = p?.profiles?.display_name ?? p?.profiles?.full_name;
  const live = pickup && ACTIVE.includes(pickup.status) ? pickup : null;
  const rank = entries.find((e) => e.user_id === myId())?.rank;
  const challenge = challenges[0];
  const progress = challenge?.user_challenges?.[0]?.progress ?? 0;
  const challengePct = challenge?.target_value ? Math.min(100, Math.round((progress / challenge.target_value) * 100)) : null;
  const best = [...demand].filter((d) => (d.mejor_precio ?? 0) > 0).sort((a, b) => b.mejor_precio - a.mejor_precio)[0];
  const unread = data.notes.some((n) => !n.is_read);

  return (
    <main className="screen with-nav">
      <header className="between">
        <div className="min0"><h1 className="t-title">{name ? `Hola, ${name}` : "Hola"}</h1><p className="muted">Sigue así, cada residuo cuenta.</p></div>
        <button className="icon-btn" aria-label={unread ? "Ver notificaciones sin leer" : "Ver notificaciones"} onClick={() => nav("/f/notificaciones")}><Icon name="bell" />{unread && <i className="dot" />}</button>
      </header>
      <button className="hero" style={{ textAlign: "left" }} onClick={() => nav("/f/wallet")} aria-label="Ir a Mi Wallet">
        <span className="t-over">Eco-puntos</span>
        <span className="t-hero">{int(p?.points_balance)}</span>
        <span className="soft">{p?.points_pending ? `+${int(p.points_pending)} por confirmar` : "Tu saldo disponible"}</span>
        <span className="between hero-rule" style={{ width: "100%" }}>
          {p?.current_streak_days ? <span className="chip chip-hero">Día {p.current_streak_days} · Racha activa</span> : <span className="soft">Empieza tu racha hoy</span>}
          <span style={{ fontWeight: 800 }}>= {ptsToSoles(p?.points_balance ?? 0)}</span>
        </span>
      </button>
      <button className="btn btn-primary" onClick={() => nav("/f/camara")}><Icon name="camera" /> Escanear residuos</button>
      {live && <div className="glass list"><NavRow icon="bike" title={live.rider?.display_name ? `${live.rider.display_name} va en camino` : STATUS[live.status]} sub={live.eta_minutes ? `Llega en ~${live.eta_minutes} min` : STATUS[live.status]} onClick={() => nav(`/f/seguimiento?id=${live.id}`)} right={<span className="chip chip-accent"><i className="pulse" /> En vivo</span>} /></div>}
      <h2 className="t-card">Recicla</h2>
      <div className="glass list">
        <NavRow icon="truck" title="Pedir recojo" sub="Un Eco-Rider pasa por tus residuos" onClick={() => nav("/f/recojo")} />
        <NavRow icon="pin" title="Puntos de acopio cerca" sub="Mira dónde reciclar y los precios" onClick={() => nav("/f/acopios")} />
      </div>
      <h2 className="t-card">Tu impacto</h2>
      <div className="grid2">
        <button style={{ textAlign: "left" }} onClick={() => nav("/f/ranking")}><Metric value={rank ? `#${rank}` : "—"} label="Tu cuadra" /></button>
        {challenge && challengePct != null ? <Metric value={`${challengePct}%`} label={challenge.name} /> : <Metric value={kg(p?.recycled_kg)} label="Reciclado" />}
        <Metric value={kg(p?.co2_avoided_kg)} label="CO₂ evitado" />
        <button style={{ textAlign: "left" }} onClick={() => nav("/f/seguimiento")}><Metric value={live?.window_label?.split(" ")[0] ?? "—"} label="Mis recojos" /></button>
      </div>
      <button className="glass pad stack" style={{ textAlign: "left" }} onClick={() => nav("/f/chat")}>
        <span className="t-over accent">Ruma te recomienda</span>
        <span>{best ? `Hoy ${best.name} se paga mejor: ${soles(best.mejor_precio)}/kg. ¿Qué juntas esta semana?` : "Pregúntame qué va en cada tacho y cuánto vale tu material."}</span>
        <span className="accent t-small" style={{ fontWeight: 800 }}>Pregúntale lo que quieras ›</span>
      </button>
    </main>
  );
}

/* ---------- Wallet ---------- */

export function Wallet() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(async () => {
    const [profile, options, history] = await Promise.all([loadGeneratorProfile(), loadRedemptionOptions(), loadRedemptions()]);
    return { profile, options, history };
  });
  const [busy, setBusy] = useState<string | null>(null);
  if (loading) return <main className="screen"><TopBar title="Mi Wallet" /><Loading /></main>;
  if (error || !data) return <main className="screen"><TopBar title="Mi Wallet" /><Failure text={error ?? "No pudimos cargar tu wallet."} retry={() => void reload()} /></main>;

  const balance: number = data.profile?.points_balance ?? 0;
  const value = (o: Row) => (o.kind === "cash" && o.points_per_unit > 0 ? (o.min_points / o.points_per_unit) * o.unit_value : 0);
  const next = [...data.options].filter((o) => o.min_points > balance).sort((a, b) => a.min_points - b.min_points)[0];
  const redeem = async (o: Row) => {
    if (!window.confirm(`¿Canjear ${int(o.min_points)} pts por ${o.name}?`)) return;
    setBusy(o.id);
    try { await createRedemption(o.id, o.kind, o.min_points, value(o)); toast("¡Canje realizado con éxito!"); await reload(true); }
    catch (e) { toast(humanize(e)); } finally { setBusy(null); }
  };

  return (
    <main className="screen">
      <TopBar title="Mi Wallet" />
      <section className="hero">
        <p className="t-over">Saldo disponible</p>
        <div className="between"><p className="t-hero">{int(balance)}</p><p className="t-card">= {ptsToSoles(balance)}</p></div>
        <p className="soft">Eco-Puntos{data.profile?.points_pending ? ` · +${int(data.profile.points_pending)} por confirmar` : ""}</p>
        {next && <div className="hero-rule stack"><div className="track"><i style={{ width: `${Math.min(100, (balance / next.min_points) * 100)}%` }} /></div><p className="soft t-small">Faltan {int(next.min_points - balance)} pts para {next.name}</p></div>}
      </section>
      <p className="t-over">Opciones de canje</p>
      {data.options.length ? <div className="glass list">
        {data.options.map((o, i) => {
          const can = balance >= o.min_points;
          return <div key={o.id}>
            <span className="badge"><Icon name={o.kind === "cash" ? "wallet" : "box"} /></span>
            <span className="min0"><span className="t-row" style={{ display: "block" }}>{o.name}</span>
              <span className="muted t-small" style={{ display: "block" }}>{[`${int(o.min_points)} pts`, o.partner].filter(Boolean).join(" · ")}</span>
              {i === 0 && can && <span className="chip chip-money">Sugerido</span>}</span>
            {can ? <button className={`btn btn-small ${i === 0 ? "btn-primary" : "btn-secondary"}`} disabled={busy === o.id} onClick={() => void redeem(o)}>{busy === o.id ? "Procesando…" : "Canjear"}</button>
              : <span className="muted t-small" style={{ flex: "none" }}>Saldo insuficiente</span>}
          </div>;
        })}
      </div> : <Empty title="Aún no hay opciones de canje" icon="wallet" />}
      <p className="t-over">Historial de canjes</p>
      {data.history.length ? <div className="glass list">
        {data.history.map((h, i) => <div key={i}>
          <span className="min0"><span className="t-row" style={{ display: "block" }}>{h.redemption_options?.name ?? h.kind}{h.monetary_value > 0 ? ` · ${soles(h.monetary_value)}` : ""}</span><span className="muted t-small">{ago(h.created_at)}</span></span>
          <span className="money">− {int(h.points_spent)} pts</span>
        </div>)}
      </div> : <p className="muted">Aún no tienes canjes</p>}
    </main>
  );
}

/* ---------- History ---------- */

export function FamilyHistory() {
  const nav = useNavigate();
  const [filter, setFilter] = useState("Todo");
  const { data, loading, error, reload } = useAsync(async () => {
    const [pickups, detections, redemptions] = await Promise.all([loadPickups(30), loadDetections(30), loadRedemptions()]);
    return [
      ...pickups.map((p) => ({ type: "Recojos", at: p.created_at as string, id: p.id as string, title: `Recojo · ${(p.pickup_items ?? []).map((i: Row) => `${i.confirmed_quantity ?? i.reported_quantity} ${i.materials?.code}`).join(" + ") || p.code}`, sub: [p.window_label, p.rider?.display_name].filter(Boolean).join(" · "), status: STATUS[p.status] ?? p.status, right: p.generator_points_awarded ? `+${int(p.generator_points_awarded)} pts` : "", icon: "truck", live: ACTIVE.includes(p.status) })),
      ...detections.map((d) => ({ type: "Detecciones", at: d.detected_at as string, id: "", title: `Foto · ${(d.detection_items ?? []).map((i: Row) => `${i.materials?.code} x${i.quantity}`).join(" · ") || "Sin ítems"}`, sub: "Detección", status: "", right: `+${int(d.total_points)} pts estimados`, icon: "camera", live: false })),
      ...redemptions.map((r) => ({ type: "Canjes", at: r.created_at as string, id: "", title: `${r.redemption_options?.name ?? "Canje"}${r.monetary_value > 0 ? ` · ${soles(r.monetary_value)}` : ""}`, sub: ago(r.created_at), status: "", right: `− ${int(r.points_spent)} pts`, icon: "wallet", live: false })),
    ].sort((a, b) => b.at.localeCompare(a.at));
  });
  const shown = (data ?? []).filter((r) => filter === "Todo" || r.type === filter);
  const groups = [...new Set(shown.map((r) => dayGroup(r.at)))];
  return (
    <main className="screen">
      <TopBar title="Mi Historial" />
      <div className="chips">{["Todo", "Recojos", "Detecciones", "Canjes"].map((f) => <button key={f} className="glass filter" aria-pressed={filter === f} onClick={() => setFilter(f)}>{f}</button>)}</div>
      {loading ? <Loading text="Cargando tu historial…" /> : error ? <Failure text={error} retry={() => void reload()} /> : !shown.length ? <Empty title="Nada por aquí todavía" text={filter === "Recojos" ? "Cuando pidas un recojo lo verás aquí, con su estado." : "Escanea tus residuos con la cámara para empezar."} icon="history" /> : groups.map((g) => (
        <section className="stack" key={g}>
          <p className="t-over">{g}</p>
          <div className="glass list">{shown.filter((r) => dayGroup(r.at) === g).map((r, i) => (
            <NavRow key={i} icon={r.icon} title={r.title} sub={r.sub} onClick={r.type === "Recojos" ? () => nav(`/f/seguimiento?id=${r.id}`) : undefined}
              right={<span style={{ textAlign: "right", flex: "none", maxWidth: "42%" }}>{r.status && <span className={`chip ${r.live ? "chip-accent" : ""}`} style={{ whiteSpace: "normal" }}>{r.status}</span>}{r.right && <span className="money t-small" style={{ display: "block" }}>{r.right}</span>}</span>} />
          ))}</div>
        </section>
      ))}
    </main>
  );
}

/* ---------- Collection centers ---------- */

export function Centers() {
  const { data, loading, error, reload } = useAsync(async () => {
    const [centers, prices] = await Promise.all([loadCenters(), loadPrices()]);
    return { centers, prices };
  });
  const pins = (data?.centers ?? []).filter((c) => c.latitude != null).map((c) => ({ lat: c.latitude, lng: c.longitude }));
  return (
    <main className="screen">
      <TopBar title="Puntos de Acopio" />
      {loading ? <Loading text="Cargando puntos de acopio…" /> : error ? <Failure text={error} retry={() => void reload()} /> : <>
        {pins.length > 0 && <RumaMap pins={pins} />}
        {!!data?.prices.length && <section className="glass pad stack">
          <h2 className="t-card">Precios de hoy</h2>
          <div className="grid2">{data.prices.map((p) => <div key={p.code} className="glass" style={{ padding: 12 }}><p className="muted t-small">{p.name}</p><p className="money">{soles(p.price_per_kg)}/kg</p></div>)}</div>
          <p className="muted t-small">Los puntos se acreditan cuando el Eco-Rider recoge y el acopio pesa el material.</p>
        </section>}
        {data?.centers.length ? data.centers.map((c) => (
          <section className="glass pad stack" key={c.id}>
            <div className="between"><h2 className="t-row">{c.name}</h2><span className={`chip ${c.is_open ? "chip-accent" : "chip-off"}`}>{c.is_open ? "Abierto" : "Cerrado"}</span></div>
            {c.address_line && <p className="muted t-small">{c.address_line}</p>}
            <p className="t-small">{(c.center_materials ?? []).map((m: Row) => m.materials?.name).filter(Boolean).join(", ")}</p>
            <div className="grid2">
              <a className={`btn ${c.latitude != null ? "btn-primary" : "btn-neutral"}`} aria-disabled={c.latitude == null} href={c.latitude != null ? `https://www.google.com/maps/dir/?api=1&destination=${c.latitude},${c.longitude}` : undefined} target="_blank" rel="noreferrer">Cómo llegar</a>
              <a className={`btn ${c.phone ? "btn-secondary" : "btn-neutral"}`} aria-disabled={!c.phone} href={c.phone ? `tel:${c.phone}` : undefined}>Llamar</a>
            </div>
          </section>
        )) : <Empty title="Aún no hay acopios cerca" text="Cuando se registre un centro en tu zona aparecerá aquí con sus precios." icon="pin" />}
      </>}
    </main>
  );
}

/* ---------- Rankings: shared by both roles ---------- */

export function Leaderboard({ role, title }: { role: "generador" | "eco_rider"; title: string }) {
  const { data, loading, error, reload } = useAsync(async () => {
    const period = await loadOpenPeriod(role);
    if (!period) return null;
    const [entries, rewards] = await Promise.all([loadLeaderboardEntries(period.id), loadRankingRewards(period.scope, period.period_type).catch(() => [])]);
    return { period, entries, rewards };
  }, [role]);
  const [showRewards, setShowRewards] = useState(false);
  const me = data?.entries.find((e) => e.user_id === myId());
  const leader = data?.entries[0];
  const MEDAL = ["linear-gradient(135deg,#f6d365,#e0a41b)", "linear-gradient(135deg,#e5e7eb,#9ca3af)", "linear-gradient(135deg,#e6b98f,#c58b5b)"];
  return (
    <main className="screen with-nav">
      <TopBar title={title} sub={data?.period.block_label ?? undefined} />
      {loading ? <Loading text="Cargando el ranking…" /> : error ? <Failure text={error} retry={() => void reload()} /> : !data ? <Empty title="Todavía no hay ranking abierto" text="Cuando arranque el periodo verás tu posición." icon="trophy" /> : <>
        {me && <section className="hero">
          <p className="t-over">Tu posición</p>
          <div className="row"><p className="t-hero">#{me.rank}</p><p className="t-card">{me.display_name}</p></div>
          <div className="between hero-rule">
            {leader && leader.user_id !== me.user_id ? <span className="chip chip-hero">A {int(leader.points - me.points)} pts del 1° lugar</span> : <span className="chip chip-hero">Vas primero</span>}
            <span style={{ fontWeight: 800 }}>{int(me.points)} pts</span>
          </div>
        </section>}
        {data.entries.length ? <div className="glass list">
          {data.entries.map((e) => <div key={e.user_id} style={e.user_id === myId() ? { background: "var(--accent-tint)" } : undefined}>
            <span className="badge" style={{ borderRadius: "50%", width: 32, height: 32, background: MEDAL[e.rank - 1] }}>{e.rank}</span>
            <span className="badge" style={{ borderRadius: "50%" }}>{initials(e.display_name)}</span>
            <span className="min0"><span className="t-row" style={{ display: "block" }}>{e.display_name ?? "Vecino"} {e.user_id === myId() && <span className="chip chip-accent">Tú</span>}</span>{e.streak_days > 0 && <span className="muted t-small">Racha {e.streak_days} días</span>}</span>
            <span className="t-row">{int(e.points)} <span className="muted t-small">pts</span></span>
          </div>)}
        </div> : <Empty title="Aún no hay posiciones" icon="trophy" />}
        {data.rewards.length > 0 && <div className="glass list">
          <NavRow icon="trophy" title="Premios del ranking" onClick={() => setShowRewards(!showRewards)} />
          {showRewards && data.rewards.map((r) => <div key={r.rank}><span className="t-row">#{r.rank}</span><span className="min0 muted">{r.description ?? (r.reward_points ? `${int(r.reward_points)} pts` : `x${r.reward_multiplier}`)}</span></div>)}
        </div>}
      </>}
    </main>
  );
}
export const Rankings = () => <Leaderboard role="generador" title="Ranking de tu cuadra" />;

/* ---------- Profile ---------- */

export function FamilyProfile() {
  const nav = useNavigate();
  const { data, loading, error, reload } = useAsync(async () => {
    const [p, address, badges] = await Promise.all([loadGeneratorProfile(), loadPrimaryAddress().catch(() => null), loadMyAchievements().catch(() => [])]);
    return { p, address, badges };
  });
  if (loading) return <main className="screen with-nav"><Loading text="Cargando tu perfil…" /></main>;
  if (error || !data) return <main className="screen with-nav"><Failure text={error ?? "No pudimos cargar tu perfil."} retry={() => void reload()} /></main>;
  const { p, address, badges } = data;
  const name = p?.profiles?.display_name ?? p?.profiles?.full_name ?? "Mi familia";
  return (
    <main className="screen with-nav">
      <TopBar title="Mi perfil" back={false} />
      <section className="hero">
        <div className="row">
          <span className="chip-hero chip" style={{ width: 56, height: 56, justifyContent: "center", fontSize: 20 }}>{initials(name)}</span>
          <div className="min0"><p className="t-card">{name}</p>{address && <p className="soft t-small">{address.address_line}</p>}</div>
        </div>
        <div className="hero-rule" style={{ display: "flex", justifyContent: "flex-end" }}><button className="chip chip-hero" style={{ minHeight: 40 }} onClick={() => nav("/f/editar")}><Icon name="edit" size={16} /> Editar perfil</button></div>
      </section>
      <p className="t-over">Tu cuenta en números</p>
      <div className="grid3"><Metric value={String(p?.current_streak_days ?? 0)} label="Racha" /><Metric value={int(p?.points_balance)} label="Eco-Puntos" /><Metric value={soles(p?.cash_earned_total)} label="Ganados" /></div>
      <p className="t-over">Tu impacto ambiental</p>
      <section className="glass pad stack">
        <div className="grid3 center">
          <div><p className="t-row">{kg(p?.co2_avoided_kg)}</p><p className="muted t-small">CO₂ evitado</p></div>
          <div><p className="t-row">{kg(p?.recycled_kg)}</p><p className="muted t-small">Reciclado</p></div>
          <div><p className="t-row">{int(p?.water_saved_l)} L</p><p className="muted t-small">Agua</p></div>
        </div>
        {(p?.trees_equivalent ?? 0) > 0 && <p className="muted t-small center">Equivalente a plantar {Number(p!.trees_equivalent).toFixed(1)} árboles</p>}
      </section>
      {badges.length > 0 && <>
        <p className="t-over">Logros</p>
        <div className="glass pad grid3 center">{badges.map((b, i) => <div className="stack" style={{ alignItems: "center" }} key={i}><span className="badge" style={{ borderRadius: "50%", width: 52, height: 52, ...(b.unlocked ? { border: "2px solid #e0a41b" } : { opacity: 0.55 }) }}><Icon name={b.unlocked ? "trophy" : "lock"} /></span><span className={`t-small ${b.unlocked ? "" : "muted"}`}>{b.achievements?.name}</span></div>)}</div>
      </>}
      <p className="t-over">Cuenta</p>
      <div className="glass list">
        <NavRow icon="wallet" title="Mi billetera" sub="Puntos, canjes y descuento de arbitrios" onClick={() => nav("/f/wallet")} />
        <NavRow icon="history" title="Mi historial" sub="Tus escaneos y recojos, uno por uno" onClick={() => nav("/f/historial")} />
        <NavRow icon="trophy" title="Rankings" sub="Cómo va tu barrio frente a los demás" onClick={() => nav("/f/ranking")} />
        <NavRow icon="bell" title="Notificaciones" sub="Avisos de tus recojos y de tus puntos" onClick={() => nav("/f/notificaciones")} />
        <NavRow icon="chat" title="Pregúntale a Ruma" sub="Qué va en cada tacho y cuánto vale tu material" onClick={() => nav("/f/chat")} />
      </div>
      <button className="btn btn-danger" onClick={() => void signOut()}><Icon name="logout" /> Cerrar sesión</button>
    </main>
  );
}
