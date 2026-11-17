/**
 * MERCADO VIVO — LAS REGLAS
 *
 * Aquí no se habla con la base: sólo referencias y la regla que decide si un
 * precio está fuera de rango. Las consultas viven en `queries.ts`, con las del
 * resto del panel, y eso es lo que deja probar esta lógica sin levantar nada
 * (ver `mercado.check.ts`).
 *
 * MERCADO VIVO — de dónde sale cada precio, y con cuánta confianza.
 *
 * Hallazgo que define este archivo: NO existe índice oficial ni público de
 * precios de reciclables en Perú. Ni MINAM, ni SIGERSOL, ni INEI, ni PRODUCE
 * publican precio: solo toneladas. La propia Guía Metodológica del MINAM le
 * dice a cada municipalidad que encueste su mercado local.
 *
 * Consecuencia: para PET, cartón y vidrio la ÚNICA fuente veraz en Perú es lo
 * que declaran los acopios de la red. Para aluminio sí hay ancla internacional
 * utilizable. Y hay una fuente que NO se puede usar: la LME cobra licencia de
 * distribución (USD 4.000 de alta + USD 5.000/año) para mostrar su dato en una
 * app, así que aquí no aparece ni una cifra LME directa.
 *
 * La regla, tomada de Reaquila (la plataforma de la región que mejor lo hace):
 * si no hay dato reciente, se dice y NO se publica número.
 */

export type Confianza = "alta" | "media" | "baja" | "sin_cotizacion";

export type Referencia = {
  code: string;
  /** Nombre legible. La red no devuelve los materiales sin precio. */
  nombre: string;
  /** Qué sostiene el precio, en una línea que el operador pueda leer. */
  fuente: string;
  url?: string;
  /** Fecha del dato, no de la consulta. */
  fecha: string;
  confianza: Confianza;
  /** Cota superior conocida (S//kg): por encima de esto estamos pagando de más. */
  techo?: number;
  /**
   * Precio de calle documentado (S//kg). `reciente` es el dato MÁS NUEVO y
   * fiable del rango, que es contra el que hay que comparar: usar el máximo
   * deja pasar precios inflados sostenidos por una cifra vieja o débil.
   *
   * `fecha` es la del dato de calle, y es SUYA: el `fecha` de arriba describe
   * otra evidencia (en PET, la cota de exportación), así que citarlo aquí
   * rejuvenecía el dato con el que se justifica una alerta. Va opcional porque
   * donde no hay fecha documentada no se inventa una.
   */
  calle?: { min: number; max: number; reciente: number; fecha?: string; nota: string };
};

/**
 * Ancla internacional del ALUMINIO: World Bank "Pink Sheet", que publica el
 * precio LME de lingote primario bajo licencia CC BY 4.0 — redistribuible, a
 * diferencia del dato LME directo. Mensual.
 *
 * ACTUALIZAR cada mes con el PDF nuevo. Mientras no se actualice, la pantalla
 * muestra la fecha de este dato y envejece a la vista: es preferible a un
 * número que finge estar vivo.
 *
 * OJO con el modelo: esto cotiza LINGOTE PRIMARIO. Una lata usada no es
 * lingote. El factor de abajo es la proporción observada, no una conversión.
 */
export const ANCLA_ALUMINIO = {
  usd_por_tonelada: 3283,
  mes: "septiembre 2026",
  fuente: "World Bank Commodities Price Data (Pink Sheet), 2-oct-2026 · CC BY 4.0",
  url: "https://www.worldbank.org/en/research/commodity-markets",
  /** S/ por USD usado para convertir. Cámbialo cuando se mueva. */
  tipo_cambio: 3.44,
};

export const SOLES_POR_KG_ALUMINIO_PRIMARIO =
  (ANCLA_ALUMINIO.usd_por_tonelada / 1000) * ANCLA_ALUMINIO.tipo_cambio;

