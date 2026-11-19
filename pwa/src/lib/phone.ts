/**
 * A Peruvian mobile number as the rider dials it: "+51" and 9 digits starting with 9.
 * Accepts what people type ("987 654 321", "+51 987654321"). Twin of `PeruPhone.kt`; `node scripts/check-phone.ts`.
 */
export function normalizePhone(raw?: string | null): string | null {
  let d = (raw ?? "").replace(/\D/g, "");
  if (d.startsWith("00")) d = d.slice(2);
  if (d.length === 11 && d.startsWith("51")) d = d.slice(2);
  return d.length === 9 && d.startsWith("9") ? `+51${d}` : null;
}
export const PHONE_HINT = "Escribe tu celular: 9 dígitos y empieza con 9.";
