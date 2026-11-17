"use client";

import { supabase } from "./supabase";
import { mediana, type PrecioRed } from "./mercado";

/**
 * Todas las lecturas van filtradas por el centro del operador. El filtro del
 * cliente es por claridad: quien de verdad impide ver otro centro es RLS
 * (`deliveries_operator`, `center_payments_operator`, `stock_movements_operator`,
 * todas contra `current_operator_center()`).
 */

/**
 * Desenvuelve una respuesta de PostgREST y LANZA si vino error.
 *
 * Antes se hacía `const { data } = await …` descartando `error`: un fallo de red
 * devolvía `[]` y el panel decía "todo recibido, pesado y liquidado" o "S/ 0 de
 * deuda". Un error de lectura tiene que verse como error, nunca como una
 * operación vacía.
 */
async function q<T>(p: PromiseLike<{ data: T; error: { message?: string } | null }>): Promise<T> {
  const { data, error } = await p;
  if (error) throw new Error(error.message ?? "No pudimos leer los datos del centro.");
  return data;
}

export type Centro = {
  id: string;
  name: string;
  address_line: string | null;
  latitude: number | null;
  longitude: number | null;
  capacity_kg: number | null;
  current_load_kg: number;
};

export async function miCentro(): Promise<Centro | null> {
  const op = await q(supabase.from("operator_profiles").select("center_id").maybeSingle());
  if (!op?.center_id) return null;
  return (await q(
    supabase
      .from("collection_centers")
      .select("id,name,address_line,latitude,longitude,capacity_kg,current_load_kg")
      .eq("id", op.center_id)
      .maybeSingle(),
  )) as Centro | null;
}

export type Entrega = {
  id: string;
  code: string | null;
  status: string;
  rider_id: string | null;
  total_kg: number;
  gross_amount: number;
  platform_fee: number;
  rider_amount: number;
  departed_at: string | null;
  received_at: string | null;
  weighed_at: string | null;
  settled_at: string | null;
  rider: { display_name: string | null } | null;
};

const ENTREGA_SELECT =
  "id,code,status,rider_id,total_kg,gross_amount,platform_fee,rider_amount," +
  "departed_at,received_at,weighed_at,settled_at," +
  "rider:profiles!deliveries_rider_id_fkey(display_name)";

/** La cola de trabajo: lo que viene, lo que llegó y lo pesado sin liquidar. */
export async function colaPendiente(centerId: string): Promise<Entrega[]> {
  return ((await q(
    supabase
      .from("deliveries")
      .select(ENTREGA_SELECT)
      .eq("center_id", centerId)
      .in("status", ["en_camino", "recibida", "pesada"])
      .order("departed_at", { ascending: true }),
  )) ?? []) as unknown as Entrega[];
}

/** Liquidadas hoy, para el margen. Día local. */
export async function liquidadasHoy(centerId: string): Promise<Entrega[]> {
  const inicio = new Date();
  inicio.setHours(0, 0, 0, 0);
  return ((await q(
    supabase
      .from("deliveries")
      .select(ENTREGA_SELECT)
      .eq("center_id", centerId)
      .eq("status", "liquidada")
      .gte("settled_at", inicio.toISOString()),
  )) ?? []) as unknown as Entrega[];
}

/**
 * Margen NETO de Ruma: platform_fee − rider_amount, lote a lote.
 * Nunca reconstruido con platform_fee_pct × gross_amount: se usan los importes
 * que quedaron registrados al liquidar. Los negativos se devuelven aparte para
 * no taparlos con los positivos.
 */
export function margen(entregas: Entrega[]) {
  const porLote = entregas.map((e) => ({
    e,
    neto: Number(e.platform_fee ?? 0) - Number(e.rider_amount ?? 0),
  }));
  const negativos = porLote.filter((x) => x.neto < 0);
  return {
    total: porLote.reduce((s, x) => s + x.neto, 0),
    negativos: negativos.length,
    perdida: negativos.reduce((s, x) => s + x.neto, 0),
    lotes: porLote,
  };
}

export type RiderVivo = {
  profile_id: string;
  rider_code: string | null;
  vehicle_type: string;
  is_available: boolean;
  current_latitude: number | null;
  current_longitude: number | null;
  last_location_at: string | null;
  profiles: { display_name: string | null } | null;
};

/**
 * Riders con su última posición REPORTADA. No es seguimiento continuo: la app
 * del rider manda un heartbeat cada ~15 s mientras está abierta, así que el
 * panel muestra la antigüedad del dato y nunca afirma "app cerrada" — también
 * puede ser falta de cobertura.
 */
