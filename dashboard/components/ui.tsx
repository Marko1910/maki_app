"use client";

/** Piezas compartidas, equivalentes a RumaScaffold/RumaUi en la app Android. */

export function Card({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <div className={`ruma-card p-5 ${className}`}>{children}</div>;
}

/** Un solo mapa de color para todo el panel: Chip, Kpi y Aviso leen de aquí. */
export const TINTE = {
  admin: "bg-admin/10 text-admin", rider: "bg-rider/10 text-rider",
  money: "bg-money/10 text-money", success: "bg-success/10 text-success",
  error: "bg-error/10 text-error", gen: "bg-gen/10 text-gen",
  text2: "bg-text2/10 text-text2",
} as const;
export type Tinte = keyof typeof TINTE;

export function Chip({ children, color = "text2" }: { children: React.ReactNode; color?: Tinte }) {
  return (
    <span className={`${TINTE[color]} rounded-lg px-2.5 py-1 text-[11px] font-bold whitespace-nowrap`}>
      {children}
    </span>
  );
}

/**
 * Resultado de una acción. Lleva `ok` explícito: antes dos páginas deducían el
 * éxito buscando palabras en el texto (`msg.includes("actualizado")`), así que
 * un error que mencionara esa palabra se pintaba de verde.
 */
export type AvisoMsg = { ok: boolean; txt: string };

export function Aviso({ aviso }: { aviso: AvisoMsg | null }) {
  if (!aviso) return null;
  return (
    <p role="status"
      className={`text-xs font-semibold rounded-lg px-3 py-2 ${aviso.ok ? TINTE.success : TINTE.error}`}>
      {aviso.txt}
    </p>
  );
}

/** El KPI del diseño: etiqueta, valor, un chip y una línea de contexto. */
export function Kpi({
  label, value, unit, chip, chipColor = "admin", hint, icon, iconColor = "admin", onClick,
}: {
  label: string; value: string; unit?: string; chip?: string;
  chipColor?: Tinte; hint?: string; icon?: string; iconColor?: Tinte;
  /** Con esto el KPI deja de ser adorno y lleva a lo que está contando. */
  onClick?: () => void;
}) {
  const tint = TINTE;
  const cuerpo = (
    <>
      <div className="flex items-center justify-between">
        <span className="text-[13px] font-semibold text-text2">{label}</span>
        {icon && (
          <span className={`${tint[iconColor]} w-[30px] h-[30px] rounded-[9px] grid place-items-center text-sm`}>
            {icon}
          </span>
        )}
      </div>
      <div className="flex items-baseline gap-2 mt-2.5">
        <span className="text-[30px] font-extrabold leading-none tracking-tight">{value}</span>
        {unit && <span className="text-[13px] font-semibold text-text2">{unit}</span>}
      </div>
      <div className="flex items-center gap-2 mt-2.5 min-w-0">
        {chip && <Chip color={chipColor}>{chip}</Chip>}
        {hint && <span className="text-[11px] text-text2 truncate">{hint}</span>}
      </div>
    </>
  );
  // Un KPI que cuenta algo accionable debería llevar a ese algo.
  return onClick ? (
    <button type="button" onClick={onClick}
      className="ruma-card ruma-press ruma-fila-hover p-5 flex-1 min-w-0 text-left cursor-pointer">
      {cuerpo}
    </button>
  ) : (
    <Card className="flex-1 min-w-0">{cuerpo}</Card>
  );
}

export function Loading({ what = "datos" }: { what?: string }) {
  return <div className="ruma-card p-8 text-center text-sm text-text2">Cargando {what}…</div>;
}

/* ---------------------------------------------------------------------------
   Esqueletos de carga.

   Tienen la FORMA de lo que va a aparecer, no un spinner genérico: el operador
   ve dónde estarán los KPIs y las filas antes de que lleguen, así la pantalla
   no da un salto al cargar. `aria-hidden` porque no son contenido; el estado
   real se anuncia con role="status" en el contenedor.
--------------------------------------------------------------------------- */

/** Barra gris que late. `w` acepta cualquier clase de ancho de Tailwind. */
export function Barra({ w = "w-24", h = "h-4", className = "" }: { w?: string; h?: string; className?: string }) {
  return <span className={`block ${w} ${h} rounded-md bg-border/70 ${className}`} />;
}

export function SkeletonKpis({ n = 4 }: { n?: number }) {
  return (
    <div className="flex gap-5 animate-pulse" aria-hidden>
      {Array.from({ length: n }).map((_, i) => (
        <div key={i} className="ruma-card p-5 flex-1 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <Barra w="w-28" h="h-3" />
            <Barra w="w-[30px]" h="h-[30px]" className="rounded-[9px]" />
          </div>
          <Barra w="w-20" h="h-8" />
          <div className="flex gap-2 items-center">
            <Barra w="w-16" h="h-5" className="rounded-lg" />
            <Barra w="w-24" h="h-3" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function SkeletonTabla({ filas = 4, cols = 6 }: { filas?: number; cols?: number }) {
  return (
    <div className="ruma-card overflow-hidden p-0 border border-border animate-pulse" aria-hidden>
      <div className="flex gap-6 px-5 py-3 bg-[#F9FAFB]">
        {Array.from({ length: cols }).map((_, i) => (
          <Barra key={i} w={i === 0 ? "w-20" : "w-16"} h="h-2.5" />
        ))}
      </div>
      {Array.from({ length: filas }).map((_, f) => (
        <div key={f} className="flex gap-6 items-center px-5 py-4 border-b border-border">
          {Array.from({ length: cols }).map((_, i) => (
            <Barra key={i} w={i === 1 ? "w-32" : i === 0 ? "w-24" : "w-16"} h="h-3.5" />
          ))}
        </div>
      ))}
    </div>
  );
}

/** Lista de tarjetitas (riders, entregas en camino, llamadas). */
export function SkeletonLista({ filas = 3, alto = "h-12" }: { filas?: number; alto?: string }) {
  return (
    <div className="ruma-card p-5 flex flex-col gap-2.5 animate-pulse" aria-hidden>
      {Array.from({ length: filas }).map((_, i) => (
        <div key={i} className={`flex items-center gap-3 bg-bg rounded-xl px-3.5 ${alto}`}>
          <Barra w="w-8" h="h-8" className="rounded-full" />
          <div className="flex-1 flex flex-col gap-1.5">
            <Barra w="w-32" h="h-3" />
            <Barra w="w-20" h="h-2.5" />
          </div>
          <Barra w="w-16" h="h-4" className="rounded-lg" />
        </div>
      ))}
    </div>
  );
}

export function SkeletonMapa({ alto = 300 }: { alto?: number }) {
  return (
    <div className="rounded-[14px] border border-border bg-surface overflow-hidden animate-pulse relative"
      style={{ height: alto }} aria-hidden>
      <div className="absolute inset-0 bg-border/30" />
      <div className="absolute left-3 bottom-3 flex gap-3 bg-white/80 rounded-xl px-3 py-2">
        {Array.from({ length: 4 }).map((_, i) => <Barra key={i} w="w-20" h="h-3" />)}
      </div>
    </div>
  );
}

export function SkeletonBloque({ alto = "h-48" }: { alto?: string }) {
  return <div className={`ruma-card ${alto} animate-pulse`} aria-hidden />;
}

/** Envoltorio accesible: anuncia que se está cargando sin texto visible. */
export function Cargando({ children, etiqueta = "Cargando datos del centro" }:
  { children: React.ReactNode; etiqueta?: string }) {
  return (
    <div role="status" aria-busy="true" aria-label={etiqueta} className="flex flex-col gap-[22px]">
      {children}
    </div>
  );
}

export function Empty({ msg }: { msg: string }) {
  return <div className="ruma-card p-8 text-center text-sm text-text2">{msg}</div>;
}

export function ErrorBox({ msg, onRetry }: { msg: string; onRetry?: () => void }) {
  return (
    <div className="ruma-card p-6 flex items-center justify-between gap-4">
      <p className="text-sm text-error font-medium">{msg}</p>
      {onRetry && (
        <button onClick={onRetry} className="text-xs font-bold text-admin border border-border rounded-lg px-3 py-2">
          Reintentar
        </button>
      )}
    </div>
  );
}

export function Th({ children, w }: { children?: React.ReactNode; w?: string }) {
  return (
    <th style={{ width: w }} className="text-left text-[10px] font-bold tracking-wide text-text2 px-5 py-3 bg-[#F9FAFB]">
      {children}
    </th>
  );
}

export function Td({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <td className={`px-5 py-3.5 border-b border-border text-[13px] ${className}`}>{children}</td>;
}

export function Tabla({ children }: { children: React.ReactNode }) {
  return (
    <div className="ruma-card overflow-hidden p-0 border border-border">
      <table className="w-full border-collapse">{children}</table>
    </div>
  );
}
