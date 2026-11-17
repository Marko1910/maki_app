import { useState } from "react";
import { Empty, Failure, Icon, Loading, TopBar, useToast } from "../../components/ui";
import { cancelMyPlan, humanize, loadMyPlan, loadPlans, requestPlan, startPlanTrial, type PlanRow } from "../../lib/api";
import { useAsync } from "../../lib/util";

/* ---------- Planes: the database prices, grants and expires; this only asks and shows (2026-11-12_planes.sql) ---------- */

const money = (n: number) => `S/ ${Number(n).toLocaleString("es-PE", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const day = (iso: string) => new Date(iso).toLocaleDateString("es-PE", { day: "numeric", month: "long", year: "numeric", timeZone: "America/Lima" });
const PERIOD: Record<string, string> = { month: "mensual", year: "anual", trial: "de prueba" };

export function Plans() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(async () => {
    const [plans, mine] = await Promise.all([loadPlans(), loadMyPlan()]);
    return { plans, mine };
  });
  const [busy, setBusy] = useState(false);
  const [period, setPeriod] = useState<"month" | "year">("month");

  const act = async (fn: () => Promise<unknown>, ok?: string) => {
    setBusy(true);
    try { const r = await fn(); toast(ok ?? (typeof r === "string" ? r : "Listo")); await reload(true); }
    catch (e) { toast(humanize(e)); }
    setBusy(false);
  };

  if (loading && !data) return <main className="screen"><TopBar title="Planes" /><Loading text="Cargando planes…" /></main>;
  if (!data) return <main className="screen"><TopBar title="Planes" /><Failure text={error ?? "No pudimos cargar los planes."} retry={() => void reload()} /></main>;

  const { plans, mine } = data;
  const paid: PlanRow[] = plans.filter((p) => p.price_month > 0);
  const free = plans.find((p) => p.price_month <= 0);
  const pay = mine.payment;
  const active = mine.active;

  return (
    <main className="screen">
      <TopBar title="Planes" sub="Ruma Plus" />
      <section className="hero">
        <p className="t-over">Tu plan</p>
        <p className="t-hero">{active ? active.name : free?.name ?? "Ruma"}</p>
        <p className="soft">{active
          ? active.cancelled_at ? `No se renovará. Tus beneficios siguen hasta el ${day(active.ends_at)}.` : `Plan ${PERIOD[active.period] ?? ""}, vigente hasta el ${day(mine.active_until ?? active.ends_at)}.`
          : free?.tagline ?? "Todo lo que necesitas para reciclar y cobrar"}</p>
        {active && mine.tickets && <p className="soft t-small">Boletos del sorteo este mes: {mine.tickets.total}</p>}
        {active && !active.cancelled_at && <button className="btn btn-secondary" disabled={busy} onClick={() => void act(() => cancelMyPlan(active.id))}>No renovar</button>}
      </section>

      {mine.pending && <section className="glass pad stack" style={{ gap: 10 }}>
        <p className="t-over">Pago en revisión</p>
        <p className="t-row">{mine.pending.name} {PERIOD[mine.pending.period]}: {money(mine.pending.amount)}</p>
        {pay?.yape_number
          ? <>
              <p>Yapea {money(mine.pending.amount)} al <b>{pay.yape_number}</b>{pay.yape_holder ? ` (${pay.yape_holder})` : ""} y escribe <b>{mine.pending.reference}</b> en el mensaje.</p>
              {pay.instructions && <p className="muted t-small">{pay.instructions}</p>}
              {pay.yape_qr_url && <img src={pay.yape_qr_url} alt="QR de Yape" width={160} height={160} />}
            </>
          : <p className="muted">El número de Yape se publicará pronto. Tu solicitud ya está guardada con el código <b>{mine.pending.reference}</b>.</p>}
        <button className="btn btn-secondary" disabled={busy} onClick={() => void act(() => cancelMyPlan(mine.pending!.id))}>Retirar solicitud</button>
      </section>}

      {paid.map((p) => {
        const canYear = p.price_year != null && p.price_year > 0;
        const chosen = period === "year" && canYear ? "year" : "month";
        const trial = mine.trial_available && p.trial_days > 0;
        return (
          <section className="glass pad stack" style={{ gap: 12 }} key={p.code}>
            <div><p className="t-card">{p.name}</p>{p.tagline && <p className="muted">{p.tagline}</p>}</div>
            {canYear && <div className="row" role="group" aria-label="Periodo de pago" style={{ gap: 8 }}>
              <button className={`chip ${chosen === "month" ? "chip-accent" : ""}`} style={{ minHeight: 44 }} aria-pressed={chosen === "month"} onClick={() => setPeriod("month")}>Mensual {money(p.price_month)}</button>
              <button className={`chip ${chosen === "year" ? "chip-accent" : ""}`} style={{ minHeight: 44 }} aria-pressed={chosen === "year"} onClick={() => setPeriod("year")}>Anual {money(p.price_year!)}</button>
            </div>}
            <ul className="plan-features">
              {p.features.map((f, i) => (
                <li key={i}>
                  <span className={f.status === "activo" ? "accent" : "muted"}><Icon name={f.status === "activo" ? "check" : "clock"} size={18} /></span>
                  <span className="min0"><span className="t-row" style={{ display: "block" }}>{f.title}{f.status === "pronto" && <> <span className="chip chip-off">Pronto</span></>}</span>
                    {f.detail && <span className="muted t-small" style={{ display: "block" }}>{f.detail}</span>}</span>
                </li>
              ))}
            </ul>
            {trial && !mine.pending && <button className="btn btn-primary" disabled={busy} onClick={() => void act(() => startPlanTrial(p.code), `¡Empezó tu prueba de ${p.trial_days} días!`)}>Probar {p.trial_days} días gratis</button>}
            {!mine.pending && <button className={`btn ${trial ? "btn-secondary" : "btn-primary"}`} disabled={busy} onClick={() => void act(() => requestPlan(p.code, chosen), "Solicitud guardada. Mira cómo pagar abajo.")}>
              {active ? "Renovar" : "Pedir"} {p.name} · {money(chosen === "year" ? p.price_year! : p.price_month)}</button>}
          </section>
        );
      })}
      {!paid.length && <Empty icon="flash" title="Aún no hay planes de pago" text="Tu plan Ruma gratis sigue completo." />}
      {pay?.business_contact && <a className="btn btn-secondary" href={pay.business_contact} target="_blank" rel="noreferrer">¿Tienes un negocio? Escríbenos</a>}
      <p className="muted t-small">Ruma Plus no da más puntos por tu material: da comodidad. Pagas por Yape y activamos tu plan en menos de 24 horas.</p>
    </main>
  );
}