export async function ridersEnVivo(): Promise<RiderVivo[]> {
  return ((await q(
    supabase
      .from("rider_profiles")
      .select(
        "profile_id,rider_code,vehicle_type,is_available,current_latitude,current_longitude,last_location_at," +
          "profiles!rider_profiles_profile_id_fkey(display_name)",
      )
      .eq("is_available", true),
  )) ?? []) as unknown as RiderVivo[];
}

export type RecojoActivo = {
  id: string;
  code: string | null;
  status: string;
  latitude: number | null;
  longitude: number | null;
  rider_id: string | null;
  generator: { display_name: string | null } | null;
};

/**
 * Domicilios de recojo. Un pin es una dirección, no una persona.
 *
 * No se filtra por `center_id` a propósito: aunque la columna existe, el cliente
 * NO puede escribirla, así que los recojos nacen SIN centro y se vinculan recién
 * al entrar en una entrega. Quien decide qué ve el operador es la política
 * `pickups_select`: los de su centro y los que aún no tienen ninguno.
 */
export async function recojosActivos(_centerId: string): Promise<RecojoActivo[]> {
  return ((await q(
    supabase
      .from("pickups")
      .select(
        "id,code,status,latitude,longitude,rider_id," +
          "generator:profiles!pickups_generator_id_fkey(display_name)",
      )
      .in("status", ["requested", "assigned", "en_route", "arriving", "collecting"])
      .order("requested_at", { ascending: false })
      .limit(40),
  )) ?? []) as unknown as RecojoActivo[];
}

export async function balanceCentro(centerId: string) {
  return await q(
    supabase
      .from("v_center_balance")
      .select("center_id,name,total_liquidado,total_pagado,saldo_pendiente")
      .eq("center_id", centerId)
      .maybeSingle(),
  );
}

export async function pagosDeclarados(centerId: string) {
  return (
    (await q(
      supabase
        .from("center_payments")
        .select("id,amount,method,reference,voucher_url,status,declared_at,confirmed_at,notes")
        .eq("center_id", centerId)
        .order("declared_at", { ascending: false }),
    )) ?? []
  );
}

/** Saldo por rider. earnings_balance lo mantiene el trigger apply_rider_earning. */
export async function saldosRiders() {
  return (
    (await q(
      supabase
        .from("rider_profiles")
        .select(
          "profile_id,rider_code,total_collections,earnings_balance,earnings_lifetime," +
            "profiles!rider_profiles_profile_id_fkey(display_name)",
        )
        .order("earnings_balance", { ascending: false }),
    )) ?? []
  );
}

export async function retirosSolicitados() {
  return (
    (await q(
      supabase
        .from("payout_withdrawals")
        .select("id,rider_id,amount,method,status,requested_at")
        .in("status", ["requested", "processing"])
        .order("requested_at", { ascending: true }),
    )) ?? []
  );
}

/** Kilos por material del periodo, desde el pesaje real. */
export async function kilosPorMaterial(centerId: string, desde: Date) {
  return (
    (await q(
      supabase
        .from("delivery_items")
        .select(
          "weight_kg,quality_grade,subtotal,materials(code,name)," +
            "deliveries!inner(center_id,status,settled_at)",
        )
        .eq("deliveries.center_id", centerId)
        .eq("deliveries.status", "liquidada")
        .gte("deliveries.settled_at", desde.toISOString()),
    )) ?? []
  );
}

// ---------------------------------------------------------------------------
// Acciones. El cliente pide; la base decide.
// ---------------------------------------------------------------------------

/** El lote llegó al mostrador. */
export async function recibirEntrega(id: string, operadorId: string) {
  return supabase
    .from("deliveries")
    .update({ status: "recibida", received_at: new Date().toISOString(), received_by: operadorId })
    .eq("id", id)
    .select("id")
    .maybeSingle();
}

export type PesoItem = { material_id: string; weight_kg: number; quality_grade: "A" | "B" | "C"; price_per_kg: number };

/** Factores de grado de platform_settings. El grado escala el precio pagado. */
export const FACTOR_GRADO = { A: 1.0, B: 0.85, C: 0.6 } as const;

/**
 * Cierra el pesaje: escribe los `delivery_items` con los kilos y la calidad
 * REALES y recién entonces marca el lote como pesado. Antes esta acción solo
 * cambiaba el estado, y el lote llegaba a la liquidación sin un solo kilo
 * registrado: settle_delivery no tenía con qué repartir puntos.
 */
export async function registrarPesaje(id: string, items: PesoItem[]) {
  if (items.length === 0) throw new Error("Registra al menos un material pesado.");
  const filas = items.map((it) => ({
    delivery_id: id,
    material_id: it.material_id,
    weight_kg: it.weight_kg,
    quality_grade: it.quality_grade,
    price_per_kg: it.price_per_kg,
    grade_factor: FACTOR_GRADO[it.quality_grade],
  }));
  const ins = await supabase.from("delivery_items").insert(filas).select("id");
  if (ins.error) return ins;
  return supabase
    .from("deliveries")
    .update({ status: "pesada", weighed_at: new Date().toISOString() })
    .eq("id", id)
    .select("id")
    .maybeSingle();
}

