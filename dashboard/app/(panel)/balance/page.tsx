"use client";

import { useCallback, useEffect, useState } from "react";
import { soles } from "@/lib/supabase";
import { miCentro, balanceCentro, pagosDeclarados } from "@/lib/queries";
import { Kpi, Card, Chip, Tabla, Th, Td, Empty, ErrorBox, SkeletonKpis, SkeletonTabla, Cargando } from "@/components/ui";

const ESTADO = {
  confirmado: "success", pendiente: "money", rechazado: "error",
} as const;

export default function Balance() {
  const [bal, setBal] = useState<any>(null);
  const [pagos, setPagos] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [primera, setPrimera] = useState(true);

  const cargar = useCallback(async () => {
    try {
      const c = await miCentro();
      if (!c) return setError("Tu cuenta no tiene un centro asignado.");
      const [b, p] = await Promise.all([balanceCentro(c.id), pagosDeclarados(c.id)]);
      setBal(b); setPagos(p); setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "No pudimos leer el balance.");
    }
    finally {
      setPrimera(false);
    }
  }, []);
  useEffect(() => { cargar(); }, [cargar]);

  if (error) return <ErrorBox msg={error} onRetry={cargar} />;

  if (primera) return (
    <Cargando etiqueta="Cargando el balance del centro">
      <SkeletonKpis />
      <SkeletonTabla filas={5} cols={6} />
    </Cargando>
  );

  const porConfirmar = pagos.filter((p) => p.status === "pendiente");
  const fecha = (iso: string) =>
    new Date(iso).toLocaleDateString("es-PE", { day: "2-digit", month: "short" });

  return (
    <>
      <div className="flex gap-5">
        <Kpi label="Total liquidado" value={soles(bal?.total_liquidado)} icon="▤" iconColor="admin"
          chip="acumulado" chipColor="admin" hint="lo que el centro debe a Ruma" />
        <Kpi label="Pagado y confirmado" value={soles(bal?.total_pagado)} icon="✓" iconColor="success"
          chip="verificado" chipColor="success" hint="solo pagos confirmados" />
        <Kpi label="Saldo pendiente" value={soles(bal?.saldo_pendiente)} icon="◉" iconColor="error"
          chip="deuda con Ruma" chipColor="error" hint="no descuenta lo no confirmado" />
        <Kpi label="Por confirmar" value={String(porConfirmar.length)} icon="◷" iconColor="money"
          chip={soles(porConfirmar.reduce((s, p) => s + Number(p.amount ?? 0), 0))}
          chipColor="money" hint="comprobantes en revisión" />
      </div>

      <section className="flex flex-col gap-3">
        <h2 className="text-[15px] font-extrabold">Pagos declarados por el centro</h2>
        {pagos.length === 0 ? <Empty msg="El centro todavía no ha declarado ningún pago." /> : (
          <Tabla>
            <thead><tr>
              <Th w="110px">FECHA</Th><Th w="130px">MONTO</Th><Th w="150px">MÉTODO</Th>
              <Th>REFERENCIA</Th><Th w="160px">COMPROBANTE</Th><Th w="140px">ESTADO</Th>
            </tr></thead>
            <tbody>
              {pagos.map((p) => (
                <tr key={p.id}>
                  <Td className="text-text2 font-semibold">{fecha(p.declared_at)}</Td>
                  <Td className="font-extrabold">{soles(p.amount)}</Td>
                  <Td className="text-text2">{p.method}</Td>
                  <Td className="font-semibold">{p.reference ?? "—"}</Td>
                  <Td>
                    {p.voucher_url
                      ? <a href={p.voucher_url} target="_blank" rel="noreferrer" className="text-rider font-semibold text-[11px]">ver comprobante</a>
                      : <span className="text-error text-[11px] font-semibold">sin comprobante</span>}
                  </Td>
                  <Td><Chip color={ESTADO[p.status as keyof typeof ESTADO] ?? "text2"}>{p.status}</Chip></Td>
                </tr>
              ))}
            </tbody>
          </Tabla>
        )}
      </section>

      <Card className="bg-[#F9FAFB] text-[12px] text-text2 flex gap-2.5">
        <span>ⓘ</span>
        <p>
          <b>Saldo pendiente = total liquidado − pagos CONFIRMADOS.</b> Un pago declarado pero sin
          confirmar no reduce la deuda, y «liquidada» no significa «pagada». Confirmar un pago no se
          hace desde este panel: la base revoca esa escritura al centro (migración 26), porque si no
          cualquier acopio declararía sus propias deudas como saldadas. Lo confirma Ruma.
        </p>
      </Card>
    </>
  );
}
