// Run: node scripts/check-fold.ts
import assert from "node:assert/strict";
import { foldPhotos } from "../src/lib/fold.ts";

const pet = (quantity: number, brands: Record<string, number> = {}) => ({ quantity, brands });
assert.equal(foldPhotos([pet(3, { "Coca-Cola": 3 }), pet(4, { "Coca-Cola": 4 })]), 4, "same pile plus one is 4, not 7");
assert.equal(foldPhotos([pet(3, { "Coca-Cola": 3 }), pet(3, { "Coca-Cola": 3 })]), 3, "same pile framed again adds nothing");
assert.equal(foldPhotos([pet(2, { "Coca-Cola": 2 }), pet(3, { "Inca Kola": 3 })]), 5, "a new brand is new waste");
assert.equal(foldPhotos([pet(2), pet(2)]), 4, "no brands seen: each photo adds up");
console.log("fold ok");
