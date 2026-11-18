// Run: node scripts/check-pile.ts — the saved pile adds scans up and keeps the last scan's corrections.
import assert from "node:assert/strict";
import { pileCounts, pileSummary } from "../src/lib/pile.ts";

const pile = [{ material_id: "p", code: "PET", name: "PET", quantity: 7 }, { material_id: "a", code: "ALU", name: "Aluminio", quantity: 1 }];
assert.deepEqual(pileCounts(pile), { p: 7, a: 1 });
// The AI saw 4 on the last scan (already inside the 7); the family corrected it to 5.
const scan = { items: [{ material_id: "p", quantity: 5 }], ai: { p: 4 } };
assert.deepEqual(pileCounts(pile, scan), { p: 8, a: 1 });
assert.equal(pileSummary(pileCounts(pile, scan), pile), "8 botellas PET y 1 lata de aluminio guardadas");
assert.equal(pileSummary({ p: 1 }, pile), "1 botella PET guardada");
assert.equal(pileSummary({}, pile), null);
console.log("check-pile: ok");
