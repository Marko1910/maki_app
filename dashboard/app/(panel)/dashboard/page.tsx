"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import dynamic from "next/dynamic";
import { supabase, hace, soles, minutesSince, horaConsulta, num } from "@/lib/supabase";
import {
  miCentro, colaPendiente, liquidadasHoy, margen, ridersEnVivo, recojosActivos,
  recibirEntrega, registrarPesaje, liquidarEntrega, materialesDelCentro, FACTOR_GRADO,
  type Entrega, type RiderVivo, type RecojoActivo, type Centro, type PesoItem,
} from "@/lib/queries";
import {
  Kpi, Card, Chip, Tabla, Th, Td, ErrorBox, Empty,
  SkeletonKpis, SkeletonTabla, SkeletonLista, SkeletonMapa, Cargando, Aviso,
} from "@/components/ui";

const Mapa = dynamic(() => import("@/components/Mapa"), {
  ssr: false,
  loading: () => <div className="h-[300px] rounded-[14px] bg-surface border border-border grid place-items-center text-sm text-text2">Cargando mapa…</div>,
});

/** El rider heartbeatea cada ~15 s; sin señal a los 15 min (igual que la app). */
const POLL_MS = 15_000;
const SIN_SENAL_MIN = 15;

const ETAPA: Record<string, { txt: string; c: "rider" | "admin" | "money" }> = {
  en_camino: { txt: "por recibir", c: "rider" },
  recibida: { txt: "por pesar", c: "admin" },
  pesada: { txt: "por liquidar", c: "money" },
};

