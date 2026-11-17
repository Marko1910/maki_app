/**
 * Business findings need fixed numerical expectations so a changed assumption
 * forces a deliberate review. Node 24 can run this without a framework or env file:
 *     node lib/economia.check.ts
 */
import assert from "node:assert/strict";
import {
  CATALOG_PAYMENTS_PER_KG, CATALOG_POINTS_PER_KG, CATALOG_PRICES_PER_KG,
  CENTER_INTERVIEW, DEFAULT_ASSUMPTIONS, HOUSEHOLD_SHARE, POINTS_PER_SOL,
  applySeasonality, calculateArbitriosDiscount, calculateHouseholdBreakEven,
  calculateMaterialValue, calculatePickupEconomics, calculateRiderDay, estimateCenterResale,
} from "./economia.ts";

let checks = 0;
function check(name: string, run: () => string) {
  try {
    const figures = run();
    checks++;
    console.log(`  ok  ${name}\n      ${figures}`);
  } catch (error) {
    console.error(`  FALLÓ  ${name}`);
    throw error;
  }
}

function close(actual: number, expected: number) {
  assert.ok(Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)),
    `Esperado ${expected}; obtenido ${actual}`);
}

const money = (value: number) => `S/${value.toFixed(2)}`;
const catalog = { householdPaymentsPerKg: CATALOG_PAYMENTS_PER_KG };

console.log("Ruma · unidad económica · entrevista de octubre de 2026\n");
console.log("Escenario supuesto: 5 kg/recojo, 50 % PET + 50 % cartón por peso; hogar 40 %; rider 80 % del remanente.");
console.log("Meta S/60/día; 26 días/mes; 2 recojos/mes/hogar; moto hipotética: 30 kg/carga y 4 cargas/día.");
console.log("Los ingresos son antes de combustible, tiempo, clasificación, rechazos y otros costos no medidos.\n");

check("La entrevista no ofrece prima por volumen: 1 tonelada vale exactamente 1.000 kilos", () => {
  assert.deepEqual(CENTER_INTERVIEW.pricesPerKg, { FER: 0.60, CAR: 0.30, PET: 1.00 });
  for (const material of ["FER", "CAR", "PET"] as const) {
    close(calculateMaterialValue({ [material]: 1000 }).grossSoles, CENTER_INTERVIEW.pricesPerTonne[material]);
  }
  assert.equal(CENTER_INTERVIEW.volumePremium, false);
  return "Chatarra: S/0,60 → S/600/t; cartón: S/0,30 → S/300/t; PET: S/1,00 → S/1.000/t; prima = 0 %";
});

check("La chatarra mueve el centro, pero el catálogo carece de una categoría ferrosa", () => {
  assert.equal(Object.hasOwn(CATALOG_PRICES_PER_KG, "FER"), false);
  assert.throws(() => calculateMaterialValue({ FER: 1 }, catalog), /Falta un precio/);
  const kgPerDay = CENTER_INTERVIEW.ferrousTonnesPerShipment * 1000 / CENTER_INTERVIEW.daysPerShipment;
  close(kgPerDay, 2000);
  close(calculateMaterialValue({ FER: 1000 }).grossSoles, 600);
  return `${CENTER_INTERVIEW.ferrousTonnesPerShipment} t / ${CENTER_INTERVIEW.daysPerShipment} días = ${kgPerDay} kg/día de flujo medio; comprador: ${CENTER_INTERVIEW.buyer}; 0 categorías ferrosas en el catálogo`;
});

check("El catálogo paga 1.000 puntos por sol; las pilas son un incentivo, no una venta", () => {
  assert.equal(POINTS_PER_SOL, 1000);
  close(HOUSEHOLD_SHARE, 0.40);
  assert.deepEqual(CATALOG_POINTS_PER_KG, { PET: 400, CAR: 240, ALU: 1600, VID: 80, PIL: 250 });
  for (const material of ["PET", "CAR", "ALU", "VID"] as const) {
    close(CATALOG_PAYMENTS_PER_KG[material]!, CATALOG_PRICES_PER_KG[material] * HOUSEHOLD_SHARE);
  }
  return "Pago/kg: PET S/0,40; cartón S/0,24; aluminio S/1,60; vidrio S/0,08; pilas S/0,25 de incentivo";
});

