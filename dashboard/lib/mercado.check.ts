/**
 * Check de la lógica que decide si un precio está fuera de rango.
 *
 * Por qué existe: esta función es lo que le dice a un operador —y a un
 * inversor mirando por encima del hombro— si el centro paga bien. Una rama mal
 * puesta aquí no rompe nada visible: pinta un verde tranquilizador sobre un
 * precio malo. Todos los casos de abajo son fallos reales que tuvo el código,
 * encontrados en la revisión del 2026-10-17; están aquí para que no vuelvan.
 *
 * Cómo correrlo (Node 24 ejecuta TypeScript sin compilar):
 *     node --env-file=.env.local lib/mercado.check.ts
 */

import assert from "node:assert/strict";
import { revisarPrecio, REFERENCIAS } from "./mercado.ts";

let n = 0;
const caso = (nombre: string, fn: () => void) => {
  fn();
  n++;
  console.log(`  ok  ${nombre}`);
};

console.log("revisarPrecio\n");

// ── Lo que la pantalla enseña hoy ───────────────────────────────────────────
caso("cartón a S/0.60 es 4x el dato reciente de S/0.15 → error", () => {
  const r = revisarPrecio("CAR", 0.6);
  assert.equal(r?.nivel, "error");
  assert.equal(r?.mult, "4.0×");
  // La fecha citada tiene que ser la del dato de CALLE (jun 2026), no la de la
  // referencia, que en otros materiales describe la cota de exportación.
  assert.match(r!.txt, /jun 2026/);
});

caso("vidrio a S/0.20 es 2x el dato reciente de S/0.10 → error en el umbral", () => {
  assert.equal(revisarPrecio("VID", 0.2)?.nivel, "error");
  // Justo por debajo del doble NO salta: el umbral es >=, no >.
  assert.equal(revisarPrecio("VID", 0.199)?.nivel, "ok");
});

caso("aluminio a S/4.00 por debajo del mínimo de calle → aviso, no error", () => {
  assert.equal(revisarPrecio("ALU", 4)?.nivel, "aviso");
});

caso("PET a S/1.00 cae dentro de lo documentado", () => {
  assert.equal(revisarPrecio("PET", 1)?.nivel, "ok");
});

caso("por encima del tope de exportación manda, aunque la calle no se pase", () => {
  const r = revisarPrecio("ALU", 9); // techo 8.04
  assert.equal(r?.nivel, "error");
  assert.match(r!.txt, /tope de exportación/);
});

// ── Entradas que antes producían una afirmación falsa ───────────────────────
caso("NaN no se evalúa (antes decía «dentro de lo documentado» y pintaba NaN×)", () => {
  assert.equal(revisarPrecio("PET", NaN), null);
});

caso("Infinity y negativos no se evalúan", () => {
  assert.equal(revisarPrecio("PET", Infinity), null);
  assert.equal(revisarPrecio("PET", -1), null);
});

caso("sin evidencia que comparar no se opina (antes las pilas salían «ok»)", () => {
  // PIL no tiene calle ni techo: no hay con qué comparar.
  assert.equal(REFERENCIAS.PIL.calle, undefined);
  assert.equal(REFERENCIAS.PIL.techo, undefined);
  assert.equal(revisarPrecio("PIL", 1), null);
  assert.equal(revisarPrecio("PIL", 0), null);
});

caso("un material desconocido no se evalúa", () => {
  assert.equal(revisarPrecio("NO_EXISTE", 1), null);
});

caso("cero es un precio evaluable, no un hueco", () => {
  // Pagar cero por el cartón está por debajo del mínimo de calle: es un aviso
  // real, no un dato ausente.
  assert.equal(revisarPrecio("CAR", 0)?.nivel, "aviso");
});

// ── Las referencias mismas tienen que ser coherentes ────────────────────────
caso("cada referencia con calle tiene min <= reciente <= max y reciente > 0", () => {
  for (const ref of Object.values(REFERENCIAS)) {
    if (!ref.calle) continue;
    const { min, max, reciente } = ref.calle;
    assert.ok(min <= max, `${ref.code}: min ${min} > max ${max}`);
    assert.ok(reciente >= min && reciente <= max, `${ref.code}: reciente ${reciente} fuera de [${min}, ${max}]`);
    assert.ok(reciente > 0, `${ref.code}: reciente no puede ser 0 (divide)`);
  }
});

caso("ningún techo documentado queda por debajo del precio de calle", () => {
  for (const ref of Object.values(REFERENCIAS)) {
    if (ref.techo === undefined || !ref.calle) continue;
    assert.ok(ref.techo >= ref.calle.max,
      `${ref.code}: techo ${ref.techo} < calle.max ${ref.calle.max}, toda la calle sería «error»`);
  }
});

console.log(`\n${n} comprobaciones, todas pasan.`);
