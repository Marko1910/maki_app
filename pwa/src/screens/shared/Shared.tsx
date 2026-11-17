import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Empty, Failure, Icon, Loading, TopBar, useToast } from "../../components/ui";
import { humanize, loadMarketNews, loadMaterialDemand, loadMyProfile, loadNotifications, loadPrices, markAllNotificationsRead, markNotificationRead, myEmail, updateMyProfile, type Row } from "../../lib/api";
import { ago, initials, soles, useAsync } from "../../lib/util";
import { PushCard } from "../../components/Push";

/* ---------- Mercado Vivo: one screen, the role only changes its colour ---------- */

const DEMAND: Record<string, { label: string; dot: string }> = {
  alta: { label: "Demanda alta", dot: "#1e8e4f" }, media: { label: "Demanda media", dot: "#d99a2b" },
  baja: { label: "Demanda baja", dot: "#9ca3af" }, sin_demanda: { label: "Sin compradores", dot: "#e05238" },
};
const demandOf = (d?: string, centers?: number) => (centers === 0 ? DEMAND.sin_demanda : DEMAND[d ?? "media"] ?? DEMAND.media);

export function Market() {
  const nav = useNavigate();
  const isRider = location.pathname.startsWith("/r/");
  const { data, loading, error, reload } = useAsync(async () => {
    const [prices, demand, news] = await Promise.all([loadPrices(), loadMaterialDemand(), loadMarketNews()]);
    return { prices, demand, news };
  });
  const rows: Row[] = (data?.demand ?? []).map((d) => {
    const p = data!.prices.find((x) => x.code === d.code);
    return { ...d, price: d.mejor_precio ?? p?.price_per_kg ?? 0, pct: p?.pct_change ?? 0 };
  }).sort((a, b) => b.price - a.price);
  const top = rows.find((r) => r.demanda === "alta") ?? rows[0];
  return (
    <main className="screen with-nav">
      <TopBar title="Mercado Vivo" sub="Cuánto paga hoy cada material y qué están pidiendo los acopios" back={false} />
      {loading ? <Loading text="Consultando el mercado…" /> : error ? <Failure text={error} retry={() => void reload()} /> : !rows.length && !data?.news.length ? <Empty title="El mercado está en calma" text="Cuando haya precios y novedades de tu zona los verás aquí." icon="market" /> : <>
        {top && <section className="hero">
          <div className="between"><p className="t-over">Lo más buscado hoy</p>{top.pct !== 0 && <span className="chip chip-hero">{top.pct > 0 ? "Sube" : "Baja"} {top.pct > 0 ? "+" : ""}{Math.round(top.pct)}%</span>}</div>
          <div className="between"><p className="t-title">{top.name}</p><p className="t-title">{soles(top.price)}<span className="t-small">/kg</span></p></div>
          {!isRider && <button className="between hero-rule soft" style={{ width: "100%" }} onClick={() => nav("/f/chat")}><span>Para ti: ¿qué junto esta semana?</span><Icon name="chevron" size={18} /></button>}
        </section>}
        {rows.length > 0 && <>
          <p className="t-over">Precios y demanda de hoy</p>
          <div className="glass list">{rows.map((r) => {
            const d = demandOf(r.demanda, r.centros);
            return <div key={r.code}>
              <span className="badge">{(r.code as string).slice(0, 3)}</span>
              <span className="min0"><span className="t-row" style={{ display: "block" }}>{r.name}</span><span className="chip" style={{ marginTop: 2 }}><i style={{ width: 7, height: 7, borderRadius: "50%", background: d.dot }} /> {d.label}</span></span>
              <span className="money" style={{ flex: "none" }}>{soles(r.price)}/kg</span>
            </div>;
          })}</div>
        </>}
        {!!data?.news.length && <>
          <p className="t-over">Qué está pasando</p>
          {data.news.map((n: Row) => <article className="glass pad stack" key={n.id}>
            <h2 className="t-row">{n.title}</h2>
            {n.summary && <p className="muted">{n.summary}</p>}
            <div className="between muted t-small">
              <span>{[n.source && `Fuente: ${n.source}`, ago(n.published_at)].filter(Boolean).join(" · ")}</span>
              {n.url && <a className="accent" style={{ fontWeight: 800, padding: "0 8px", minHeight: 48, display: "inline-flex", alignItems: "center", flex: "none" }} href={n.url} target="_blank" rel="noreferrer">Abrir</a>}
            </div>
          </article>)}
        </>}
      </>}
    </main>
  );
}