/** Lo ya pesado de un lote, para no duplicar filas al reabrir el pesaje. */
export async function pesajeDe(id: string) {
  return (
    (await q(
      supabase
        .from("delivery_items")
        .select("id,material_id,weight_kg,quality_grade,price_per_kg,subtotal,materials(code,name)")
        .eq("delivery_id", id),
    )) ?? []
  );
}

/**
 * Liquidar: reparte puntos a las familias, fija el margen y mueve el stock.
 * Es una RPC porque todo eso tiene que pasar en una sola transacción, y porque
 * el cliente no puede escribir ni un céntimo de esos importes.
 */
export async function liquidarEntrega(id: string) {
  return supabase.rpc("settle_delivery", { p_delivery_id: id });
}

// ---------------------------------------------------------------------------
// Materiales y precios del centro
// ---------------------------------------------------------------------------

export async function materialesDelCentro(centerId: string) {
  return (
    (await q(
      supabase
        .from("center_materials")
        .select(
          "id,material_id,buy_price_per_kg,sell_price_per_kg,current_stock_kg,is_accepted,is_free," +
            "materials(code,name,icon,color,points_per_kg,avg_weight_kg,unit)",
        )
        .eq("center_id", centerId),
    )) ?? []
  );
}

export async function preciosMercado() {
  return (
    (await q(supabase.from("v_material_prices").select("code,name,price_per_kg,pct_change"))) ?? []
  );
}

/**
 * Cambiar un precio. El trigger `trg_center_materials_price_log` deja solo en
 * material_price_history: cada cambio queda fechado, no se pisa el anterior.
 */
export async function cambiarPrecio(id: string, buy: number, sell: number) {
  return supabase
    .from("center_materials")
    .update({ buy_price_per_kg: buy, sell_price_per_kg: sell })
    .eq("id", id)
    .select("id")
    .maybeSingle();
}

export async function aceptarMaterial(id: string, acepta: boolean) {
  return supabase.from("center_materials").update({ is_accepted: acepta }).eq("id", id).select("id").maybeSingle();
}

/** Últimos movimientos del libro de stock (solo-añadir: nunca se edita). */
export async function movimientosStock(centerId: string) {
  return (
    (await q(
      supabase
        .from("center_stock_movements")
        .select("id,kind,weight_kg,unit_price,note,created_at,materials(code,name)")
        .eq("center_id", centerId)
        .order("created_at", { ascending: false })
        .limit(12),
    )) ?? []
  );
}

/** Salida de material (venta a la industria) o merma. Entra por el libro. */
export async function registrarMovimiento(
  centerId: string, materialId: string, kind: "salida" | "merma" | "ajuste",
  kg: number, precio: number | null, nota: string, operadorId: string,
) {
  return supabase
    .from("center_stock_movements")
    .insert({ center_id: centerId, material_id: materialId, kind, weight_kg: kg,
              unit_price: precio, note: nota, created_by: operadorId })
    .select("id")
    .maybeSingle();
}

// ---------------------------------------------------------------------------
// Call center
// ---------------------------------------------------------------------------

export const TIPOS_LLAMADA = [
  { v: "register_pickup", t: "Registrar recojo" },
  { v: "check_balance", t: "Consultar saldo" },
  { v: "resolve_doubt", t: "Resolver duda" },
  { v: "contact_rider", t: "Contactar rider" },
  { v: "complaint", t: "Reclamo" },
  { v: "other", t: "Otro" },
] as const;

/** Las últimas, para la lista. NO sirve para contar: está recortada. */
export async function llamadas(centerId: string) {
  return (
    (await q(
      supabase
        .from("call_center_interactions")
        .select("id,kind,caller_phone,notes,duration_seconds,created_at,generator_id,pickup_id")
        .eq("center_id", centerId)
        .order("created_at", { ascending: false })
        .limit(25),
    )) ?? []
  );
}

/**
 * TODAS las del día para los indicadores. Antes los KPIs se calculaban sobre
 * las últimas 25, así que a partir de la llamada 26 el contador se congelaba y
 * un reclamo viejo desaparecía de la cuenta.
 */
export async function llamadasDelDia(centerId: string) {
  const inicio = new Date();
  inicio.setHours(0, 0, 0, 0);
  return (
    (await q(
      supabase
        .from("call_center_interactions")
        .select("id,kind,duration_seconds,created_at")
        .eq("center_id", centerId)
        .gte("created_at", inicio.toISOString()),
    )) ?? []
  );
}

