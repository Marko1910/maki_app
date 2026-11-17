"use client";

/**
 * La barra de rango: la pregunta "¿pago bien?" contestada de un vistazo.
 *
 * Antes esto eran tres párrafos de 11 px que nadie lee. Un operador no
 * compara cartón con aluminio — compara cada material contra SU propia
 * evidencia — así que cada barra tiene su escala y lo comparable entre filas
 * es el múltiplo y el color, no el ancho.
 *
 * Cuatro marcas, cada una con función:
 *   banda      el rango de calle documentado
 *   tick fino  el dato más reciente, que es contra el que decide la regla
 *   tick seco  el tope de exportación, cuando se conoce
 *   marcador   lo que paga este centro
 */

const COLOR = {
  ok: { bg: "bg-success", txt: "text-success", anillo: "ring-success/25" },
  aviso: { bg: "bg-money", txt: "text-money", anillo: "ring-money/25" },
  error: { bg: "bg-error", txt: "text-error", anillo: "ring-error/25" },
} as const;

export type EstadoRango = keyof typeof COLOR;

export function RangoPrecio({
  precio, calle, techo, estado, i = 0,
}: {
  precio: number;
  calle: { min: number; max: number; reciente: number };
  techo?: number;
  estado: EstadoRango;
  /** Posición en la lista: sólo alimenta el retardo de la cascada. */
  i?: number;
}) {
  // Datos defensivos: la barra es la pieza que convierte números en una
  // afirmación visual, así que con una entrada imposible no dibuja nada en vez
  // de dibujar algo falso. Sin esto, un precio negativo mandaba el marcador a
  // left:-127% y un rango invertido daba una banda de ancho negativo.
  const lo = Math.min(calle.min, calle.max);
  const hi = Math.max(calle.min, calle.max);
  const finitos = [precio, lo, hi, techo].every((v) => v === undefined || Number.isFinite(v));
  // El 1.12 deja aire a la derecha para que el marcador del valor más alto no
  // quede pegado al borde, donde su etiqueta se saldría de la tarjeta.
  const tope = Math.max(techo ?? 0, precio, hi) * 1.12;
  if (!finitos || precio < 0 || tope <= 0) return null;

  const pct = (v: number) => `${Math.max(0, Math.min(100, (v / tope) * 100))}%`;
  // El ancho se calcula con los porcentajes ya recortados, no con la resta
  // cruda: así nunca puede salir negativo aunque el recorte mueva un extremo.
  const ancho = `${Math.max(0, Math.min(100, (hi / tope) * 100) - Math.min(100, (lo / tope) * 100))}%`;
  const c = COLOR[estado];

  return (
    <div className="pt-6 pb-1" style={{ "--i": i } as React.CSSProperties}>
      <div className="relative h-2.5">
        <div className="absolute inset-0 rounded-full bg-border/70" />

        {/* Rango de calle documentado. */}
        <div
          className="ruma-medir absolute top-0 h-2.5 rounded-full bg-gen/25"
          style={{ left: pct(lo), width: ancho, "--i": i } as React.CSSProperties}
        />

        {/* El dato más reciente: la regla decide contra éste, así que se ve. */}
        <div
          className="absolute -top-0.5 h-3.5 w-[2px] rounded-full bg-gen"
          style={{ left: pct(calle.reciente) }}
          title="dato de calle más reciente"
        />

        {/* Tope de exportación: nadie paga por encima y sigue ganando. */}
        {techo !== undefined && (
          <div
            className="absolute -top-1 h-4.5 w-[2px] rounded-full bg-text2/45"
            style={{ left: pct(techo) }}
            title="tope de exportación"
          />
        )}

        {/* Lo que paga este centro. Sin etiqueta de precio: la cifra grande de
            la fila ya la da, y repetirla aquí sólo añadía ruido. */}
        <div className="ruma-marcador absolute -top-[7px] -translate-x-1/2"
          style={{ left: pct(precio), "--i": i } as React.CSSProperties}>
          <div className={`h-6 w-[3px] rounded-full ${c.bg} ring-4 ${c.anillo}`} />
        </div>
      </div>
    </div>
  );
}

/** Leyenda, una sola vez para toda la lista en lugar de por fila. */
export function LeyendaRango() {
  return (
    <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 text-[11px] text-text2">
      <span className="flex items-center gap-1.5">
        <span className="h-2.5 w-5 rounded-full bg-gen/25" /> rango de calle documentado
      </span>
      <span className="flex items-center gap-1.5">
        <span className="h-3 w-[2px] rounded-full bg-gen" /> dato más reciente
      </span>
      <span className="flex items-center gap-1.5">
        <span className="h-3 w-[2px] rounded-full bg-text2/45" /> tope de exportación
      </span>
      <span className="flex items-center gap-1.5">
        <span className="h-3 w-[3px] rounded-full bg-text ring-2 ring-text/15" /> lo que paga tu centro
      </span>
    </div>
  );
}
