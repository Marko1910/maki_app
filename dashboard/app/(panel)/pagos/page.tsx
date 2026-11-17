"use client";

import { useCallback, useEffect, useState } from "react";
import { soles, hace } from "@/lib/supabase";
import { saldosRiders, retirosSolicitados } from "@/lib/queries";
import { Kpi, Card, Chip, Tabla, Th, Td, Empty, ErrorBox, SkeletonKpis, SkeletonTabla, SkeletonLista, Cargando } from "@/components/ui";

export default function Pagos() {
  const [riders, setRiders] = useState<any[]>([]);
  const [retiros, setRetiros] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [primera, setPrimera] = useState(true);

  const cargar = useCallback(async () => {
    try {
      const [r, w] = await Promise.all([saldosRiders(), retirosSolicitados()]);
      setRiders(r); setRetiros(w); setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "No pudimos leer los saldos.");
    }
    finally {
      setPrimera(false);
    }
  }, []);
  useEffect(() => { cargar(); }, [cargar]);

  if (error) return <ErrorBox msg={error} onRetry={cargar} />;

  if (primera) return (
    <Cargando etiqueta="Cargando saldos de los Eco-Riders">
      <SkeletonKpis />
      <div className="flex gap-5 items-start">
        <div className="flex-1 min-w-0"><SkeletonTabla filas={5} cols={4} /></div>
        <div className="w-[340px] shrink-0"><SkeletonLista filas={2} alto="h-20" /></div>
      </div>
    </Cargando>
  );

  const saldoTotal = riders.reduce((s, r) => s + Number(r.earnings_balance ?? 0), 0);
  const devengado = riders.reduce((s, r) => s + Number(r.earnings_lifetime ?? 0), 0);
  const pedido = retiros.reduce((s, w) => s + Number(w.amount ?? 0), 0);
  const nombre = (r: any) => r.profiles?.display_name ?? "Eco-Rider";

  return (
    <>
      <div className="flex gap-5">
        <Kpi label="Saldo por pagar" value={soles(saldoTotal)} icon="▭" iconColor="admin"
          chip={`${riders.filter((r) => Number(r.earnings_balance) > 0).length} riders`}
          chipColor="admin" hint="acumulado sin retirar" />
        <Kpi label="Devengado histórico" value={soles(devengado)} icon="◎" iconColor="money"
          chip="pagado al completar" chipColor="money" hint="por recojo entregado" />
        <Kpi label="Retiros solicitados" value={String(retiros.length)} icon="↥" iconColor="rider"
          chip={soles(pedido)} chipColor="rider" hint="esperan transferencia" />
        <Kpi label="Mínimo de retiro" value={soles(50)} icon="⚐" iconColor="success"
          chip="lo valida la base" chipColor="success" hint="trigger validate_withdrawal" />
      </div>

      <div className="flex gap-5 items-start">
        <section className="flex-1 min-w-0 flex flex-col gap-3">
          <h2 className="text-[15px] font-extrabold">Saldo por Eco-Rider</h2>
          {riders.length === 0 ? <Empty msg="Todavía no hay Eco-Riders con movimientos." /> : (
            <Tabla>
              <thead><tr>
                <Th>ECO-RIDER</Th><Th w="90px">RECOJOS</Th><Th w="120px">DEVENGADO</Th><Th w="120px">SALDO</Th>
              </tr></thead>
              <tbody>
                {riders.map((r) => (
                  <tr key={r.profile_id}>
                    <Td>
                      <div className="flex items-center gap-2.5">
                        <span className="w-8 h-8 rounded-full bg-rider/10 text-rider grid place-items-center text-xs font-extrabold">
                          {nombre(r).charAt(0)}
                        </span>
                        <div>
                          <p className="font-bold leading-tight">{nombre(r)}</p>
                          <p className="text-[10px] text-text2">{r.rider_code ?? "—"}</p>
                        </div>
                      </div>
                    </Td>
                    <Td className="text-text2 font-semibold">{r.total_collections ?? 0}</Td>
                    <Td className="font-bold">{soles(r.earnings_lifetime)}</Td>
                    <Td className="font-extrabold text-money">{soles(r.earnings_balance)}</Td>
                  </tr>
                ))}
              </tbody>
            </Tabla>
          )}
        </section>

        <section className="w-[340px] shrink-0 flex flex-col gap-3">
          <h2 className="text-[15px] font-extrabold">Retiros solicitados</h2>
          {retiros.length === 0 ? <Empty msg="Ningún retiro pendiente." /> : (
            <Card className="flex flex-col gap-2.5">
              {retiros.map((w) => (
                <div key={w.id} className="bg-bg rounded-xl p-3.5 flex flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <span className="text-[13px] font-bold">{w.method}</span>
                    <span className="text-[15px] font-extrabold text-money">{soles(w.amount)}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <Chip color="rider">{w.status}</Chip>
                    <span className="text-[11px] text-text2 font-semibold">{hace(w.requested_at)}</span>
                  </div>
                </div>
              ))}
            </Card>
          )}
          <Card className="bg-[#F9FAFB] text-[11px] text-text2">
            El saldo y el mínimo de S/ 50 los valida la base de datos con el trigger
            <code className="mx-1">validate_withdrawal</code>, no esta pantalla. Marcar un retiro
            como pagado es una operación de Ruma, no del centro.
          </Card>
        </section>
      </div>
    </>
  );
}