/**
 * Qué sostiene el precio de cada material, hoy. Las cotas vienen de evidencia
 * verificable: exportación real (SUNAT vía PROMPERÚ) y precio de calle
 * documentado con fecha y autor.
 */
export const REFERENCIAS: Record<string, Referencia> = {
  ALU: {
    code: "ALU",
    nombre: "Aluminio",
    fuente: "Ancla: aluminio primario del Pink Sheet (Banco Mundial). Cota: exportación peruana real",
    url: "https://exportemos.pe/descubre-oportunidades-de-exportacion/producto/7602000000",
    fecha: "sep 2026 (ancla) · ene–jul 2026 (exportación)",
    confianza: "alta",
    techo: 8.04,   // USD 2,34/kg FOB de exportación peruana, partida 7602
    calle: { min: 5.0, max: 5.5, reciente: 5.5, nota: "lata prensada, intermediario peruano; sin fecha documentada" },
  },
  PET: {
    code: "PET",
    nombre: "PET",
    fuente: "Sin índice aplicable a Perú. Precio declarado por los acopios de la red",
    url: "https://exportemos.pe/descubre-oportunidades-de-exportacion/producto/3915900000",
    fecha: "cota de exportación: ene–jul 2026",
    confianza: "media",
    techo: 2.56,   // USD 0,74/kg FOB, partida mixta de desechos plásticos
    calle: { min: 0.22, max: 0.7, reciente: 0.7, fecha: "Lima 2024", nota: "Trujillo 2014 (oficial) y Lima 2024 (prensa, dato débil)" },
  },
  CAR: {
    code: "CAR",
    nombre: "Cartón",
    fuente: "Sin índice ni señal de exportación. Solo precio declarado por la red",
    url: "https://www.actualidadambiental.pe/que-significa-ser-reciclador-en-lima-2-historias-sobre-formalizacion-cuidado-del-ambiente-y-deuda-pendiente/",
    fecha: "jun 2026",
    confianza: "baja",
    calle: { min: 0.15, max: 0.4, reciente: 0.15, fecha: "jun 2026", nota: "lo declara un dirigente de la Red de Recicladores de Lima" },
  },
  VID: {
    code: "VID",
    nombre: "Vidrio",
    fuente: "Sin cotización pública en Perú ni índice internacional aplicable",
    url: "https://dialnet.unirioja.es/descarga/articulo/6232133.pdf",
    fecha: "2017 (estudio UNALM, Lima Norte)",
    confianza: "sin_cotizacion",
    calle: { min: 0.05, max: 0.15, reciente: 0.1, fecha: "2017", nota: "cadena de vidrio en Lima Norte, Anales Científicos UNALM 2017" },
  },
  PIL: {
    code: "PIL",
    nombre: "Pilas",
    fuente: "No es un precio: nadie compra pilas. Es un incentivo cívico",
    fecha: "—",
    confianza: "sin_cotizacion",
  },
};

export const ETIQUETA_CONFIANZA: Record<Confianza, { txt: string; color: "success" | "money" | "error" | "text2" }> = {
  alta: { txt: "ancla verificada", color: "success" },
  media: { txt: "referencia de la red", color: "money" },
  baja: { txt: "dato escaso", color: "error" },
  sin_cotizacion: { txt: "sin cotización pública", color: "text2" },
};

