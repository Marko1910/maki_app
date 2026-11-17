"use client";

import { useCallback, useEffect, useState } from "react";
import { soles, kilos } from "@/lib/supabase";
import { miCentro, kilosPorMaterial, liquidadasHoy, margen } from "@/lib/queries";
import { Kpi, Card, Empty, ErrorBox, SkeletonKpis, SkeletonBloque, Cargando } from "@/components/ui";

const PERIODOS = [
  { label: "Últimos 30 días", dias: 30 },
  { label: "Últimos 7 días", dias: 7 },
  { label: "Trimestre", dias: 90 },
];

const COLOR: Record<string, string> = {
  PET: "var(--color-gen)", CAR: "#B07B4F", VID: "#5B8FB9",
  ALU: "#9CA3AF", PIL: "var(--color-error)",
};

export default function Reportes() {
  const [dias, setDias] = useState(30);
  const [items, setItems] = useState<any[]>([]);
  const [hoy, setHoy] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [primera, setPrimera] = useState(true);

  const cargar = useCallback(async () => {
    try {
      const c = await miCentro();
      if (!c) return setError("Tu cuenta no tiene un centro asignado.");
      const desde = new Date();
      desde.setDate(desde.getDate() - dias);
      const [i, h] = await Promise.all([kilosPorMaterial(c.id, desde), liquidadasHoy(c.id)]);
      setItems(i); setHoy(h); setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "No pudimos construir el reporte.");
    }
    finally {
      setPrimera(false);
    }
  }, [dias]);
  useEffect(() => { cargar(); }, [cargar]);

  if (error) return <ErrorBox msg={error} onRetry={cargar} />;

  if (primera) return (
    <Cargando etiqueta="Cargando los reportes del periodo">
      <SkeletonKpis />
      <div className="flex gap-5 items-start">
        <div className="flex-1 min-w-0"><SkeletonBloque alto="h-[290px]" /></div>
        <div className="w-[340px] shrink-0"><SkeletonBloque alto="h-[290px]" /></div>
      </div>
    </Cargando>
  );

  // Kilos por material y reparto de calidad, desde el pesaje real.
  const porMaterial = new Map<string, { nombre: string; kg: number }>();
  const porGrado = new Map<string, number>();
  let kgTotal = 0;
  for (const it of items) {
    const code = it.materials?.code ?? "—";
    const kg = Number(it.weight_kg ?? 0);
    kgTotal += kg;
    const prev = porMaterial.get(code);
    porMaterial.set(code, { nombre: it.materials?.name ?? code, kg: (prev?.kg ?? 0) + kg });
    porGrado.set(it.quality_grade, (porGrado.get(it.quality_grade) ?? 0) + kg);
  }
  const barras = [...porMaterial.entries()].sort((a, b) => b[1].kg - a[1].kg);
  const maxKg = barras[0]?.[1].kg ?? 1;
  const m = margen(hoy as any);

  return (
    <>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-[13px] font-semibold text-text2 mr-1">Periodo</span>
          {PERIODOS.map((p) => (
            <button key={p.dias} onClick={() => setDias(p.dias)}
              className={`rounded-[9px] px-3.5 py-2 text-xs font-bold border
                ${dias === p.dias ? "bg-admin text-white border-admin" : "bg-surface text-text2 border-border"}`}>
              {p.label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex gap-5">
        <Kpi label="Material procesado" value={kgTotal.toFixed(1)} unit="kg" icon="▣" iconColor="gen"
          chip={`${items.length} pesajes`} chipColor="success" hint="de lotes liquidados" />
        <Kpi label="Margen neto de hoy" value={soles(m.total)} icon="◉"
          iconColor={m.total < 0 ? "error" : "success"}
          chip={m.negativos ? `${m.negativos} en pérdida` : "sin pérdidas"}
          chipColor={m.negativos ? "error" : "success"} hint="tras el costo del rider" />
        <Kpi label="Materiales distintos" value={String(porMaterial.size)} icon="◈" iconColor="rider"
          chip="en el periodo" chipColor="rider" hint="con pesaje registrado" />
        <Kpi label="Grado A" value={`${Math.round(((porGrado.get("A") ?? 0) / (kgTotal || 1)) * 100)}%`}
          icon="★" iconColor="money" chip="paga 100%" chipColor="money" hint="del peso recibido" />
      </div>

      <div className="flex gap-5 items-start">
        <Card className="flex-1 min-w-0 flex flex-col gap-4">
          <h2 className="text-[15px] font-extrabold">Kilos recibidos por material</h2>
          {barras.length === 0 ? (
            <Empty msg="Todavía no hay lotes liquidados en este periodo." />
          ) : (
            <div className="flex items-end gap-5 h-[200px]">
              {barras.map(([code, v]) => (
                <div key={code} className="flex-1 flex flex-col items-center gap-2 justify-end h-full">
                  <span className="text-xs font-extrabold">{kilos(v.kg)}</span>
                  <div className="w-full rounded-t-lg"
                    style={{ height: `${Math.max(4, (v.kg / maxKg) * 150)}px`, background: COLOR[code] ?? "var(--color-text2)" }} />
                  <span className="text-xs font-semibold text-text2">{v.nombre}</span>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card className="w-[340px] shrink-0 flex flex-col gap-3.5">
          <h2 className="text-[15px] font-extrabold">Calidad del lote</h2>
          <p className="text-[11px] text-text2">El grado escala el precio pagado al centro.</p>
          {(["A", "B", "C"] as const).map((g) => {
            const kg = porGrado.get(g) ?? 0;
            const pct = Math.round((kg / (kgTotal || 1)) * 100);
            const col = g === "A" ? "var(--color-success)" : g === "B" ? "var(--color-money)" : "var(--color-error)";
            const factor = g === "A" ? "100%" : g === "B" ? "85%" : "60%";
            return (
              <div key={g} className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-text2">Grado {g} · paga {factor}</span>
                  <span className="text-[13px] font-extrabold">{pct}%</span>
                </div>
                <div className="h-2 rounded bg-border overflow-hidden">
                  <div style={{ width: `${pct}%`, background: col }} className="h-full rounded" />
                </div>
              </div>
            );
          })}
        </Card>
      </div>

      <Card className="bg-[#F9FAFB] text-[12px] text-text2 flex gap-2.5">
        <span>ⓘ</span>
        <p>
          Todo sale del pesaje real (<code>delivery_items</code>) de lotes ya liquidados. No se
          muestran puntualidad ni rentabilidad del centro: el esquema no guarda ventas a la
          industria ni gastos, así que cualquier cifra de ese tipo sería inventada.
        </p>
      </Card>
    </>
  );
}