check("CARTÓN: el pago actual consume el 80 %, no el 40 %; rider y plataforma reciben menos que el hogar", () => {
  const value = calculateMaterialValue({ CAR: 1 }, catalog);
  close(value.grossSoles, 0.30);
  close(value.householdSoles, 0.24);
  close(value.householdSoles / value.grossSoles, 0.80);
  close(value.remainderSoles, 0.06);
  assert.ok(value.remainderSoles < value.householdSoles);
  return `Por kg: valor ${money(value.grossSoles)}, hogar ${money(value.householdSoles)} (80 %), remanente ${money(value.remainderSoles)} (20 % < 80 %)`;
});

check("PET: el pago actual sí deja el 60 % para rider y plataforma", () => {
  const value = calculateMaterialValue({ PET: 1 }, catalog);
  close(value.householdSoles, 0.40);
  close(value.remainderSoles, 0.60);
  close(value.remainderSoles / value.grossSoles, 0.60);
  return `Por kg: valor ${money(value.grossSoles)}, hogar ${money(value.householdSoles)}, remanente ${money(value.remainderSoles)} = 60 %`;
});

check("Corregir cartón al 40 % real TRIPLICA el remanente por kilo", () => {
  const current = calculateMaterialValue({ CAR: 1 }, catalog);
  const corrected = calculateMaterialValue({ CAR: 1 });
  close(corrected.householdSoles, 0.12);
  close(corrected.remainderSoles, 0.18);
  const multiple = corrected.remainderSoles / current.remainderSoles;
  close(multiple, 3);
  return `Hogar ${money(current.householdSoles)} → ${money(corrected.householdSoles)}; remanente ${money(current.remainderSoles)} → ${money(corrected.remainderSoles)} = ${multiple.toFixed(2)}×`;
});

check("Un recojo de la mezcla supuesta deja S/1,95; el rider obtiene S/1,56 antes de costos", () => {
  const value = calculatePickupEconomics();
  close(value.kg, 5);
  close(value.grossSoles, 3.25);
  close(value.householdSoles, 1.30);
  close(value.remainderSoles, 1.95);
  close(value.remainderSoles * DEFAULT_ASSUMPTIONS.riderShare, 1.56);
  return `5 kg: bruto ${money(value.grossSoles)}, hogar ${money(value.householdSoles)}, remanente ${money(value.remainderSoles)}, rider S/1,56 y plataforma S/0,39`;
});

check("507 hogares por rider: 195 kg/día hacen inviable la moto SUPUESTA de 4 cargas de 30 kg", () => {
  const result = calculateHouseholdBreakEven();
  const day = result.day.requirements;
  assert.ok(day);
  assert.equal(day.pickupsPerDay, 39);
  assert.equal(result.activeHouseholds, 507);
  close(day.kgPerDay, 195);
  close(day.riderDailySoles, 60.84);
  assert.equal(day.minimumLoadsPerDay, 7);
  assert.equal(day.exceedsLoadPlan, true);
  assert.ok((day.pickupsPerDay - 1) * result.day.riderSolesPerPickup < result.day.targetDailySoles);
  assert.ok(result.activeHouseholds * result.pickupsPerHouseholdPerMonth >= day.pickupsPerDay * result.workdaysPerMonth);
  assert.ok((result.activeHouseholds - 1) * result.pickupsPerHouseholdPerMonth < day.pickupsPerDay * result.workdaysPerMonth);
  return `${result.activeHouseholds} hogares × 2/mes = 39 paradas/día × 26 días; ${money(day.riderDailySoles)}/día; ${day.kgPerDay} kg/día > 120 kg/día; ≥${day.minimumLoadsPerDay} cargas, no 195 kg simultáneos`;
});

check("Mantener el pago del catálogo exige todavía más: 598 hogares y 230 kg/día", () => {
  const result = calculateHouseholdBreakEven({ valuation: catalog });
  const day = result.day.requirements;
  assert.ok(day);
  assert.equal(result.activeHouseholds, 598);
  assert.equal(day.pickupsPerDay, 46);
  close(day.kgPerDay, 230);
  close(result.day.riderSolesPerPickup, 1.32);
  return `${result.activeHouseholds} hogares; ${day.pickupsPerDay} paradas y ${day.kgPerDay} kg/día; ${money(result.day.riderSolesPerPickup)} por parada para el rider`;
});

