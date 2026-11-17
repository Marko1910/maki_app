"use client";

import { createClient } from "@supabase/supabase-js";

/**
 * Cliente del navegador. La anon key es pública a propósito: lo que protege los
 * datos es RLS — un operador solo ve su propio centro porque las políticas
 * `deliveries_operator`, `center_payments_operator` y compañía filtran por
 * `current_operator_center()`, no porque esta app pida lo correcto.
 */
export const supabase = createClient(
  process.env.NEXT_PUBLIC_SUPABASE_URL!,
  process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
  { auth: { persistSession: true, autoRefreshToken: true } },
);

/** Minutos transcurridos desde una marca de tiempo ISO; null si no hay. */
export function minutesSince(iso: string | null | undefined): number | null {
  if (!iso) return null;
  return Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
}

/**
 * "hace 8 min" / "hace 2 h" / "ayer". Nunca afirma más de lo que sabe: si no
 * hay marca de tiempo devuelve "sin dato", no un cero que parezca reciente.
 */
export function hace(iso: string | null | undefined): string {
  const m = minutesSince(iso);
  if (m === null) return "sin dato";
  if (m < 1) return "hace segundos";
  if (m < 60) return `hace ${m} min`;
  const h = Math.floor(m / 60);
  if (h < 24) return `hace ${h} h`;
  const d = Math.floor(h / 24);
  if (d === 1) return "ayer";
  if (d < 60) return `hace ${d} días`;
  // Pasados dos meses "hace 6219 días" no lo lee nadie: la fecha dice más.
  return new Date(iso!).toLocaleDateString("es-PE", { month: "short", year: "numeric" });
}

export const soles = (n: number | null | undefined) =>
  `S/ ${(n ?? 0).toLocaleString("es-PE", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

export const kilos = (n: number | null | undefined) =>
  `${(n ?? 0).toLocaleString("es-PE", { maximumFractionDigits: 1 })} kg`;

/** Hora local de la consulta, que NO es la antigüedad del dato. */
export const horaConsulta = () =>
  new Date().toLocaleTimeString("es-PE", { hour12: false });

/**
 * Número escrito por una persona en Perú: "1,50" y "1.50" valen lo mismo.
 * Devuelve null si está vacío o no es un número — nunca 0, que se guardaría
 * como un precio de verdad.
 */
export function num(v: string): number | null {
  const t = v.trim().replace(",", ".");
  if (t === "") return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}

/**
 * Escapa texto que viene de la base antes de meterlo en HTML. Los nombres de
 * riders y familias los escribe un usuario, así que un `<img onerror=...>`
 * guardado como nombre se ejecutaría en el panel del operador.
 */
export function esc(s: unknown): string {
  return String(s ?? "")
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}