export type PrecioRed = {
  code: string;
  name: string;
  /**
   * Lo que paga ESTE centro. La pantalla se titula "lo que paga tu centro" y
   * antes evaluaba la mediana de toda la red, así que un acopio que pagara el
   * triple se veía a sí mismo en verde mientras sus tres vecinos le sostenían
   * la mediana. El veredicto se calcula sobre este número.
   */
  miPrecio: number | null;
  /** El centro no acepta el material (no hay fila suya en center_materials). */
  loAcepto: boolean;
  /** Mediana de lo que declaran los acopios. Mediana, no media: un acopio con
   *  un precio raro no debe mover la referencia de toda la red. */
  mediana: number | null;
  min: number | null;
  max: number | null;
  /** Cuántos acopios lo sostienen. Con n=1 no es "el mercado", es un acopio. */
  n: number;
  /** Cuándo se tocó por última vez alguno de esos precios. */
  actualizado: string | null;
  /**
   * Todos los acopios declaran EXACTAMENTE lo mismo. Con n>1 eso casi nunca es
   * un mercado poniéndose de acuerdo al céntimo: es el valor del catálogo
   * inicial que nadie ha tocado. Llamarlo "mediana de 4 acopios" lo haría pasar
   * por consenso, así que la pantalla lo dice.
   */
  uniforme: boolean;
  /**
   * Acopios cuyo precio no se pudo usar (nulo, negativo, no numérico). Antes se
   * descartaban en silencio: con [0, 1, 1] la pantalla decía "el mismo precio
   * en los 2 acopios" ocultando al tercero, que paga cero.
   */
  descartados: number;
};

export const mediana = (xs: number[]) => {
  if (xs.length === 0) return null;
  const s = [...xs].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

/**
 * ¿El precio que paga este centro se sale de lo que la evidencia sostiene?
 * Devuelve null cuando no hay con qué comparar — que es la mitad de los casos,
 * y decirlo es parte del trabajo.
 */
export function revisarPrecio(code: string, precio: number | null) {
  const ref = REFERENCIAS[code];
  if (!ref) return null;

  // `=== null` no bastaba: un NaN colado desde la base caía hasta el return
  // final y la pantalla afirmaba "dentro de lo documentado" sobre un número
  // que no existe, además de pintar "NaN×" en el chip.
  if (precio === null || !Number.isFinite(precio) || precio < 0) return null;

  // Un divisor de 0 daba Infinity× para cualquier precio y NaN× para cero, y
  // una referencia sin calle NI techo (las pilas) terminaba en "ok" afirmando
  // evidencia que no existe — y sumando al KPI verde. Sin con qué comparar, la
  // respuesta honesta es no opinar.
  const calle = ref.calle && Number.isFinite(ref.calle.reciente) && ref.calle.reciente > 0
    && Number.isFinite(ref.calle.min) && Number.isFinite(ref.calle.max)
    ? ref.calle : null;
  const techo = Number.isFinite(ref.techo) && (ref.techo ?? 0) > 0 ? ref.techo! : null;
  if (!calle && !techo) return null;

  /** Veces el dato de calle más reciente. Es lo comparable entre materiales. */
  const x = calle ? precio / calle.reciente : null;
  const mult = x === null ? null : `${x.toFixed(1)}×`;
  /** La fecha del dato de CALLE, no la de la otra evidencia de la referencia. */
  const cuando = calle?.fecha ? ` de ${calle.fecha}` : "";

  if (techo && precio > techo) {
    return {
      nivel: "error" as const, mult,
      txt: `Pagas más que el tope de exportación (S/ ${techo.toFixed(2)}/kg). Por encima de ahí el kilo no se recupera al venderlo.`,
      accion: "Bajar el precio de compra",
    };
  }
  if (calle && precio >= calle.reciente * 2) {
    return {
      nivel: "error" as const, mult,
      txt: `Pagas ${x!.toFixed(1)} veces el dato de calle más reciente, que es S/ ${calle.reciente.toFixed(2)}/kg${cuando}. O el dato está viejo, o este precio regala margen.`,
      accion: "Revisar el precio de compra",
    };
  }
  if (calle && precio < calle.min) {
    return {
      nivel: "aviso" as const, mult,
      txt: `Pagas menos que el mínimo de calle documentado (S/ ${calle.min.toFixed(2)}/kg${cuando}). No es un riesgo contable, pero el vecino puede conseguir más en la esquina.`,
      accion: "Considerar subirlo",
    };
  }
  return { nivel: "ok" as const, mult, txt: "Dentro de lo que sostiene la evidencia.", accion: null };
}