check("Una bodega con 30 kg de cartón deja más remanente que 2 hogares de la mezcla supuesta", () => {
  // The task supplies the 30 kg scenario, not a measured store frequency or a special purchase price.
  const store = calculateMaterialValue({ CAR: 30 });
  const household = calculatePickupEconomics();
  const equivalentStops = store.remainderSoles / household.remainderSoles;
  const fewerStops = Math.ceil(equivalentStops) - 1;
  close(store.householdSoles, 3.60);
  close(store.remainderSoles, 5.40);
  close(equivalentStops, 36 / 13);
  assert.equal(fewerStops, 2);
  assert.ok(store.remainderSoles > fewerStops * household.remainderSoles);
  assert.ok(store.remainderSoles <= (fewerStops + 1) * household.remainderSoles);
  const currentStore = calculateMaterialValue({ CAR: 30 }, catalog);
  const currentHousehold = calculatePickupEconomics(undefined, undefined, catalog);
  close(currentStore.remainderSoles / currentHousehold.remainderSoles, 12 / 11);
  return `Pago anticipable a bodega ${money(store.householdSoles)}; remanente ${money(store.remainderSoles)} / ${money(household.remainderSoles)} = ${equivalentStops.toFixed(3)} hogares: N=${fewerStops}. Con pagos actuales: S/1,80 / S/1,65 = 1,091 hogares`;
});

check("Arbitrios del escenario: S/60 al año frente a S/18.982,08 teóricos del rider = 316,37×", () => {
  const discount = calculateArbitriosDiscount();
  const day = calculateRiderDay().requirements;
  assert.ok(day);
  // Twelve months is the calendar conversion; constant prices and workdays are a scenario, not a forecast.
  const annualMaterialSoles = day.riderDailySoles * DEFAULT_ASSUMPTIONS.workdaysPerMonth * 12;
  const ratio = annualMaterialSoles / discount.riderAnnualDiscountSoles;
  close(discount.householdAnnualDiscountSoles, 60);
  close(discount.riderAnnualDiscountSoles, 60);
  close(annualMaterialSoles, 18982.08);
  close(ratio, 316.368);
  assert.ok(ratio > 100);
  assert.equal(discount.riderPointsRequired, 60000);
  return `Arbitrios ficticios S/600/año × 10 % = ${money(discount.riderAnnualDiscountSoles)} para rider y hogar; material ${money(annualMaterialSoles)}/año = ${ratio.toFixed(2)}× (${Math.log10(ratio).toFixed(2)} órdenes); exige 60.000 puntos si se canjea según SQL. Ingreso teórico: la ruta supera la carga supuesta`;
});

check("«Con cualquier parámetro razonable» no demuestra órdenes de magnitud: el cociente depende del escenario", () => {
  // These sensitivity inputs are hypothetical bills and discounts, not district prices or entitlements.
  const sensitivity = { riderAnnualSoles: 2400, riderDiscount: 0.50 };
  const discount = calculateArbitriosDiscount(sensitivity);
  const day = calculateRiderDay().requirements;
  assert.ok(day);
  const ratio = day.riderDailySoles * DEFAULT_ASSUMPTIONS.workdaysPerMonth * 12 / discount.riderAnnualDiscountSoles;
  close(discount.riderAnnualDiscountSoles, 1200);
  close(ratio, 15.8184);
  assert.ok(ratio < 100);
  return `Sensibilidad supuesta: S/2.400/año × 50 % = S/1.200; material/descuento = ${ratio.toFixed(2)}× (${Math.log10(ratio).toFixed(2)} órdenes), no ≥100×; el SQL canjea puntos ya ganados, no añade este ingreso`;
});

check("Diciembre es UN mes de caída; enero y febrero recuperan el precio base en el escenario", () => {
  const prices = Array.from({ length: 12 }, (_, month) => applySeasonality(1, month + 1));
  assert.deepEqual(prices, [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0.80]);
  close(applySeasonality(1, 12, { decemberDrop: 0.35 }), 0.65);
  close(applySeasonality(1, 1, { januaryPriceFactor: 1.05 }), 1.05);
  close(applySeasonality(1, 2, { februaryPriceFactor: 1.10 }), 1.10);
  const seasonalCardboard = calculateMaterialValue({ CAR: 1 }, {
    ...catalog, pricesPerKg: { CAR: applySeasonality(CENTER_INTERVIEW.pricesPerKg.CAR, 12) },
  });
  close(seasonalCardboard.remainderSoles, 0);
  return "PET: diciembre S/0,80, enero S/1,00, febrero S/1,00; caída supuesta 20 %, recuperación completa supuesta; cartón diciembre S/0,24 − pago fijo S/0,24 = S/0,00 de remanente";
});

