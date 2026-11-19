// Run: node scripts/check-phone.ts — same cases as PeruPhoneTest on Android.
import assert from "node:assert/strict";
import { normalizePhone } from "../src/lib/phone.ts";

assert.equal(normalizePhone("987 654 321"), "+51987654321");
assert.equal(normalizePhone("+51 987-654-321"), "+51987654321");
assert.equal(normalizePhone("0051987654321"), "+51987654321");
assert.equal(normalizePhone("044 123456"), null);      // landline
assert.equal(normalizePhone("98765432"), null);        // 8 digits
assert.equal(normalizePhone(""), null);
console.log("check-phone: ok");
