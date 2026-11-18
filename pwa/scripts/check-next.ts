// Run: node scripts/check-next.ts — same cases as NextStepTest on Android.
import assert from "node:assert/strict";
import { nextStep } from "../src/lib/nextStep.ts";

const live = { title: "Carlos va en camino", sub: "Llega en ~5 min", to: "/f/seguimiento?id=1" };
assert.equal(nextStep({ live, unpickedScan: true, balance: 9000, cashMin: 1000, weekDays: 0 }).kind, "live");
assert.equal(nextStep({ unpickedScan: true, balance: 9000, cashMin: 1000 }).kind, "pickup");
assert.equal(nextStep({ balance: 1000, cashMin: 1000, weekDays: 0 }).kind, "cash");
assert.equal(nextStep({ balance: 0, cashMin: 1000, weekDays: 0, weekLeft: 3 }).kind, "start-week");
assert.equal(nextStep({ weekDays: 2, weekLeft: 1 }).kind, "near-goal");
const near = nextStep({ balance: 600, cashMin: 1000, weekDays: 1, weekLeft: 2 });
assert.equal(near.kind, "near-cash");
assert.equal(near.body, "Te faltan 400 pts para cobrar S/1.00.");
assert.equal(nextStep({ balance: 0, cashMin: null, weekDays: null }).kind, "register");
assert.equal(nextStep({ unpickedScan: true, saved: "7 botellas PET guardadas" }).title, "Tienes 7 botellas PET guardadas");
console.log("check-next: ok");