check("La reventa supuesta de 1,8× pertenece al centro y no infla el ingreso del recojo", () => {
  const pickup = calculateMaterialValue({ PET: 1 });
  close(estimateCenterResale(pickup.grossSoles), 1.80);
  close(estimateCenterResale(pickup.grossSoles, 1.25), 1.25);
  close(pickup.grossSoles, 1);
  close(pickup.remainderSoles, 0.60);
  return "PET bruto S/1,00; remanente S/0,60; reventa posterior supuesta S/1,80 (o S/1,25 cambiando el parámetro), sin sumarla al rider";
});

check("Sin remanente positivo no existe un número finito de hogares que sostenga al rider", () => {
  const batteries = calculateMaterialValue({ PIL: 1 }, catalog);
  close(batteries.grossSoles, 0);
  close(batteries.householdSoles, 0.25);
  close(batteries.remainderSoles, -0.25);
  const impossible = calculateHouseholdBreakEven({ mix: { PIL: 1 }, valuation: catalog });
  assert.equal(impossible.day.requirements, null);
  assert.equal(impossible.activeHouseholds, null);
  assert.equal(calculateRiderDay({ riderShare: 0 }).requirements, null);
  assert.equal(calculateRiderDay({ kgPerPickup: 0 }).requirements, null);
  assert.equal(calculateRiderDay({ valuation: { householdShare: 1 } }).requirements, null);
  const zeroTarget = calculateHouseholdBreakEven({ targetDailySoles: 0, riderShare: 0 });
  assert.equal(zeroTarget.activeHouseholds, 0);
  assert.equal(zeroTarget.day.requirements?.kgPerDay, 0);
  return "Pilas: S/0 de venta − S/0,25 de incentivo/kg = −S/0,25; objetivo S/60 sin financiación: imposible (null). Objetivo S/0: 0 hogares y 0 kg";
});

check("La capacidad puede cambiar: 7 cargas permiten la masa diaria; una parada de 35 kg supera una carga de 30 kg", () => {
  const moreLoads = calculateRiderDay({ maxLoadsPerDay: 7 }).requirements;
  assert.ok(moreLoads);
  assert.equal(moreLoads.exceedsLoadPlan, false);
  close(moreLoads.kgPerDay, 195);
  const oversized = calculateRiderDay({ kgPerPickup: 35, maxLoadsPerDay: 100 }).requirements;
  assert.ok(oversized);
  assert.equal(oversized.exceedsLoadPlan, true);
  return "195 kg/día caben por masa en 7 cargas de 30 kg; una parada de 35 kg > 30 kg exige dividir la entrega. Tiempo, volumen y rutas siguen sin validar";
});

check("Los datos inválidos fallan explícitamente; cero kilos o cero descuento sí tienen valor cero", () => {
  assert.throws(() => calculateMaterialValue({ CAR: -1 }), RangeError);
  assert.throws(() => calculateMaterialValue({ PET: NaN }), RangeError);
  assert.throws(() => calculateMaterialValue({ PET: 1 }, { pricesPerKg: { PET: Infinity } }), RangeError);
  assert.throws(() => calculateMaterialValue({ ALU: 1 }, { pricesPerKg: { PET: 1 } }), /Falta un precio/);
  assert.throws(() => calculateMaterialValue({ PET: 1 }, { householdShare: 1.10 }), RangeError);
  assert.throws(() => calculatePickupEconomics({ PET: 50, CAR: 50 }), /sumar 1/);
  assert.throws(() => calculateHouseholdBreakEven({ pickupsPerHouseholdPerMonth: 0 }), RangeError);
  assert.throws(() => calculateHouseholdBreakEven({ workdaysPerMonth: 32 }), RangeError);
  assert.throws(() => calculateRiderDay({ payloadKg: 0 }), RangeError);
  assert.throws(() => calculateRiderDay({ riderShare: -0.1 }), RangeError);
  assert.throws(() => applySeasonality(1, 13), RangeError);
  assert.throws(() => applySeasonality(1, 12, { decemberDrop: 1.01 }), RangeError);
  assert.throws(() => calculateArbitriosDiscount({ householdDiscount: -0.1 }), RangeError);
  close(calculateMaterialValue({}).remainderSoles, 0);
  close(calculatePickupEconomics({ PET: 1 }, 0).grossSoles, 0);
  close(calculateArbitriosDiscount({ riderDiscount: 0 }).riderAnnualDiscountSoles, 0);
  return "Rechazados: −1 kg, NaN, ∞, precio ausente, 110 %, mezcla 50+50, frecuencia 0, 32 días, carga 0, mes 13; aceptados: 0 kg → S/0 y descuento 0 % → S/0";
});

console.log(`\n${checks} comprobaciones, todas pasan.`);
