"use client";

import { useCallback, useEffect, useState } from "react";
import { supabase, hace, num } from "@/lib/supabase";
import { miCentro, llamadas, llamadasDelDia, registrarLlamada, TIPOS_LLAMADA } from "@/lib/queries";
import {
  Kpi, Card, Chip, Empty, ErrorBox, SkeletonKpis, SkeletonLista, Cargando,
  Aviso, type AvisoMsg, type Tinte,
} from "@/components/ui";

const COLOR: Record<string, Tinte> = {
  register_pickup: "admin", check_balance: "money", resolve_doubt: "rider",
  contact_rider: "success", complaint: "error", other: "text2",
};
const nombreTipo = (v: string) => TIPOS_LLAMADA.find((t) => t.v === v)?.t ?? v;

export default function CallCenter() {
  const [centro, setCentro] = useState<any>(null);
  const [lista, setLista] = useState<any[]>([]);
  const [hoy, setHoy] = useState<any[]>([]);
  const [kind, setKind] = useState<string>("register_pickup");
  const [tel, setTel] = useState("");
  const [notas, setNotas] = useState("");
  const [min, setMin] = useState("");
  const [busy, setBusy] = useState(false);
  const [aviso, setAviso] = useState<AvisoMsg | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [primera, setPrimera] = useState(true);

  const cargar = useCallback(async () => {
    try {
      const c = await miCentro();
      if (!c) return setError("Tu cuenta no tiene un centro asignado.");
      setCentro(c);
      // La lista va recortada a 25; los indicadores se cuentan aparte sobre
      // TODAS las del día, o a partir de la llamada 26 dejaban de sumar.
      const [l, d] = await Promise.all([llamadas(c.id), llamadasDelDia(c.id)]);
      setLista(l); setHoy(d);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "No pudimos leer las llamadas.");
    }
    finally {
      setPrimera(false);
    }
  }, []);
  useEffect(() => { cargar(); }, [cargar]);

  async function guardar(e: React.FormEvent) {
    e.preventDefault();
    if (!centro) return setAviso({ ok: false, txt: "Todavía cargando el centro. Espera un segundo." });
    if (!notas.trim() && !tel.trim()) return setAviso({ ok: false, txt: "Anota al menos el teléfono o qué pidió la persona." });
    // "1,5" minutos es entrada válida en Perú; Number() lo volvía NaN y la
    // duración se guardaba como null mientras el panel decía "registrada".
    const mins = min.trim() ? num(min) : null;
    if (min.trim() && mins === null) return setAviso({ ok: false, txt: "La duración tiene que ser un número. Puedes usar coma: 1,5" });
    setBusy(true);
    try {
      const { data: s } = await supabase.auth.getSession();
      if (!s.session) { setAviso({ ok: false, txt: "Tu sesión se cerró. Entra de nuevo." }); return; }
      const r = await registrarLlamada(centro.id, s.session.user.id, kind, tel.trim(), notas.trim(),
        mins === null ? null : Math.round(mins * 60));
      if (r.error) setAviso({ ok: false, txt: r.error.message });
      else { setAviso({ ok: true, txt: "Llamada registrada." }); setTel(""); setNotas(""); setMin(""); }
    } catch (err) {
      setAviso({ ok: false, txt: err instanceof Error ? err.message : "No se pudo registrar la llamada." });
    } finally {
      setBusy(false);
      await cargar();
    }
  }

  if (error) return <ErrorBox msg={error} onRetry={cargar} />;

  if (primera) return (
    <Cargando etiqueta="Cargando atención telefónica">
      <SkeletonKpis />
      <div className="flex gap-5 items-start">
        <div className="w-[380px] shrink-0"><SkeletonLista filas={4} alto="h-14" /></div>
        <div className="flex-1 min-w-0"><SkeletonLista filas={5} /></div>
      </div>
    </Cargando>
  );

  const reclamos = hoy.filter((l) => l.kind === "complaint").length;
  const minutos = Math.round(hoy.reduce((s, l) => s + (l.duration_seconds ?? 0), 0) / 60);

  return (
    <>
      <div className="flex gap-5">
        <Kpi label="Llamadas de hoy" value={String(hoy.length)} icon="✆" iconColor="rider"
          chip="del día completo" chipColor="rider" hint="registradas en el centro" />
        <Kpi label="Reclamos de hoy" value={String(reclamos)} icon="◉"
          iconColor={reclamos ? "error" : "success"}
          chip={reclamos ? "requieren respuesta" : "ninguno"} chipColor={reclamos ? "error" : "success"}
          hint="del total del día" />
        <Kpi label="Tiempo al teléfono" value={String(minutos)} unit="min" icon="◷" iconColor="money"
          chip="hoy" chipColor="money" hint="solo lo que se anotó" />
        <Kpi label="Recojos por teléfono" value={String(hoy.filter((l) => l.kind === "register_pickup").length)}
          icon="▦" iconColor="admin" chip="sin app" chipColor="admin" hint="quien no usa el celular" />
      </div>

      <div className="flex gap-5 items-start">
        <section className="w-[380px] shrink-0 flex flex-col gap-3">
          <h2 className="text-[15px] font-extrabold">Registrar llamada</h2>
          <Card>
            <form onSubmit={guardar} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <span className="text-xs font-bold text-text2">¿Qué necesitaba?</span>
                <div className="flex flex-wrap gap-1.5">
                  {TIPOS_LLAMADA.map((t) => (
                    <button key={t.v} type="button" onClick={() => setKind(t.v)}
                      className={`rounded-lg px-3 py-1.5 text-[11px] font-bold border transition
                        ${kind === t.v ? "bg-admin text-white border-admin" : "bg-surface text-text2 border-border"}`}>
                      {t.t}
                    </button>
                  ))}
                </div>
              </div>

              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-bold text-text2">Teléfono de quien llamó</span>
                <input value={tel} onChange={(e) => setTel(e.target.value)} inputMode="tel"
                  placeholder="987 654 321"
                  className="h-11 border border-border rounded-xl px-3.5 text-sm outline-none focus:border-admin" />
              </label>

              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-bold text-text2">Qué pidió y qué se hizo</span>
                <textarea value={notas} onChange={(e) => setNotas(e.target.value)} rows={3}
                  placeholder="La señora de la cuadra 7 quiere que pasen hoy por 20 botellas."
                  className="border border-border rounded-xl px-3.5 py-2.5 text-sm outline-none focus:border-admin resize-none" />
              </label>

              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-bold text-text2">Duración (minutos, opcional)</span>
                <input value={min} onChange={(e) => setMin(e.target.value)} inputMode="decimal" placeholder="3"
                  className="h-11 w-28 border border-border rounded-xl px-3.5 text-sm outline-none focus:border-admin" />
              </label>

              <Aviso aviso={aviso} />

              <button disabled={busy || !centro}
                className="h-11 bg-admin text-white rounded-xl text-sm font-bold disabled:opacity-60">
                {busy ? "Guardando…" : !centro ? "Cargando centro…" : "Registrar llamada"}
              </button>
            </form>
          </Card>
          <Card className="bg-[#F9FAFB] text-[11px] text-text2">
            Registrar la llamada no crea el recojo. Si la familia no usa la app, el recojo lo crea
            el operador desde la pantalla del vecino o lo toma un rider del tablero.
          </Card>
        </section>

        <section className="flex-1 min-w-0 flex flex-col gap-3">
          <h2 className="text-[15px] font-extrabold">Últimas llamadas</h2>
          {lista.length === 0 ? <Empty msg="Todavía no se ha registrado ninguna llamada." /> : (
            <Card className="flex flex-col gap-2.5">
              {lista.map((l) => (
                <div key={l.id} className="flex items-start gap-3 bg-bg rounded-xl px-3.5 py-3">
                  <Chip color={COLOR[l.kind] ?? "text2"}>{nombreTipo(l.kind)}</Chip>
                  <div className="min-w-0 flex-1">
                    <p className="text-[13px] leading-snug">{l.notes || <span className="text-text2">sin nota</span>}</p>
                    <p className="text-[11px] text-text2 mt-0.5">
                      {l.caller_phone ?? "sin teléfono"}
                      {l.duration_seconds ? ` · ${Math.round(l.duration_seconds / 60)} min` : ""}
                    </p>
                  </div>
                  <span className="text-[11px] text-text2 font-semibold whitespace-nowrap">{hace(l.created_at)}</span>
                </div>
              ))}
            </Card>
          )}
        </section>
      </div>
    </>
  );
}
