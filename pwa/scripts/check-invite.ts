// Run: node scripts/check-invite.ts
import assert from "node:assert/strict";
import { cleanCode, inviteDue, inviteLine, inviteLink, inviteMessage } from "../src/lib/referral.ts";

assert.equal(cleanCode(" r4n-qus "), "R4NQUS");
assert.equal(cleanCode("R4NQUS99"), "R4NQUS");
assert.equal(cleanCode(null), "");

const DAY = 86400000;
assert.equal(inviteDue(0, Date.now()), true, "never shown");
assert.equal(inviteDue(1000, 1000 + 6 * DAY), false, "six days later is too soon");
assert.equal(inviteDue(1000, 1000 + 7 * DAY), true);

assert.equal(inviteLink("R4NQUS", true), "https://ruma-staging.vercel.app/?ref=R4NQUS");
assert.match(inviteMessage("R4NQUS", 1000, true), /1,000 pts de bienvenida.*\?ref=R4NQUS$/);
// Points only: a referral never talks about money.
assert.doesNotMatch(inviteMessage("R4NQUS", 1000, true), /S\/|soles/i);

assert.deepEqual(inviteLine({ status: "pagado", points: 2000 }, 5), ["Pagado", "¡Te pagamos 2,000 pts!"]);
assert.equal(inviteLine({ status: "pendiente" }, 5)[0], "Pendiente");
assert.equal(inviteLine({ status: "pendiente", reason: "tope_mensual" }, 5)[0], "El próximo mes");
assert.equal(inviteLine({ status: "rechazado", reason: "mismo_telefono" }, 5)[1], "Usa el mismo teléfono que tú");
assert.equal(inviteLine({ status: "rechazado", reason: "otro" }, 5)[1], "No cumplió las reglas del programa");

console.log("invite ok");