export default function Dashboard() {
  const [centro, setCentro] = useState<Centro | null>(null);
  const [cola, setCola] = useState<Entrega[]>([]);
  const [hoy, setHoy] = useState<Entrega[]>([]);
  const [riders, setRiders] = useState<RiderVivo[]>([]);
  const [recojos, setRecojos] = useState<RecojoActivo[]>([]);
  const [mats, setMats] = useState<any[]>([]);
  const [consulta, setConsulta] = useState("");
  const [error, setError] = useState<string | null>(null);
  // Solo la PRIMERA carga muestra esqueleto; los polls siguientes refrescan en
  // silencio para no hacer parpadear la pantalla cada 15 s.
  const [primera, setPrimera] = useState(true);
  const [ocupados, setOcupados] = useState<Set<string>>(new Set());
  const [aviso, setAviso] = useState<{ ok: boolean; txt: string } | null>(null);
  const [pesando, setPesando] = useState<Entrega | null>(null);

  // Cada carga lleva número: si vuelve una antigua después de una nueva, se
  // descarta. Sin esto una respuesta lenta revivía un "Recibir" ya hecho.
  const seq = useRef(0);
  const vivo = useRef(true);
  // Se RESTABLECE en el setup, no solo se apaga en el cleanup: en Strict Mode
  // React monta, desmonta y vuelve a montar. Sin esta línea `vivo` quedaba en
  // false tras el primer desmontaje y la pantalla no salía nunca del esqueleto.
  useEffect(() => {
    vivo.current = true;
    return () => { vivo.current = false; };
  }, []);

  const cargar = useCallback(async () => {
    const mio = ++seq.current;
    try {
      const c = await miCentro();
      if (!c) {
        if (mio === seq.current) { setError("Tu cuenta no tiene un centro de acopio asignado."); setPrimera(false); }
        return;
      }
      const [a, b, r, p, m] = await Promise.all([
        colaPendiente(c.id), liquidadasHoy(c.id), ridersEnVivo(), recojosActivos(c.id), materialesDelCentro(c.id),
      ]);
      if (mio !== seq.current || !vivo.current) return;   // llegó tarde: la pisa una más nueva
      setCentro(c); setCola(a); setHoy(b); setRiders(r); setRecojos(p); setMats(m);
      setConsulta(horaConsulta());   // hora de la CONSULTA, no del dato
      setError(null);
    } catch (e) {
      if (mio !== seq.current || !vivo.current) return;
      setError(e instanceof Error ? e.message : "No pudimos leer los datos del centro.");
    } finally {
      if (mio === seq.current && vivo.current) setPrimera(false);
    }
  }, []);

  useEffect(() => {
    cargar();
    const t = setInterval(cargar, POLL_MS);
    return () => clearInterval(t);
  }, [cargar]);

  const marcar = (id: string, on: boolean) =>
    setOcupados((s) => { const n = new Set(s); on ? n.add(id) : n.delete(id); return n; });

  async function accion(e: Entrega) {
    if (ocupados.has(e.id)) return;
    if (e.status === "recibida") { setPesando(e); return; }   // pesar abre el formulario
    marcar(e.id, true);
    setAviso(null);
    try {
      const { data: s } = await supabase.auth.getSession();
      if (!s.session) { setAviso({ ok: false, txt: "Tu sesión se cerró. Entra de nuevo." }); return; }
      let err: string | null = null;
      if (e.status === "en_camino") {
        const r = await recibirEntrega(e.id, s.session.user.id);
        err = r.error ? r.error.message : (!r.data ? "La base no aceptó recibir este lote (¿es de tu centro?)." : null);
      } else if (e.status === "pesada") {
        const r = await liquidarEntrega(e.id);
        err = r.error ? r.error.message : null;
      }
      setAviso(err ? { ok: false, txt: err } : { ok: true, txt: "Listo." });
    } catch (err) {
      setAviso({ ok: false, txt: err instanceof Error ? err.message : "No se pudo completar la acción." });
    } finally {
      marcar(e.id, false);
      await cargar();
    }
  }

  const porPesar = cola.filter((e) => e.status === "recibida");
  const porLiquidar = cola.filter((e) => e.status === "pesada");
  const enCamino = cola.filter((e) => e.status === "en_camino");
  const m = margen(hoy);
  const esperaMax = (l: Entrega[], campo: keyof Entrega) =>
    l.map((e) => minutesSince(e[campo] as string) ?? 0).sort((a, b) => b - a)[0] ?? 0;

  if (error) return <ErrorBox msg={error} onRetry={cargar} />;

  if (primera) return (
    <Cargando etiqueta="Cargando la operación del centro">
      <SkeletonKpis />
      <div className="flex gap-5 items-start">
        <div className="flex-1 min-w-0 flex flex-col gap-3">
          <SkeletonMapa />
          <SkeletonLista filas={2} />
        </div>
        <div className="w-[400px] shrink-0"><SkeletonLista filas={3} /></div>
      </div>
      <SkeletonTabla filas={4} />
    </Cargando>
  );

  return (
    <>
      <div className="flex gap-5">
        <Kpi label="Por pesar" value={String(porPesar.length)} unit="lotes" icon="⚖" iconColor="admin"
          chip={porPesar.length ? `espera ${esperaMax(porPesar, "received_at")} min` : "sin cola"}
          chipColor="admin" hint="el más antiguo" />
        <Kpi label="Por liquidar" value={String(porLiquidar.length)} unit="lotes" icon="▤" iconColor="money"
          chip={soles(porLiquidar.reduce((s, e) => s + Number(e.gross_amount ?? 0), 0))}
          chipColor="money" hint="valor del pesaje" />
        <Kpi label="Entregas en camino" value={String(enCamino.length)} unit="lotes" icon="⛟" iconColor="rider"
          chip={`${enCamino.length} riders`} chipColor="rider" hint="hacia el centro" />
        <Kpi label="Margen neto de hoy" value={soles(m.total)} icon="◉"
          iconColor={m.total < 0 ? "error" : "success"}
          chip={m.negativos ? `${m.negativos} lotes en pérdida` : "sin pérdidas"}
          chipColor={m.negativos ? "error" : "success"}
          hint={m.negativos ? `${soles(m.perdida)} sin compensar` : "liquidado"} />
      </div>

      <Aviso aviso={aviso} />

      <div className="flex gap-5 items-start">
        <section className="flex-1 min-w-0 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <h2 className="text-[15px] font-extrabold">Seguimiento operativo</h2>
            <span className="text-[11px] text-text2 font-semibold">↻ Última consulta {consulta}</span>
          </div>

          <Mapa centro={centro} riders={riders} recojos={recojos} />

          <Card className="flex flex-col gap-2.5">
            {riders.length === 0 && <p className="text-sm text-text2">Ningún Eco-Rider en turno ahora.</p>}
            {riders.map((r) => {
              const min = minutesSince(r.last_location_at);
              const sinSenal = min === null || min >= SIN_SENAL_MIN;
              return (
                <div key={r.profile_id} className="flex items-center gap-3 bg-bg rounded-xl px-3.5 py-3">
                  <span className={`w-8 h-8 rounded-full grid place-items-center text-xs
                    ${sinSenal ? "bg-text2/15 text-text2" : "bg-rider/10 text-rider"}`}>⚲</span>
                  <div className="min-w-0 flex-1">
                    <p className="text-[13px] font-bold truncate">{r.profiles?.display_name ?? "Eco-Rider"}</p>
                    <p className="text-[11px] text-text2">{r.rider_code ?? "—"} · {r.vehicle_type}</p>
                  </div>
                  <Chip color={sinSenal ? "text2" : "rider"}>
                    {sinSenal
                      ? `sin actualización ${min === null ? "" : hace(r.last_location_at)}`
                      : `ubicación ${hace(r.last_location_at)}`}
                  </Chip>
                </div>
              );
            })}
            <p className="text-[11px] text-text2 mt-1">
              La ubicación se reporta cada ~15 s mientras la app del rider está abierta. Sin
              actualización reciente puede ser cobertura, no la app cerrada; se muestra la última
              posición conocida.
            </p>
          </Card>
        </section>

        <section className="w-[400px] shrink-0 flex flex-col gap-3">
          <h2 className="text-[15px] font-extrabold">Entregas en camino</h2>
          {enCamino.length === 0 ? (
            <Empty msg="Nada viniendo al centro ahora." />
          ) : (
            <Card className="flex flex-col gap-2.5">
              {enCamino.map((e) => (
                <div key={e.id} className="flex items-center gap-3 bg-bg rounded-xl px-3.5 py-3">
                  <span className="w-8 h-8 rounded-full bg-rider/10 text-rider grid place-items-center">⛟</span>
                  <div className="min-w-0 flex-1">
                    <p className="text-[13px] font-extrabold truncate">{e.code}</p>
                    <p className="text-[11px] text-text2 truncate">{e.rider?.display_name ?? "Eco-Rider"}</p>
                  </div>
                  <span className="text-[11px] text-text2 font-semibold">{hace(e.departed_at)}</span>
                </div>
              ))}
            </Card>
          )}
        </section>
      </div>

      <section className="flex flex-col gap-3">
        <h2 className="text-[15px] font-extrabold">Pendientes · incluye lotes de días anteriores</h2>
        {cola.length === 0 ? (
          <Empty msg="No hay lotes pendientes. Todo recibido, pesado y liquidado." />
        ) : (
          <Tabla>
            <thead>
              <tr>
                <Th w="120px">LOTE</Th><Th>RIDER</Th><Th>CONTENIDO</Th>
                <Th w="190px">ETAPA</Th><Th w="130px">RESULTADO</Th><Th w="120px">ACCIÓN</Th>
              </tr>
            </thead>
            <tbody>
              {cola.map((e) => {
                const et = ETAPA[e.status] ?? { txt: e.status, c: "text2" as const };
                const desde = e.status === "pesada" ? e.weighed_at
                  : e.status === "recibida" ? e.received_at : e.departed_at;
                const neto = Number(e.platform_fee ?? 0) - Number(e.rider_amount ?? 0);
                // Los importes los escribe settle_delivery. Antes de liquidar no
                // existen: mostrar S/ 0.00 en verde sería inventar un margen.
                const hayImporte = e.status === "pesada" && Number(e.gross_amount ?? 0) > 0;
                const btn = e.status === "en_camino" ? "Recibir"
                  : e.status === "recibida" ? "Pesar" : "Liquidar";
                const ocupado = ocupados.has(e.id);
                return (
                  <tr key={e.id}>
                    <Td className="font-extrabold">{e.code}</Td>
                    <Td className="font-semibold">{e.rider?.display_name ?? "—"}</Td>
                    <Td className="text-text2">{Number(e.total_kg) > 0 ? `${e.total_kg} kg` : "sin pesar"}</Td>
                    <Td><Chip color={et.c}>{et.txt} · {hace(desde).replace("hace ", "")}</Chip></Td>
                    <Td className={`font-extrabold ${hayImporte ? (neto < 0 ? "text-error" : "text-success") : "text-text2"}`}>
                      {hayImporte ? soles(neto) : <span className="font-medium text-[11px]">sin liquidar</span>}
                    </Td>
                    <Td>
                      <button disabled={ocupado} onClick={() => accion(e)}
                        className={`rounded-[9px] px-3.5 py-2 text-xs font-bold text-white disabled:opacity-50
                          ${e.status === "en_camino" ? "bg-rider" : e.status === "recibida" ? "bg-admin" : "bg-success"}`}>
                        {ocupado ? "…" : btn}
                      </button>
                    </Td>
                  </tr>
                );
              })}
            </tbody>
          </Tabla>
        )}
      </section>

      {pesando && (
        <ModalPesaje
          entrega={pesando}
          materiales={mats}
          onCerrar={() => setPesando(null)}
          onListo={async (msg) => { setPesando(null); setAviso(msg); await cargar(); }}
        />
      )}
    </>
  );
}

