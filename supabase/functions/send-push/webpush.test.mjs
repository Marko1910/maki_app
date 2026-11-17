// node supabase/functions/send-push/webpush.test.mjs
// Replays the RFC 8291 section 5 example through encrypt() and checks the VAPID header with an independent verifier.
import assert from "node:assert/strict";
import { createPublicKey, verify, webcrypto } from "node:crypto";
import { b64url, encrypt, unb64url, vapidAuthorization } from "./webpush.ts";

const subtle = webcrypto.subtle;
const point = (pub) => { const p = unb64url(pub); return { x: b64url(p.slice(1, 33)), y: b64url(p.slice(33, 65)) }; };

// --- RFC 8291, section 5 and appendix A ---
const asPublic = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
const asPrivate = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
const sub = {
  endpoint: "https://push.example.net/push/JzLQ3raZJfFBR0aqvOMsLrt54w4rJUsV",
  p256dh: "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
  auth: "BTBZMqHH6r4Tts7J_aSIgg",
};
const expected = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";
const jwk = { kty: "EC", crv: "P-256", ...point(asPublic) };
const local = {
  publicKey: await subtle.importKey("jwk", jwk, { name: "ECDH", namedCurve: "P-256" }, true, []),
  privateKey: await subtle.importKey("jwk", { ...jwk, d: asPrivate }, { name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]),
};
const body = await encrypt(sub, "When I grow up, I want to be a watermelon", unb64url("DGv6ra1nlYgDCS1FRnbzlw"), local);
assert.equal(b64url(body), expected, "aes128gcm body must match the RFC 8291 example byte for byte");

// A real call draws its own salt and key: two messages never repeat.
assert.notEqual(b64url(await encrypt(sub, "hola")), b64url(await encrypt(sub, "hola")));

// --- VAPID (RFC 8292): the JWT must verify with the public key and name the push service as audience ---
const header = await vapidAuthorization(sub.endpoint, { publicKey: asPublic, privateKey: asPrivate, subject: "mailto:prueba@example.com" }, 1_700_000_000_000);
const [, token, k] = header.match(/^vapid t=([\w.-]+), k=([\w-]+)$/);
assert.equal(k, asPublic);
const [h, c, s] = token.split(".");
assert.deepEqual(JSON.parse(Buffer.from(h, "base64url")), { typ: "JWT", alg: "ES256" });
assert.deepEqual(JSON.parse(Buffer.from(c, "base64url")), { aud: "https://push.example.net", exp: 1_700_000_000 + 12 * 3600, sub: "mailto:prueba@example.com" });
assert.ok(verify("sha256", Buffer.from(`${h}.${c}`), { key: createPublicKey({ key: jwk, format: "jwk" }), dsaEncoding: "ieee-p1363" }, Buffer.from(s, "base64url")));

console.log("webpush: 3/3 pasan (vector RFC 8291, sal aleatoria, firma VAPID)");
