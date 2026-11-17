/**
 * These are scenario calculations, not settlement rules: unmeasured costs must
 * not turn the remaining material revenue into a claim of net profit.
 */

export type Material = "FER" | "CAR" | "PET" | "ALU" | "VID" | "PIL";
export type MaterialAmounts = Readonly<Partial<Record<Material, number>>>;

export const MATERIAL_LABELS: Readonly<Record<Material, string>> = Object.freeze({
  FER: "Chatarra ferrosa", CAR: "Cartón", PET: "Botellas PET",
  ALU: "Aluminio", VID: "Vidrio", PIL: "Pilas",
});

// The October 2026 collection-center interview is local evidence, not a national index.
export const CENTER_INTERVIEW = Object.freeze({
  source: "Entrevista a un centro de acopio, octubre de 2026 (encargo-economia.md)",
  pricesPerKg: Object.freeze({ FER: 0.60, CAR: 0.30, PET: 1.00 }),
  pricesPerTonne: Object.freeze({ FER: 600, CAR: 300, PET: 1000 }),
  mainMaterial: "FER" as const,
  ferrousTonnesPerShipment: 30,
  daysPerShipment: 15,
  buyer: "Aceros Arequipa",
  smallCollectorKgPerDay: Object.freeze([20, 100] as const),
  largestCollectorKgPerDay: 2000,
  priceFallsInMonth: 12,
  priceRisesInMonths: Object.freeze([1, 2] as const),
  volumePremium: false,
});

export const STORE_INTERVIEW = Object.freeze({
  source: "Entrevista a una bodega (encargo-economia.md)",
  currentlyGivesAway: "Cartón, botellas y jabas de huevo",
  adoptionCondition: "Saber de antemano cuánto va a ganar",
});

// The SQL is a catalog snapshot, not confirmation of a center's buying prices.
export const CATALOG_SOURCE = "supabase/sql/2026-10-09_precios_reales_y_arbitrios.sql";
export const POINTS_PER_SOL = 1000;
export const HOUSEHOLD_SHARE = 0.40;
export const CATALOG_PRICES_PER_KG = Object.freeze({ PET: 1, CAR: 0.60, ALU: 4, VID: 0.20, PIL: 0 });
export const CATALOG_POINTS_PER_KG = Object.freeze({ PET: 400, CAR: 240, ALU: 1600, VID: 80, PIL: 250 });
export const CATALOG_PAYMENTS_PER_KG: MaterialAmounts = Object.freeze(
  Object.fromEntries(Object.entries(CATALOG_POINTS_PER_KG).map(([material, points]) =>
    [material, points / POINTS_PER_SOL])),
);

// ALU and VID retain explicit, unverified catalog defaults because neither was quoted
// in the interview. PIL's zero resale value does not finance its SQL civic incentive.
export const DEFAULT_PRICES_PER_KG: MaterialAmounts = Object.freeze({
  ...CATALOG_PRICES_PER_KG, ...CENTER_INTERVIEW.pricesPerKg,
});
export const PRICE_SOURCES: Readonly<Record<Material, string>> = Object.freeze({
  FER: CENTER_INTERVIEW.source, CAR: CENTER_INTERVIEW.source, PET: CENTER_INTERVIEW.source,
  ALU: "Supuesto del catálogo SQL: S/4,00/kg; sin verificar en la entrevista",
  VID: "Supuesto del catálogo SQL: S/0,20/kg; sin verificar en la entrevista",
  PIL: "SQL y encargo: sin reventa; los puntos son un incentivo cívico",
});

export const DEFAULT_ASSUMPTIONS = Object.freeze({
  // No household weights or composition were measured; this is an illustrative mix by mass.
  mix: Object.freeze({ PET: 0.50, CAR: 0.50 }),
  kgPerPickup: 5,
  // Neither a rider income target nor a negotiated split was supplied.
  targetDailySoles: 60,
  riderShare: 0.80,
  // These are scheduling hypotheses, not measured household production or attendance.
  workdaysPerMonth: 26,
  pickupsPerHouseholdPerMonth: 2,
  // These are planning limits, not a motorcycle specification. Daily mass can span loads.
  payloadKg: 30,
  maxLoadsPerDay: 4,
  // Only the direction and months were observed; the loss and recovery size were not.
  decemberDrop: 0.20,
  januaryPriceFactor: 1,
  februaryPriceFactor: 1,
  // The SQL's 1.8 multiplier is unverified and belongs to the center's next sale.
  centerResaleMultiplier: 1.8,
  // Synthetic examples only: no district's bill or municipal entitlement was provided.
  householdAnnualArbitriosSoles: 600,
  riderAnnualArbitriosSoles: 600,
  householdArbitriosDiscount: 0.10,
  riderArbitriosDiscount: 0.10,
});

