"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { soles, hace } from "@/lib/supabase";
import {
  revisarPrecio, REFERENCIAS, ETIQUETA_CONFIANZA,
  ANCLA_ALUMINIO, SOLES_POR_KG_ALUMINIO_PRIMARIO, type PrecioRed,
} from "@/lib/mercado";
import { miCentro, preciosDeLaRed, noticiasMercado } from "@/lib/queries";
import { RangoPrecio, LeyendaRango, type EstadoRango } from "@/components/RangoPrecio";
import {
  Kpi, Card, Chip, Empty, ErrorBox, SkeletonKpis, SkeletonBloque, SkeletonLista, Cargando,
} from "@/components/ui";

/** Lo que pide acción va primero; lo que no tiene precio, al final. */
const PESO = { error: 0, aviso: 1, ok: 2 } as const;

export default function Mercado() {
  const [red, setRed] = useState<PrecioRed[]>([]);
  const [news, setNews] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [primera, setPrimera] = useState(true);
  /** Acordeón de uno: dos detalles abiertos vuelven a ser el muro de texto. */
  const [abierto, setAbierto] = useState<string | null>(null);
  const [notaAbierta, setNotaAbierta] = useState(false);
  const filas = useRef(new Map<string, HTMLDivElement>());
  /** Descarta respuestas viejas: dos clics en Reintentar podían dejar en
   *  pantalla la primera respuesta, o un fallo tardío tapar una carga buena. */
  const seq = useRef(0);

  const cargar = useCallback(async () => {
    const mio = ++seq.current;
    try {
      const centro = await miCentro();
      const [r, n] = await Promise.all([preciosDeLaRed(centro?.id ?? null), noticiasMercado()]);
      if (mio !== seq.current) return;
      setRed(r); setNews(n); setError(null);
    } catch (e) {
      if (mio !== seq.current) return;
      setError(e instanceof Error ? e.message : "No pudimos leer los precios de la red.");
    } finally {
      if (mio === seq.current) setPrimera(false);
    }
  }, []);
  useEffect(() => { cargar(); }, [cargar]);

  if (error) return <ErrorBox msg={error} onRetry={cargar} />;

  if (primera) return (
    <Cargando etiqueta="Cargando el mercado">
      <SkeletonKpis n={3} />
      <SkeletonBloque alto="h-[460px]" />
      <SkeletonLista filas={3} alto="h-16" />
    </Cargando>
  );

  const items = Object.values(REFERENCIAS).map((ref) => {
    const r = red.find((x) => x.code === ref.code) ?? null;
    const precio = r?.miPrecio ?? null;
    const rev = revisarPrecio(ref.code, precio);
    return { ref, r, precio, rev };
  }).sort((a, b) =>
    (a.rev ? PESO[a.rev.nivel] : 3) - (b.rev ? PESO[b.rev.nivel] : 3)
    || a.ref.code.localeCompare(b.ref.code),
  );

  const evaluables = items.filter((f) => f.rev);
  const alertas = items.filter((f) => f.rev?.nivel === "error");
  const avisos = items.filter((f) => f.rev?.nivel === "aviso");
  const enOrden = items.filter((f) => f.rev?.nivel === "ok");
  const uniformes = items.filter((f) => f.r?.uniforme).map((f) => f.ref.code);

  function irA(code: string) {
    setAbierto(code);
    const quieto = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    filas.current.get(code)?.scrollIntoView({ behavior: quieto ? "auto" : "smooth", block: "center" });
  }

  return (
    <>
      <div className="flex gap-5">
        <Kpi label="Precios en orden" value={`${enOrden.length}`} unit={`de ${evaluables.length}`}
          icon="✓" iconColor="success" chip="dentro de la evidencia" chipColor="success"
          hint={`${items.length - evaluables.length} sin cotización que comparar`} />
        <Kpi label="Necesitan tu atención" value={`${alertas.length + avisos.length}`}
          icon={alertas.length ? "◉" : avisos.length ? "⚠" : "✓"}
          iconColor={alertas.length ? "error" : avisos.length ? "money" : "success"}
          chip={[...alertas, ...avisos].map((a) => a.ref.code).join(" · ") || "ninguno"}
          chipColor={alertas.length ? "error" : avisos.length ? "money" : "success"}
          hint={alertas.length + avisos.length ? "toca para ir al primero" : "nada por revisar"}
          onClick={alertas.length + avisos.length
            ? () => irA((alertas[0] ?? avisos[0]).ref.code)
            : undefined} />
        <Kpi label="Aluminio internacional" value={soles(SOLES_POR_KG_ALUMINIO_PRIMARIO)} unit="/kg"
          icon="◎" iconColor="money" chip={ANCLA_ALUMINIO.mes} chipColor="money"
          hint="lingote primario, no la lata" />
      </div>

      {/* El aviso de uniformidad se decía entero en CADA tarjeta: cuatro veces
          el mismo párrafo. Dicho una vez vale igual y deja leer la pantalla. */}
      {uniformes.length > 1 && (
        <Card className="ruma-in flex items-start gap-3 border-l-4 border-money bg-money/5">
          <span className="text-money text-base leading-none mt-0.5">⚠</span>
          <p className="text-[12px] leading-relaxed">
            <b>Los acopios de la red declaran el mismo precio al céntimo</b> en{" "}
            {uniformes.join(", ")}. Eso casi nunca es el mercado coincidiendo: suele ser el valor
            del catálogo inicial que nadie ha cambiado. Hasta que cada acopio declare el suyo,
            trátalos como precios <b>propuestos</b>, no observados.
          </p>
        </Card>
      )}

      <section className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between gap-4">
          <h2 className="text-[15px] font-extrabold">Lo que paga tu centro, contra la evidencia</h2>
          <span className="text-[11px] text-text2">
            Cada barra tiene su escala: se compara un material con su propia referencia, no con otro.
          </span>
        </div>

        <Card><LeyendaRango /></Card>

        <div className="flex flex-col gap-3">
          {items.map(({ ref, r, precio, rev }, i) => {
            const et = ETIQUETA_CONFIANZA[ref.confianza];
            const abierta = abierto === ref.code;
            const sinPrecio = precio === null || precio <= 0;
            const nivel: EstadoRango = (rev?.nivel ?? "ok") as EstadoRango;
            const borde = rev?.nivel === "error" ? "border-l-error"
              : rev?.nivel === "aviso" ? "border-l-money"
              : rev?.nivel === "ok" ? "border-l-success" : "border-l-border";

            return (
              <div key={ref.code} ref={(el) => { if (el) filas.current.set(ref.code, el); }}
                className={`ruma-in ruma-card border-l-4 ${borde} overflow-hidden`}
                style={{ "--i": i } as React.CSSProperties}>

                <button type="button" onClick={() => setAbierto(abierta ? null : ref.code)}
                  className="ruma-fila-hover ruma-press w-full text-left px-5 pt-4 pb-3 cursor-pointer"
                  aria-expanded={abierta}>
                  <div className="flex items-start gap-4">
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2.5 flex-wrap">
                        <span className="text-[16px] font-extrabold">{r?.name ?? ref.nombre}</span>
                        <Chip color="text2">{ref.code}</Chip>
                        {rev?.mult && (
                          <Chip color={rev.nivel === "error" ? "error" : rev.nivel === "aviso" ? "money" : "success"}>
                            {rev.mult} el dato reciente
                          </Chip>
                        )}
                      </div>
                      <p className="text-[11px] text-text2 mt-1">
                        {/* La cifra grande es la de ESTE centro. Lo que hace la
                            red va aquí al lado, como contexto, no como si fuera
                            el precio propio. */}
                        {!r?.loAcepto ? "tu centro no acepta este material"
                          : sinPrecio ? "tu centro no le ha puesto precio"
                          : `la red paga ${r.n === 1 ? "esto en 1 acopio" :
                              r.uniforme ? `lo mismo en los ${r.n} acopios`
                              : `una mediana de ${soles(r.mediana)} en ${r.n} acopios · ${soles(r.min)}–${soles(r.max)}`}`}
                        {r?.actualizado && ` · actualizado ${hace(r.actualizado)}`}
                        {r && r.descartados > 0 &&
                          ` · ${r.descartados} ${r.descartados === 1 ? "acopio declaró un precio" : "acopios declararon precios"} que no se pudo usar`}
                      </p>
                    </div>

                    <div className="text-right shrink-0 flex items-center gap-3">
                      <div>
                        <p className={`text-[26px] font-extrabold leading-none ${sinPrecio ? "text-text2" : ""}`}>
                          {sinPrecio ? "—" : soles(precio)}
                        </p>
                        <p className="text-[10px] text-text2 mt-1">{ref.code === "PIL" ? "incentivo cívico" : "compra · por kg"}</p>
                      </div>
                      <Chevron abierto={abierta} />
                    </div>
                  </div>

                  {ref.calle && !sinPrecio && (
                    <RangoPrecio precio={precio!} calle={ref.calle} techo={ref.techo} estado={nivel} i={i} />
                  )}
                </button>

                {rev && rev.nivel !== "ok" && (
                  <div className={`mx-5 mb-4 rounded-xl px-3.5 py-3 flex items-start gap-3
                    ${rev.nivel === "error" ? "bg-error/8" : "bg-money/8"}`}>
                    <span className={`text-base leading-none mt-0.5
                      ${rev.nivel === "error" ? "text-error" : "text-money"}`}>
                      {rev.nivel === "error" ? "◉" : "⚠"}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="text-[12px] leading-relaxed">{rev.txt}</p>
                      {rev.accion && (
                        <Link href="/materiales"
                          className={`ruma-press inline-block mt-2 rounded-lg px-3 py-1.5 text-[11px] font-bold text-white
                            ${rev.nivel === "error" ? "bg-error" : "bg-money"}`}>
                          {rev.accion} →
                        </Link>
                      )}
                    </div>
                  </div>
                )}

                {/* Lo que antes era un muro: fuente, nota, fecha y enlace. Sigue
                    entero — es el rigor de la pantalla — pero bajo demanda. */}
                <div className="ruma-fold" data-abierto={abierta} inert={!abierta}>
                  <div>
                    <div className="mx-5 pb-4 flex flex-col gap-2.5 border-t border-border">
                      <div className="flex items-center gap-2 pt-3">
                        <Chip color={et.color}>{et.txt}</Chip>
                        <span className="text-[11px] text-text2">dato de {ref.fecha}</span>
                      </div>
                      <Dato k="De dónde sale" v={ref.fuente} />
                      {ref.calle && (
                        <Dato k="En la calle"
                          v={`S/ ${ref.calle.min.toFixed(2)}–${ref.calle.max.toFixed(2)} · el más reciente es S/ ${ref.calle.reciente.toFixed(2)} — ${ref.calle.nota}`} />
                      )}
                      {ref.techo !== undefined && (
                        <Dato k="Tope de exportación"
                          v={`${soles(ref.techo)}/kg · es el precio FOB real de la partida; por encima de ahí el kilo no se recupera al venderlo`} />
                      )}
                      {ref.code === "ALU" && (
                        <Dato k="Ancla internacional"
                          v={`${soles(SOLES_POR_KG_ALUMINIO_PRIMARIO)}/kg de lingote primario (${ANCLA_ALUMINIO.mes}). Una lata usada no es lingote: sirve de techo del mercado, no de precio de compra.`} />
                      )}
                      {ref.confianza === "sin_cotizacion" && ref.code !== "PIL" && (
                        <Dato k="Por qué no hay número"
                          v="No existe cotización pública de este material en Perú ni índice internacional aplicable: es local porque el flete se come su valor. El precio sale de negociar con el acopio." />
                      )}
                      {ref.code === "PIL" && (
                        <Dato k="Por qué vale cero"
                          v="Nadie compra pilas: su recojo cuesta dinero. Los puntos que paga Ruma son un incentivo cívico, no la compra de un material." />
                      )}
                      {ref.url && (
                        <a href={ref.url} target="_blank" rel="noreferrer"
                          className="ruma-press self-start text-[11px] font-bold text-rider hover:underline">
                          Abrir la fuente ↗
                        </a>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-[15px] font-extrabold">Qué está pasando en el mercado</h2>
        {news.length === 0 ? (
          <Empty msg="Todavía no hay notas de mercado publicadas." />
        ) : (
          <Card className="flex flex-col gap-2.5">
            {news.map((n, i) => (
              <div key={n.id} className="ruma-in flex items-start gap-3 bg-bg rounded-xl px-3.5 py-3"
                style={{ "--i": i } as React.CSSProperties}>
                <Chip color={n.kind === "price" ? "money" : n.kind === "demand" ? "rider" : "gen"}>
                  {n.kind}
                </Chip>
                <div className="min-w-0 flex-1">
                  <p className="text-[13px] font-bold leading-snug">{n.title}</p>
                  {n.summary && <p className="text-[11px] text-text2 mt-0.5">{n.summary}</p>}
                  {n.source && (
                    <p className="text-[10px] text-text2 mt-1">
                      {n.source}
                      {n.url && (
                        <a href={n.url} target="_blank" rel="noreferrer"
                          className="ml-2 text-rider font-bold hover:underline">abrir</a>
                      )}
                    </p>
                  )}
                </div>
                <span className="text-[11px] text-text2 whitespace-nowrap">{hace(n.published_at)}</span>
              </div>
            ))}
          </Card>
        )}
      </section>

      {/* Tres párrafos fijos al pie empujaban el contenido útil fuera de la
          pantalla. Se queda la frase que hay que leer; el resto, a un clic. */}
      <Card className="bg-[#F9FAFB] text-[12px] text-text2">
        <button type="button" onClick={() => setNotaAbierta(!notaAbierta)}
          className="ruma-press w-full text-left flex items-center gap-2.5 cursor-pointer"
          aria-expanded={notaAbierta}>
          <span>ⓘ</span>
          <span className="flex-1">
            <b className="text-text">Esta pantalla no tiene un ticker porque en Perú no existe uno.</b>{" "}
            Ningún organismo publica precios de reciclables.
          </span>
          <Chevron abierto={notaAbierta} />
        </button>
        <div className="ruma-fold" data-abierto={notaAbierta} inert={!notaAbierta}>
          <div>
            <div className="flex flex-col gap-1.5 pt-3 pl-6">
              <p>
                MINAM, SIGERSOL, INEI y PRODUCE publican toneladas, no precios. La propia guía
                metodológica del MINAM le pide a cada municipalidad que encueste su mercado local.
              </p>
              <p>
                Para el aluminio sí hay ancla: el Pink Sheet del Banco Mundial, que publica el precio
                LME bajo licencia CC BY 4.0 y por eso se puede mostrar aquí. El dato de la LME directo
                no: su licencia de distribución cuesta USD 4.000 de alta más USD 5.000 al año.
              </p>
              <p>
                Para PET, cartón y vidrio la única fuente veraz es lo que declaran los acopios de esta
                red. Por eso cada precio lleva cuántos acopios lo sostienen y de cuándo es. Con un solo
                acopio no es un mercado: es un acopio.
              </p>
            </div>
          </div>
        </div>
      </Card>
    </>
  );
}

function Chevron({ abierto }: { abierto: boolean }) {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden
      className="ruma-chevron shrink-0 text-text2 transition-transform duration-200"
      style={{ transform: abierto ? "rotate(180deg)" : "none", transitionTimingFunction: "var(--ease-out)" }}>
      <path d="M3.5 5.5L7 9l3.5-3.5" stroke="currentColor" strokeWidth="1.8"
        strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function Dato({ k, v }: { k: string; v: string }) {
  return (
    <p className="text-[11px] leading-relaxed">
      <b className="text-text">{k}.</b> <span className="text-text2">{v}</span>
    </p>
  );
}