export async function registrarLlamada(
  centerId: string, operadorId: string, kind: string,
  telefono: string, notas: string, segundos: number | null,
) {
  return supabase
    .from("call_center_interactions")
    .insert({ center_id: centerId, operator_id: operadorId, kind,
              caller_phone: telefono || null, notes: notas || null, duration_seconds: segundos })
    .select("id,created_at")
    .maybeSingle();
}

/**
 * OJO: confirmar un pago NO es posible desde este panel y es deliberado.
 * La migración 26 revoca `update (status, confirmed_by, confirmed_at)` sobre
 * center_payments a `authenticated`: si el propio acopio pudiera confirmarlos,
 * declararía sus deudas como pagadas. Lo confirma Ruma con service_role.
 */
export const CONFIRMAR_PAGO_ES_DE_RUMA = true;

/**
 * Precio de la red: lo que de verdad declaran los acopios, material por
 * material. Esto es el producto: nadie más tiene cobertura de precios de
 * reciclaje en Perú, porque no existe la fuente.
 */
export async function preciosDeLaRed(centerId?: string | null): Promise<PrecioRed[]> {
  const { data, error } = await supabase
    .from("center_materials")
    .select("center_id,buy_price_per_kg,is_accepted,updated_at,materials(code,name,is_active)");
  if (error) throw new Error(error.message);

  type Acum = {
    name: string; precios: number[]; fechas: string[]; descartados: number;
    miPrecio: number | null; loAcepto: boolean;
  };
  const porCodigo = new Map<string, Acum>();
  for (const row of (data ?? []) as any[]) {
    // El join de PostgREST puede venir como objeto o como array de uno.
    const m = Array.isArray(row.materials) ? row.materials[0] : row.materials;
    if (!m?.code || !m.is_active || !row.is_accepted) continue;

    const e: Acum = porCodigo.get(m.code)
      ?? { name: m.name ?? m.code, precios: [], fechas: [], descartados: 0, miPrecio: null, loAcepto: false };

    const p = Number(row.buy_price_per_kg);
    // Cero es legítimo (las pilas no se compran); negativo o no numérico no.
    const usable = Number.isFinite(p) && p > 0;
    if (usable) e.precios.push(p);
    else if (!Number.isFinite(p) || p < 0) e.descartados++;

    if (centerId && row.center_id === centerId) {
      e.loAcepto = true;
      e.miPrecio = Number.isFinite(p) && p >= 0 ? p : null;
    }
    if (row.updated_at) e.fechas.push(row.updated_at);
    porCodigo.set(m.code, e);
  }

  return [...porCodigo.entries()].map(([code, e]) => {
    const min = e.precios.length ? Math.min(...e.precios) : null;
    const max = e.precios.length ? Math.max(...e.precios) : null;
    return {
      code,
      name: e.name,
      miPrecio: e.miPrecio,
      loAcepto: e.loAcepto,
      mediana: mediana(e.precios),
      min, max,
      n: e.precios.length,
      actualizado: e.fechas.sort().at(-1) ?? null,
      uniforme: e.precios.length > 1 && min === max,
      descartados: e.descartados,
    };
  });
}

/** Noticias de precio/demanda que alimentan la pestaña. */
export async function noticiasMercado() {
  const { data, error } = await supabase
    .from("market_news")
    .select("id,kind,title,summary,material_code,source,url,published_at")
    .eq("is_active", true)
    .order("published_at", { ascending: false })
    .limit(8);
  if (error) throw new Error(error.message);
  return data ?? [];
}

// ---------------------------------------------------------------------------
// Planes: pagos por Yape que el operador cierra a mano
// ---------------------------------------------------------------------------

/** Solicitudes `pending_payment` (lectura permitida al operador por RLS; no hay RPC de listado). */
export async function pagosPlanPendientes() {
  return (
    (await q(
      supabase.from("plan_subscriptions")
        .select("id, plan_code, period, amount, reference, created_at, profiles(display_name)")
        .eq("status", "pending_payment")
        .order("created_at", { ascending: true }),
    )) ?? []
  );
}

/** Activar tras ver llegar el Yape; la base valida operador, estado y referencia. */
export async function activarPlan(id: string, operacion: string) {
  const { error } = await supabase.rpc("activate_plan_subscription", { p_subscription_id: id, p_payment_reference: operacion });
  if (error) throw new Error(error.message);
}

export async function rechazarPlan(id: string, motivo: string) {
  const { error } = await supabase.rpc("reject_plan_payment", { p_subscription_id: id, p_reason: motivo });
  if (error) throw new Error(error.message);
}