/* ---------- Notifications ---------- */

export function Notifications() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(loadNotifications);
  const [read, setRead] = useState<Set<number>>(new Set());
  const isUnread = (n: Row) => !n.is_read && !read.has(n.id);
  const all = async () => {
    try { await markAllNotificationsRead(); setRead(new Set((data ?? []).map((n) => n.id))); toast("Notificaciones marcadas como leídas"); }
    catch (e) { toast(humanize(e)); }
  };
  const one = (n: Row) => {
    if (!isUnread(n)) return;
    setRead(new Set(read).add(n.id));
    markNotificationRead(n.id).catch(() => undefined);
  };
  return (
    <main className="screen">
      <TopBar title="Notificaciones" right={data?.some(isUnread) ? <button className="btn-text" style={{ padding: "0 8px" }} onClick={() => void all()}>Marcar leídas</button> : undefined} />
      <PushCard />
      {loading ? <Loading text="Cargando notificaciones…" /> : error ? <Failure text={error} retry={() => void reload()} /> : !data?.length ? <Empty title="Sin notificaciones" text="Aquí te avisaremos de tus recojos, puntos y ganancias." icon="bell" /> :
        <div className="glass list">{data.map((n) => (
          <button key={n.id} onClick={() => one(n)} style={isUnread(n) ? { background: "var(--accent-tint)" } : undefined} aria-label={`${isUnread(n) ? "No leída. " : ""}${n.title}`}>
            <span className="badge"><Icon name="bell" /></span>
            <span className="min0"><span className="between"><span className="t-row">{n.title}</span><span className="muted t-small" style={{ flex: "none" }}>{ago(n.created_at)}</span></span>{n.body && <span className="muted t-small" style={{ display: "block" }}>{n.body}</span>}</span>
          </button>
        ))}</div>}
    </main>
  );
}

/* ---------- Edit profile ---------- */

export function EditProfile() {
  const nav = useNavigate();
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(async () => {
    const [p, email] = await Promise.all([loadMyProfile(), myEmail()]);
    return { p, email };
  });
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (data) { setName(data.p?.display_name ?? ""); setPhone(data.p?.phone ?? ""); } }, [data]);
  const save = async () => {
    if (!name.trim()) return toast("Escribe tu nombre.");
    setBusy(true);
    try { await updateMyProfile(name.trim(), phone.trim() || null); toast("Perfil actualizado."); nav(-1); }
    catch (e) { toast(humanize(e)); setBusy(false); }
  };
  return (
    <main className="screen with-bar">
      <TopBar title="Editar perfil" />
      {loading ? <Loading text="Cargando tu perfil…" /> : error ? <Failure text={error} retry={() => void reload()} /> : <>
        <div className="stack center" style={{ alignItems: "center" }}>
          <span className="glass t-title accent" style={{ width: 88, height: 88, borderRadius: "50%", display: "grid", placeItems: "center" }}>{initials(name)}</span>
          <p className="muted t-small">Tu inicial por ahora. La foto llega pronto.</p>
        </div>
        <p className="t-over">Tus datos</p>
        <div className="glass pad stack" style={{ gap: 16 }}>
          <label className="field"><span className="t-over">Nombre</span><span className="input-wrap"><input autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} /></span><span className="muted t-small">Cómo quieres que te llamen</span></label>
          <label className="field"><span className="t-over">Teléfono</span><span className="input-wrap"><input type="tel" autoComplete="tel" placeholder="+51 987 650 010" value={phone} onChange={(e) => setPhone(e.target.value)} /></span><span className="muted t-small">Para coordinar el recojo</span></label>
          <label className="field"><span className="t-over">Correo</span><span className="input-wrap"><input readOnly value={data?.email ?? ""} /></span><span className="muted t-small">El correo no se puede cambiar desde aquí.</span></label>
        </div>
        <div className="bottombar"><div><button className="btn btn-primary" disabled={busy} onClick={() => void save()}>{busy ? "Guardando…" : "Guardar cambios"}</button></div></div>
      </>}
    </main>
  );
}
