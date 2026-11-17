"use client";

import { useCallback, useEffect, useState } from "react";
import { supabase, soles, kilos, hace, num } from "@/lib/supabase";
import {
  miCentro, materialesDelCentro, preciosMercado, cambiarPrecio, aceptarMaterial,
  movimientosStock, registrarMovimiento,
} from "@/lib/queries";
import {
  Kpi, Card, Chip, Tabla, Th, Td, Empty, ErrorBox,
  SkeletonKpis, SkeletonTabla, SkeletonLista, Cargando, Aviso, type AvisoMsg,
} from "@/components/ui";

const MOV: Record<string, { t: string; c: "success" | "error" | "money" | "text2" }> = {
  entrada: { t: "entrada", c: "success" }, salida: { t: "salida", c: "money" },
  merma: { t: "merma", c: "error" }, ajuste: { t: "ajuste", c: "text2" },
};

export default function Materiales() {
  const [centro, setCentro] = useState<any>(null);
  const [mats, setMats] = useState<any[]>([]);
  const [mercado, setMercado] = useState<any[]>([]);
  const [movs, setMovs] = useState<any[]>([]);
  const [edit, setEdit] = useState<string | null>(null);
  const [buy, setBuy] = useState("");
  const [sell, setSell] = useState("");
  const [aviso, setAviso] = useState<AvisoMsg | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [ocupados, setOcupados] = useState<Set<string>>(new Set());
  const [salidaDe, setSalidaDe] = useState<any>(null);
  const [kgSalida, setKgSalida] = useState("");

  const [primera, setPrimera] = useState(true);

  const cargar = useCallback(async () => {
    try {
      const c = await miCentro();
      if (!c) return setError("Tu cuenta no tiene un centro asignado.");
      setCentro(c);
      const [m, p, mv] = await Promise.all([
        materialesDelCentro(c.id), preciosMercado(), movimientosStock(c.id),
      ]);
      setMats(m); setMercado(p); setMovs(mv); setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "No pudimos leer los materiales.");
    } finally {
      setPrimera(false);
    }
  }, []);
  useEffect(() => { cargar(); }, [cargar]);

  // Un id por fila en vuelo: sin esto un segundo clic duplicaba el movimiento
  // en un libro que, por diseño, no se puede corregir desde el panel.
  const enVuelo = (id: string) => ocupados.has(id);
  const marcar = (id: string, on: boolean) =>
    setOcupados((s) => { const n = new Set(s); on ? n.add(id) : n.delete(id); return n; });

  async function guardar(row: any) {
    // num() acepta coma decimal (1,50) y devuelve null si está vacío —
    // Number("") da 0, que se guardaría como un precio real de cero.
    const b = num(buy), s = num(sell);
    if (b === null || s === null) return setAviso({ ok: false, txt: "Escribe los dos precios. Puedes usar coma: 1,50" });
    if (b < 0 || s < 0) return setAviso({ ok: false, txt: "Los precios no pueden ser negativos." });
    if (enVuelo(row.id)) return;
    marcar(row.id, true);
    try {
      const r = await cambiarPrecio(row.id, b, s);
      setAviso(r.error ? { ok: false, txt: r.error.message } : { ok: true, txt: `Precio de ${row.materials?.name} actualizado. Queda en el histórico.` });
      setEdit(null);
    } finally { marcar(row.id, false); await cargar(); }
  }

  async function toggle(row: any) {
    if (enVuelo(row.id)) return;
    marcar(row.id, true);
    try {
      const r = await aceptarMaterial(row.id, !row.is_accepted);
      if (r.error) setAviso({ ok: false, txt: r.error.message });
    } finally { marcar(row.id, false); await cargar(); }
  }

  async function salida(row: any) {
    if (enVuelo(row.id)) return;
    setSalidaDe(row);
  }

  async function confirmarSalida(row: any, texto: string) {
    const n = num(texto);
    if (n === null || n <= 0) return setAviso({ ok: false, txt: "Pon los kilos que salen. Puedes usar coma: 2,5" });
    if (n > Number(row.current_stock_kg)) return setAviso({ ok: false, txt: `Solo hay ${kilos(row.current_stock_kg)} en stock.` });
    marcar(row.id, true);
    try {
      const { data: s } = await supabase.auth.getSession();
      if (!s.session) { setAviso({ ok: false, txt: "Tu sesión se cerró. Entra de nuevo." }); return; }
      const r = await registrarMovimiento(centro.id, row.material_id, "salida", n,
        Number(row.sell_price_per_kg), "venta a la industria", s.session.user.id);
      setAviso(r.error ? { ok: false, txt: r.error.message } : { ok: true, txt: `Salida registrada: ${n} kg de ${row.materials?.name}.` });
      setSalidaDe(null);
    } catch (e) {
      setAviso({ ok: false, txt: e instanceof Error ? e.message : "No se pudo registrar la salida." });
    } finally { marcar(row.id, false); await cargar(); }
  }

  if (error) return <ErrorBox msg={error} onRetry={cargar} />;

  if (primera) return (
    <Cargando etiqueta="Cargando precios y stock">
      <SkeletonKpis />
      <SkeletonTabla filas={5} cols={7} />
      <SkeletonLista filas={3} alto="h-10" />
    </Cargando>
  );

  const stockTotal = mats.reduce((s, m) => s + Number(m.current_stock_kg ?? 0), 0);
  const valorStock = mats.reduce((s, m) => s + Number(m.current_stock_kg ?? 0) * Number(m.sell_price_per_kg ?? 0), 0);
  const ocupacion = centro?.capacity_kg ? Math.round((Number(centro.current_load_kg) / Number(centro.capacity_kg)) * 100) : null;
  const precioMercado = (code: string) => mercado.find((x) => x.code === code);

  return (
    <>
      <div className="flex gap-5">
        <Kpi label="Stock total" value={stockTotal.toFixed(1)} unit="kg" icon="▣" iconColor="gen"
          chip={`${mats.filter((m) => m.is_accepted).length} materiales`} chipColor="success" hint="aceptados hoy" />
        <Kpi label="Valor de venta" value={soles(valorStock)} icon="◎" iconColor="money"
          chip="a tu precio" chipColor="money" hint="si vendieras todo el stock" />
        <Kpi label="Ocupación" value={ocupacion === null ? "—" : `${ocupacion}%`} icon="▤"
          iconColor={ocupacion !== null && ocupacion > 85 ? "error" : "admin"}
          chip={centro?.capacity_kg ? `${kilos(centro.current_load_kg)} de ${kilos(centro.capacity_kg)}` : "sin capacidad definida"}
          chipColor={ocupacion !== null && ocupacion > 85 ? "error" : "admin"} hint="del centro" />
        <Kpi label="Movimientos" value={String(movs.length)} icon="⇄" iconColor="rider"
          chip="últimos" chipColor="rider" hint="el libro no se edita" />
      </div>

      <Aviso aviso={aviso} />

      <section className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between">
          <h2 className="text-[15px] font-extrabold">Precios y stock de tu centro</h2>
          <span className="text-[11px] text-text2">Cada cambio de precio queda fechado en el histórico.</span>
        </div>
        {mats.length === 0 ? <Empty msg="Este centro todavía no acepta ningún material." /> : (
          <Tabla>
            <thead><tr>
              <Th>MATERIAL</Th><Th w="110px">COMPRA S//kg</Th><Th w="110px">VENTA S//kg</Th>
              <Th w="130px">MERCADO</Th><Th w="110px">STOCK</Th><Th w="110px">PAGA A LA FAMILIA</Th>
              <Th w="210px">ACCIÓN</Th>
            </tr></thead>
            <tbody>
              {mats.map((m) => {
                const mk = precioMercado(m.materials?.code);
                const editando = edit === m.id;
                const dif = mk ? Number(m.buy_price_per_kg) - Number(mk.price_per_kg) : null;
                return (
                  <tr key={m.id} className={m.is_accepted ? "" : "opacity-50"}>
                    <Td>
                      <span className="font-bold">{m.materials?.name}</span>
                      <span className="text-text2 text-[11px] ml-2">{m.materials?.code}</span>
                    </Td>
                    <Td>
                      {editando
                        ? <input value={buy} onChange={(e) => setBuy(e.target.value)} inputMode="decimal"
                            className="w-20 border border-admin rounded-lg px-2 py-1 text-[13px] outline-none" />
                        : <span className="font-extrabold">{Number(m.buy_price_per_kg).toFixed(2)}</span>}
                    </Td>
                    <Td>
                      {editando
                        ? <input value={sell} onChange={(e) => setSell(e.target.value)} inputMode="decimal"
                            className="w-20 border border-admin rounded-lg px-2 py-1 text-[13px] outline-none" />
                        : <span className="font-semibold text-text2">{Number(m.sell_price_per_kg).toFixed(2)}</span>}
                    </Td>
                    <Td>
                      {mk ? (
                        <span className="text-[12px]">
                          {Number(mk.price_per_kg).toFixed(2)}
                          {dif !== null && Math.abs(dif) >= 0.01 && (
                            <span className={`ml-1.5 font-bold ${dif > 0 ? "text-success" : "text-error"}`}>
                              {dif > 0 ? "↑" : "↓"}{Math.abs(dif).toFixed(2)}
                            </span>
                          )}
                        </span>
                      ) : <span className="text-text2">—</span>}
                    </Td>
                    <Td className="font-semibold">{kilos(m.current_stock_kg)}</Td>
                    <Td className="text-text2">
                      {Number(m.materials?.points_per_kg ?? 0).toFixed(0)} pts/kg
                    </Td>
                    <Td>
                      <div className="flex gap-1.5">
                        {editando ? (
                          <>
                            <button onClick={() => guardar(m)} disabled={enVuelo(m.id)}
                              className="bg-success text-white rounded-lg px-3 py-1.5 text-[11px] font-bold disabled:opacity-50">
                              {enVuelo(m.id) ? "…" : "Guardar"}
                            </button>
                            <button onClick={() => setEdit(null)} className="border border-border rounded-lg px-3 py-1.5 text-[11px] font-bold text-text2">Cancelar</button>
                          </>
                        ) : (
                          <>
                            <button onClick={() => { setEdit(m.id); setBuy(String(m.buy_price_per_kg)); setSell(String(m.sell_price_per_kg)); }}
                              className="border border-border rounded-lg px-3 py-1.5 text-[11px] font-bold text-admin">Precio</button>
                            <button onClick={() => salida(m)} disabled={!Number(m.current_stock_kg) || enVuelo(m.id)}
                              className="border border-border rounded-lg px-3 py-1.5 text-[11px] font-bold text-text2 disabled:opacity-40">Salida</button>
                            <button onClick={() => toggle(m)} disabled={enVuelo(m.id)}
                              className="border border-border rounded-lg px-3 py-1.5 text-[11px] font-bold text-text2 disabled:opacity-50">
                              {m.is_accepted ? "No aceptar" : "Aceptar"}
                            </button>
                          </>
                        )}
                      </div>
                    </Td>
                  </tr>
                );
              })}
            </tbody>
          </Tabla>
        )}
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-[15px] font-extrabold">Libro de movimientos</h2>
        {movs.length === 0 ? <Empty msg="Todavía no hay movimientos de stock." /> : (
          <Card className="flex flex-col gap-2">
            {movs.map((mv) => {
              const k = MOV[mv.kind] ?? { t: mv.kind, c: "text2" as const };
              return (
                <div key={mv.id} className="flex items-center gap-3 bg-bg rounded-xl px-3.5 py-2.5">
                  <Chip color={k.c}>{k.t}</Chip>
                  <span className="text-[13px] font-bold">{mv.materials?.name}</span>
                  <span className="text-[13px] font-extrabold">{kilos(mv.weight_kg)}</span>
                  <span className="text-[11px] text-text2 flex-1 truncate">{mv.note}</span>
                  <span className="text-[11px] text-text2">{hace(mv.created_at)}</span>
                </div>
              );
            })}
          </Card>
        )}
        <Card className="bg-[#F9FAFB] text-[12px] text-text2 flex gap-2.5">
          <span>ⓘ</span>
          <p>
            El libro es de solo añadir: un movimiento no se edita ni se borra, se corrige con otro.
            Por eso el stock siempre se puede auditar. Las entradas las escribe la liquidación de
            cada entrega, no se teclean a mano.
          </p>
        </Card>
      </section>

      {salidaDe && (
        <div className="fixed inset-0 z-50 bg-black/40 grid place-items-center p-6"
          onClick={() => setSalidaDe(null)}>
          <div className="ruma-card w-full max-w-sm p-6 flex flex-col gap-4"
            onClick={(ev) => ev.stopPropagation()}>
            <div>
              <h3 className="text-lg font-extrabold">Salida de {salidaDe.materials?.name}</h3>
              <p className="text-xs text-text2 mt-0.5">
                En stock: {kilos(salidaDe.current_stock_kg)} · se registra en el libro y no se
                puede deshacer desde aquí.
              </p>
            </div>
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-text2">Kilos que salen</span>
              <input autoFocus value={kgSalida} inputMode="decimal" placeholder="2,5"
                onChange={(e) => setKgSalida(e.target.value)}
                className="h-11 border border-border rounded-xl px-3.5 text-sm outline-none focus:border-admin" />
            </label>
            <div className="flex gap-2">
              <button disabled={enVuelo(salidaDe.id)}
                onClick={() => { confirmarSalida(salidaDe, kgSalida); setKgSalida(""); }}
                className="flex-1 h-11 bg-admin text-white rounded-xl text-sm font-bold disabled:opacity-60">
                {enVuelo(salidaDe.id) ? "Registrando…" : "Registrar salida"}
              </button>
              <button onClick={() => { setSalidaDe(null); setKgSalida(""); }}
                className="px-5 h-11 border border-border rounded-xl text-sm font-bold text-text2">
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