export type ValuationOptions = {
  pricesPerKg?: MaterialAmounts;
  householdShare?: number;
  // Fixed catalog payments expose overpayment; recomputing 40% would hide the cardboard gap.
  householdPaymentsPerKg?: MaterialAmounts;
};

function requireNonnegative(value: number, label: string) {
  if (!Number.isFinite(value) || value < 0) throw new RangeError(`${label}: debe ser finito y no negativo`);
}

function requirePositive(value: number, label: string) {
  requireNonnegative(value, label);
  if (value === 0) throw new RangeError(`${label}: debe ser mayor que cero`);
}

function requireFraction(value: number, label: string) {
  requireNonnegative(value, label);
  if (value > 1) throw new RangeError(`${label}: debe estar entre 0 y 1`);
}

function materialEntries(amounts: MaterialAmounts): [Material, number][] {
  return Object.entries(amounts).map(([material, amount]) => {
    if (!Object.hasOwn(MATERIAL_LABELS, material)) throw new RangeError(`Material sin modelo: ${material}`);
    requireNonnegative(amount, `Cantidad de ${MATERIAL_LABELS[material as Material]}`);
    return [material as Material, amount];
  });
}

function requirePrice(prices: MaterialAmounts, material: Material) {
  const price = prices[material];
  if (price === undefined) throw new RangeError(`Falta un precio o pago explícito para ${MATERIAL_LABELS[material]}`);
  requireNonnegative(price, `Precio o pago de ${MATERIAL_LABELS[material]}`);
  return price;
}

export function calculateMaterialValue(amountsKg: MaterialAmounts, {
  pricesPerKg = DEFAULT_PRICES_PER_KG,
  householdShare = HOUSEHOLD_SHARE,
  householdPaymentsPerKg,
}: ValuationOptions = {}) {
  requireFraction(householdShare, "Participación del hogar");
  let kg = 0;
  let grossSoles = 0;
  let householdSoles = 0;
  for (const [material, amount] of materialEntries(amountsKg)) {
    if (amount === 0) continue;
    const price = requirePrice(pricesPerKg, material);
    const payment = householdPaymentsPerKg === undefined
      ? price * householdShare : requirePrice(householdPaymentsPerKg, material);
    kg += amount;
    grossSoles += amount * price;
    householdSoles += amount * payment;
  }
  // Clamping a deficit to zero would conceal subsidies, especially the battery incentive.
  return { kg, grossSoles, householdSoles, remainderSoles: grossSoles - householdSoles };
}

export function calculatePickupEconomics(
  mix: MaterialAmounts = DEFAULT_ASSUMPTIONS.mix,
  kg: number = DEFAULT_ASSUMPTIONS.kgPerPickup,
  valuation: ValuationOptions = {},
) {
  requireNonnegative(kg, "Kilos por recojo");
  const entries = materialEntries(mix);
  const total = entries.reduce((sum, [, fraction]) => sum + fraction, 0);
  // A floating-point tolerance permits decimal fractions without silently normalizing bad inputs.
  if (Math.abs(total - 1) > 1e-9) throw new RangeError("Las proporciones por peso deben sumar 1");
  return calculateMaterialValue(Object.fromEntries(entries.map(([material, fraction]) =>
    [material, kg * fraction])), valuation);
}

export type RiderDayOptions = {
  mix?: MaterialAmounts;
  kgPerPickup?: number;
  targetDailySoles?: number;
  riderShare?: number;
  payloadKg?: number;
  maxLoadsPerDay?: number;
  valuation?: ValuationOptions;
};

export function calculateRiderDay({
  mix = DEFAULT_ASSUMPTIONS.mix,
  kgPerPickup = DEFAULT_ASSUMPTIONS.kgPerPickup,
  targetDailySoles = DEFAULT_ASSUMPTIONS.targetDailySoles,
  riderShare = DEFAULT_ASSUMPTIONS.riderShare,
  payloadKg = DEFAULT_ASSUMPTIONS.payloadKg,
  maxLoadsPerDay = DEFAULT_ASSUMPTIONS.maxLoadsPerDay,
  valuation = {},
}: RiderDayOptions = {}) {
  requireNonnegative(targetDailySoles, "Ingreso objetivo diario");
  requireFraction(riderShare, "Participación del rider en el remanente");
  requirePositive(payloadKg, "Carga máxima por viaje");
  requirePositive(maxLoadsPerDay, "Máximo de cargas diarias");
  if (!Number.isInteger(maxLoadsPerDay)) throw new RangeError("El máximo de cargas debe ser entero");
  const pickup = calculatePickupEconomics(mix, kgPerPickup, valuation);
  const riderSolesPerPickup = pickup.remainderSoles * riderShare;
  // No finite number of stops can meet a positive target without positive rider revenue.
  const requirements = targetDailySoles > 0 && riderSolesPerPickup <= 0 ? null : (() => {
    const pickupsPerDay = targetDailySoles === 0 ? 0 : Math.ceil(targetDailySoles / riderSolesPerPickup);
    const kgPerDay = pickupsPerDay * pickup.kg;
    const minimumLoadsPerDay = Math.ceil(kgPerDay / payloadKg);
    return {
      pickupsPerDay, kgPerDay, minimumLoadsPerDay,
      riderDailySoles: pickupsPerDay * riderSolesPerPickup,
      platformDailySoles: pickupsPerDay * pickup.remainderSoles * (1 - riderShare),
      // This is only a mass lower bound: travel, volume and uneven loads may require more trips.
      exceedsLoadPlan: minimumLoadsPerDay > maxLoadsPerDay || (pickupsPerDay > 0 && pickup.kg > payloadKg),
    };
  })();
  return { pickup, riderSolesPerPickup, targetDailySoles, payloadKg, maxLoadsPerDay, requirements };
}