/**
 * El pesaje de verdad: kilos y calidad por material. Esto es lo que convierte
 * unidades en kilos y da a settle_delivery con qué repartir los puntos.
 */
function ModalPesaje({
  entrega, materiales, onCerrar, onListo,
}: {
  entrega: Entrega; materiales: any[];
  onCerrar: () => void; onListo: (m: { ok: boolean; txt: string }) => void;
}) {
  const [kg, setKg] = useState<Record<string, string>>({});
  const [grado, setGrado] = useState<Record<string, "A" | "B" | "C">>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const aceptados = materiales.filter((m) => m.is_accepted);

  const items: PesoItem[] = aceptados.flatMap((m) => {
    const n = num(kg[m.material_id] ?? "");
    if (n === null || n <= 0) return [];
    return [{
      material_id: m.material_id, weight_kg: n,
      quality_grade: grado[m.material_id] ?? "B",
      price_per_kg: Number(m.buy_price_per_kg ?? 0),
    }];
  });

  const total = items.reduce(
    (s, it) => s + it.weight_kg * it.price_per_kg * FACTOR_GRADO[it.quality_grade], 0);

  async function guardar() {
    if (items.length === 0) return setErr("Pon los kilos de al menos un material.");
    setBusy(true);
    setErr(null);
    try {
      const r = await registrarPesaje(entrega.id, items);
      if (r.error) { setErr(r.error.message); setBusy(false); return; }
      onListo({ ok: true, txt: `Pesaje de ${entrega.code} registrado: ${items.length} materiales.` });
    } catch (e) {
      setErr(e instanceof Error ? e.message : "No se pudo registrar el pesaje.");
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/40 grid place-items-center p-6" onClick={onCerrar}>
      <div className="ruma-card w-full max-w-lg p-6 flex flex-col gap-4 max-h-[85vh] overflow-auto"
        onClick={(ev) => ev.stopPropagation()}>
        <div>
          <h3 className="text-lg font-extrabold">Pesar {entrega.code}</h3>
          <p className="text-xs text-text2 mt-0.5">
            De {entrega.rider?.display_name ?? "Eco-Rider"}. Los kilos y la calidad que pongas aquí
            son los que pagan a las familias.
          </p>
        </div>

        <div className="flex flex-col gap-2.5">
          {aceptados.map((m) => (
            <div key={m.material_id} className="flex items-center gap-3 bg-bg rounded-xl px-3.5 py-2.5">
              <div className="min-w-0 flex-1">
                <p className="text-[13px] font-bold">{m.materials?.name}</p>
                <p className="text-[10px] text-text2">S/ {Number(m.buy_price_per_kg).toFixed(2)}/kg</p>
              </div>
              <input value={kg[m.material_id] ?? ""} inputMode="decimal" placeholder="0,0"
                onChange={(ev) => setKg({ ...kg, [m.material_id]: ev.target.value })}
                className="w-24 h-9 border border-border rounded-lg px-2.5 text-[13px] text-right outline-none focus:border-admin" />
              <span className="text-[11px] text-text2 w-4">kg</span>
              <div className="flex gap-1">
                {(["A", "B", "C"] as const).map((g) => (
                  <button key={g} onClick={() => setGrado({ ...grado, [m.material_id]: g })}
                    className={`w-7 h-7 rounded-lg text-[11px] font-extrabold border
                      ${(grado[m.material_id] ?? "B") === g
                        ? "bg-admin text-white border-admin" : "bg-surface text-text2 border-border"}`}>
                    {g}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>

        <div className="flex items-center justify-between bg-[#F9FAFB] rounded-xl px-4 py-3">
          <span className="text-xs font-bold text-text2">Valor del pesaje</span>
          <span className="text-lg font-extrabold">{soles(total)}</span>
        </div>
        <p className="text-[11px] text-text2">
          El grado escala el precio: A paga 100 %, B 85 %, C 60 %. El importe definitivo lo calcula
          la base al liquidar; este es el que sale de lo que acabas de pesar.
        </p>

        {err && <p className="text-xs text-error bg-error/10 rounded-lg px-3 py-2 font-semibold">{err}</p>}

        <div className="flex gap-2">
          <button onClick={guardar} disabled={busy}
            className="flex-1 h-11 bg-admin text-white rounded-xl text-sm font-bold disabled:opacity-60">
            {busy ? "Guardando…" : "Registrar pesaje"}
          </button>
          <button onClick={onCerrar} disabled={busy}
            className="px-5 h-11 border border-border rounded-xl text-sm font-bold text-text2">
            Cancelar
          </button>
        </div>
      </div>
    </div>
  );
}
