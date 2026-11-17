import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Empty, Failure, Field, Icon, Loading, NavRow, StepDots, TopBar, useToast } from "../../components/ui";
import { applyReferralCode, humanize, loadReferralSummary, myId } from "../../lib/api";
import { IS_STAGING } from "../../lib/config";
import { captureRef, claimInvitePrompt, cleanCode, clearRef, inviteLine, inviteMessage, savedRef, type Invite as InviteRow } from "../../lib/referral";
import { int, useAsync } from "../../lib/util";

// A shared link opens the app at "/?ref=CODIGO" and the entry route redirects at once: keep the code now.
captureRef(location.search);

/* ---------- Invita y gana: everything shown comes from rpc/my_referral_summary ---------- */

export function Invite() {
  const toast = useToast();
  const { data: s, loading, error, reload } = useAsync(loadReferralSummary);
  const [code, setCode] = useState(savedRef);
  const [busy, setBusy] = useState(false);
  const [applyError, setApplyError] = useState<string | null>(null);

  const apply = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true); setApplyError(null);
    try { await applyReferralCode(code); clearRef(); await reload(); }
    catch (err) { setApplyError(humanize(err)); }
    setBusy(false);
  };
  const copy = async () => {
    try { await navigator.clipboard.writeText(s!.code); toast("Código copiado"); }
    catch { toast(`Tu código es ${s!.code}`); }
  };

  if (loading && !s) return <main className="screen"><TopBar title="Invita y gana" /><Loading text="Cargando tu código…" /></main>;
  if (!s) return <main className="screen"><TopBar title="Invita y gana" /><Failure text={error ?? "No pudimos cargar tus invitaciones."} retry={() => void reload()} /></main>;

  const invites: InviteRow[] = s.invites ?? [];
  const by: InviteRow | null = s.invited_by ?? null;
  return (
    <main className="screen">
      <TopBar title="Invita y gana" sub="Recicla con tus vecinos" />
      <section className="hero invite-hero">
        <div className="min0 stack" style={{ gap: 8 }}>
          <p className="t-over">Tu código</p>
          <p className="t-hero invite-code">{s.code}</p>
          <p className="soft">Tú ganas {int(s.referrer_points)} pts y tu vecino {int(s.referred_points)} pts cuando el acopio pese su primer reciclaje.</p>
        </div>
        <img src="/ruma/ruma-5.webp" alt="" width={88} height={88} />
      </section>
      <a className="btn btn-primary" href={`https://wa.me/?text=${encodeURIComponent(inviteMessage(s.code, s.referred_points, IS_STAGING))}`} target="_blank" rel="noreferrer"><Icon name="send" /> Compartir por WhatsApp</a>
      <button className="btn btn-secondary" onClick={() => void copy()}>Copiar código</button>

      <section className="glass pad stack" style={{ gap: 14 }}>
        <p className="t-over">Cómo funciona</p>
        <StepDots label="Cómo funciona Invita y gana" current={0} steps={[["Comparte", "tu código"], ["Tu vecino recicla", "y pide su recojo"], ["Lo pesan", "y ganan los dos"]]} />
        <p className="muted t-small">Cuenta desde {Number(s.min_kg).toLocaleString("es-PE")} kg pesados en el acopio. Hasta {s.monthly_cap} invitados pagados por mes. No vale para tu misma casa ni tu mismo teléfono.</p>
        <p className="t-row">Este mes: {s.paid_this_month} de {s.monthly_cap} · Ganaste {int(s.earned_points)} pts invitando</p>
      </section>

      {by && <section className="glass pad">
        <p className="t-row">Te invitó {by.name ?? "un vecino"}</p>
        <p className="muted t-small">{by.status === "pagado" ? `Recibiste ${int(by.points)} pts de bienvenida.` : by.status === "rechazado" ? "Esta invitación no calificó." : `Tus ${int(s.referred_points)} pts de bienvenida llegan cuando pesen tu primer reciclaje.`}</p>
      </section>}

      {s.can_apply && <form className="glass pad stack" style={{ gap: 12 }} onSubmit={(e) => void apply(e)}>
        <Field label="¿Te invitó alguien? Código" placeholder="6 letras" autoCapitalize="characters" autoComplete="off" value={code} onChange={(e) => { setCode(cleanCode(e.target.value)); setApplyError(null); }}
          helper={`Solo en tus primeros ${s.attach_days} días y antes de tu primer pesaje.`} />
        {applyError && <p className="danger" role="alert">{applyError}</p>}
        <button className="btn btn-secondary" disabled={busy || code.length < 6}>{busy ? "Revisando…" : "Usar código"}</button>
      </form>}

      <p className="t-over">Tus invitados</p>
      {invites.length ? <div className="glass list">
        {invites.map((i, n) => {
          const [chip, line] = inviteLine(i, s.monthly_cap);
          return <NavRow key={n} title={i.name ?? "Tu invitado"} sub={line} right={<span className={`chip ${i.status === "pagado" ? "chip-accent" : i.status === "rechazado" ? "chip-off" : "chip-money"}`}>{chip}</span>} />;
        })}
      </div> : <Empty icon="send" title="Aún no invitas a nadie" text={`Comparte tu código por WhatsApp. Cuando pesen su primer reciclaje, te pagamos ${int(s.referrer_points)} pts.`} />}
    </main>
  );
}

/** The home card: one calm line at a happy moment (points credited, weekly goal met), at most once a week. */
export function InvitePrompt({ joy }: { joy: boolean }) {
  const nav = useNavigate();
  const [show, setShow] = useState(false);
  useEffect(() => { if (joy && claimInvitePrompt(myId())) setShow(true); }, [joy]);
  if (!show) return null;
  return (
    <div className="glass invite-prompt">
      <button className="row min0" onClick={() => nav("/f/invita")} aria-label="Abrir Invita y gana">
        <img src="/ruma/ruma-5.webp" alt="" width={56} height={56} />
        <span className="min0"><span className="t-row" style={{ display: "block" }}>¿Te gusta Ruma? Invita a un vecino</span><span className="muted t-small" style={{ display: "block" }}>Cuando pesen su primer reciclaje, ganan puntos los dos.</span></span>
      </button>
      <button className="ghost-btn" aria-label="Ahora no" onClick={() => setShow(false)}><Icon name="close" size={18} /></button>
    </div>
  );
}