export function calculateHouseholdBreakEven({
  workdaysPerMonth = DEFAULT_ASSUMPTIONS.workdaysPerMonth,
  pickupsPerHouseholdPerMonth = DEFAULT_ASSUMPTIONS.pickupsPerHouseholdPerMonth,
  ...riderOptions
}: RiderDayOptions & { workdaysPerMonth?: number; pickupsPerHouseholdPerMonth?: number } = {}) {
  requirePositive(workdaysPerMonth, "Días de trabajo por mes");
  if (!Number.isInteger(workdaysPerMonth) || workdaysPerMonth > 31) {
    throw new RangeError("Los días de trabajo por mes deben ser enteros entre 1 y 31");
  }
  requirePositive(pickupsPerHouseholdPerMonth, "Recojos mensuales por hogar");
  const day = calculateRiderDay(riderOptions);
  // Whole daily stops must be rounded before sizing the household pool; this assumes balanced routes.
  const activeHouseholds = day.requirements === null ? null
    : Math.ceil(day.requirements.pickupsPerDay * workdaysPerMonth / pickupsPerHouseholdPerMonth);
  return { day, activeHouseholds, workdaysPerMonth, pickupsPerHouseholdPerMonth };
}

export function applySeasonality(price: number, month: number, {
  decemberDrop = DEFAULT_ASSUMPTIONS.decemberDrop,
  januaryPriceFactor = DEFAULT_ASSUMPTIONS.januaryPriceFactor,
  februaryPriceFactor = DEFAULT_ASSUMPTIONS.februaryPriceFactor,
}: { decemberDrop?: number; januaryPriceFactor?: number; februaryPriceFactor?: number } = {}) {
  requireNonnegative(price, "Precio base");
  if (!Number.isInteger(month) || month < 1 || month > 12) throw new RangeError("El mes debe estar entre 1 y 12");
  requireFraction(decemberDrop, "Caída de diciembre");
  requireNonnegative(januaryPriceFactor, "Factor de enero");
  requireNonnegative(februaryPriceFactor, "Factor de febrero");
  // December alone receives the recurring drop; January/February recovery amounts remain assumptions.
  const factor = month === CENTER_INTERVIEW.priceFallsInMonth ? 1 - decemberDrop
    : month === 1 ? januaryPriceFactor : month === 2 ? februaryPriceFactor : 1;
  return price * factor;
}

export function estimateCenterResale(price: number, multiplier: number = DEFAULT_ASSUMPTIONS.centerResaleMultiplier) {
  requireNonnegative(price, "Precio de compra del centro");
  requireNonnegative(multiplier, "Multiplicador de reventa no verificado");
  return price * multiplier;
}

export function calculateArbitriosDiscount({
  householdAnnualSoles = DEFAULT_ASSUMPTIONS.householdAnnualArbitriosSoles,
  riderAnnualSoles = DEFAULT_ASSUMPTIONS.riderAnnualArbitriosSoles,
  householdDiscount = DEFAULT_ASSUMPTIONS.householdArbitriosDiscount,
  riderDiscount = DEFAULT_ASSUMPTIONS.riderArbitriosDiscount,
}: { householdAnnualSoles?: number; riderAnnualSoles?: number; householdDiscount?: number; riderDiscount?: number } = {}) {
  requireNonnegative(householdAnnualSoles, "Arbitrios anuales del hogar (escenario)");
  requireNonnegative(riderAnnualSoles, "Arbitrios anuales del rider (escenario)");
  requireFraction(householdDiscount, "Descuento del hogar");
  requireFraction(riderDiscount, "Descuento del rider");
  const householdAnnualDiscountSoles = householdAnnualSoles * householdDiscount;
  const riderAnnualDiscountSoles = riderAnnualSoles * riderDiscount;
  // The SQL redemption spends existing points; these are not extra earnings or promised benefits.
  return {
    householdAnnualDiscountSoles, riderAnnualDiscountSoles,
    householdPointsRequired: householdAnnualDiscountSoles * POINTS_PER_SOL,
    riderPointsRequired: riderAnnualDiscountSoles * POINTS_PER_SOL,
  };
}
