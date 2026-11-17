"use client";

import { useCallback, useEffect, useState } from "react";
import { soles, hace } from "@/lib/supabase";
import { pagosPlanPendientes, activarPlan, rechazarPlan } from "@/lib/queries";
import { Card, Chip, Tabla, Th, Td, Empty, ErrorBox, Aviso, SkeletonTabla, Cargando, type AvisoMsg } from "@/components/ui";

const PERIODO: Record<string, string> = { month: "Mensual", year: "Anual", trial: "Prueba" };

export default function Planes() {
  const [filas, setFilas] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [primera, setPrimera] = useState(true);
  const [aviso, setAviso] = useState<AvisoMsg | null>(null);
  const [ocupado, setOcupado] = useState<string | null>(null);
  // Un número de operación / motivo por solicitud.
  const [texto, setTexto] = useState<Record<string, string>>({});

  const cargar = useCallback(async () => {
    try { setFilas(await pagosPlanPendientes()); setError(null); }
    catch (e) { setError(e instanceof Error ? e.message : "No pudimos leer las solicitudes."); }
    finally { setPrimera(false); }
  }, []);
  useEffect(() => { void cargar(); }, [cargar]);

  async function resolver(id: string, activar: boolean) {
    const t = (texto[id] ?? "").trim();
    if (activar && !t) return setAviso({ ok: false, txt: "Anota el número de operación del Yape antes de activar." });
    setOcupado(id); setAviso(null);
    try {
      if (activar) await activarPlan(id, t); else await rechazarPlan(id, t);
      setAviso({ ok: true, txt: activar ? "Plan activado. La familia ya fue avisada." : "Solicitud rechazada." });
      await cargar();
    } catch (e) {
      setAviso({ ok: false, txt: e instanceof Error ? e.message : "No se pudo completar la operación." });
    } finally { setOcupado(null); }
  }

  if (error) return <ErrorBox msg={error} onRetry={cargar} />;
  if (primera) return <Cargando etiqueta="Cargando pagos de planes"><SkeletonTabla filas={4} cols={6} /></Cargando>;

  return (
    <>
      <section className="flex flex-col gap-3">
        <h2 className="text-[15px] font-extrabold">Pagos de planes por confirmar</h2>
        <Aviso aviso={aviso} />
        {filas.length === 0 ? <Empty msg="No hay pagos de planes pendientes." /> : (
          <Tabla>
            <thead><tr>
              <Th>FAMILIA</Th><Th w="110px">CÓDIGO</Th><Th w="90px">PERIODO</Th><Th w="90px">MONTO</Th>
              <Th w="110px">SOLICITADO</Th><Th w="320px">OPERACIÓN YAPE / MOTIVO</Th>
            </tr></thead>
            <tbody>
              {filas.map((f) => (
                <tr key={f.id}>
                  <Td><p className="font-bold">{f.profiles?.display_name ?? "Familia"}</p>
                    <p className="text-[10px] text-text2">{f.plan_code}</p></Td>
                  <Td className="font-mono text-xs">{f.reference}</Td>
                  <Td><Chip color="admin">{PERIODO[f.period] ?? f.period}</Chip></Td>
                  <Td className="font-extrabold text-money">{soles(f.amount)}</Td>
                  <Td className="text-text2 text-xs">{hace(f.created_at)}</Td>
                  <Td>
                    <div className="flex items-center gap-2">
                      <input value={texto[f.id] ?? ""} onChange={(e) => setTexto({ ...texto, [f.id]: e.target.value })}
                        placeholder="N.º de operación" aria-label="Número de operación o motivo"
                        className="w-32 border border-border rounded-lg px-2 py-1.5 text-xs" />
                      <button disabled={ocupado === f.id} onClick={() => void resolver(f.id, true)}
                        className="px-3 py-1.5 rounded-lg bg-admin text-white text-xs font-bold disabled:opacity-50">Activar</button>
                      <button disabled={ocupado === f.id} onClick={() => void resolver(f.id, false)}
                        className="px-3 py-1.5 rounded-lg border border-border text-error text-xs font-bold disabled:opacity-50">Rechazar</button>
                    </div>
                  </Td>
                </tr>
              ))}
            </tbody>
          </Tabla>
        )}
        <Card className="bg-[#F9FAFB] text-[11px] text-text2">
          Activa solo cuando veas llegar el Yape con el código de la fila en el mensaje. Al rechazar, el texto
          del campo queda como motivo (vacío = «Pago no recibido»). La base valida que seas operador.
        </Card>
      </section>
    </>
  );
}
