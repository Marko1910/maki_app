// Web Push with nothing but WebCrypto: message encryption (RFC 8291, aes128gcm) and VAPID (RFC 8292).
// No library on purpose: the Deno web-push packages wrap these same ~60 lines, and this module is checked
// against the RFC 8291 test vector in webpush.test.mjs (node supabase/functions/send-push/webpush.test.mjs).

const enc = new TextEncoder();

export const b64url = (data: ArrayBuffer | Uint8Array | string): string => {
  const bytes = typeof data === "string" ? enc.encode(data) : new Uint8Array(data);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

export const unb64url = (s: string): Uint8Array =>
  Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));

const concat = (...parts: Uint8Array[]): Uint8Array => {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let at = 0;
  for (const p of parts) { out.set(p, at); at += p.length; }
  return out;
};

async function hkdf(salt: Uint8Array, ikm: Uint8Array, info: Uint8Array, length: number): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey("raw", ikm, "HKDF", false, ["deriveBits"]);
  return new Uint8Array(await crypto.subtle.deriveBits({ name: "HKDF", hash: "SHA-256", salt, info }, key, length * 8));
}

/** What the browser hands over in PushSubscription.toJSON(): the endpoint and its two keys (base64url). */
export type WebSubscription = { endpoint: string; p256dh: string; auth: string };
/** publicKey: the 65-byte uncompressed P-256 point; privateKey: its 32-byte scalar; both base64url. */
export type Vapid = { publicKey: string; privateKey: string; subject: string };

const P256 = { name: "ECDH", namedCurve: "P-256" };

/**
 * Encrypts [payload] for one subscription. [salt] and [local] exist only so the RFC vector can be replayed;
 * a real call leaves them out and gets a fresh salt and key pair per message, as the RFC requires.
 */
export async function encrypt(sub: WebSubscription, payload: string, salt?: Uint8Array, local?: CryptoKeyPair): Promise<Uint8Array> {
  salt ??= crypto.getRandomValues(new Uint8Array(16));
  local ??= await crypto.subtle.generateKey(P256, true, ["deriveBits"]) as CryptoKeyPair;
  const uaPublic = unb64url(sub.p256dh);
  const asPublic = new Uint8Array(await crypto.subtle.exportKey("raw", local.publicKey));
  const uaKey = await crypto.subtle.importKey("raw", uaPublic, P256, false, []);
  const shared = new Uint8Array(await crypto.subtle.deriveBits({ name: "ECDH", public: uaKey }, local.privateKey, 256));

  const ikm = await hkdf(unb64url(sub.auth), shared, concat(enc.encode("WebPush: info\0"), uaPublic, asPublic), 32);
  const cek = await hkdf(salt, ikm, enc.encode("Content-Encoding: aes128gcm\0"), 16);
  const nonce = await hkdf(salt, ikm, enc.encode("Content-Encoding: nonce\0"), 12);

  const key = await crypto.subtle.importKey("raw", cek, "AES-GCM", false, ["encrypt"]);
  // One record: the 0x02 delimiter marks it as the last.
  const sealed = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv: nonce }, key, concat(enc.encode(payload), Uint8Array.of(2))));
  // Header: salt(16) | record size(4) | key id length(1) | key id = our public key(65).
  return concat(salt, Uint8Array.of(0, 0, 16, 0), Uint8Array.of(asPublic.length), asPublic, sealed);
}

/** `Authorization` value proving to the push service that this server owns the key the browser subscribed with. */
export async function vapidAuthorization(endpoint: string, v: Vapid, now = Date.now()): Promise<string> {
  const pub = unb64url(v.publicKey);
  const key = await crypto.subtle.importKey(
    "jwk",
    { kty: "EC", crv: "P-256", d: v.privateKey, x: b64url(pub.slice(1, 33)), y: b64url(pub.slice(33, 65)) },
    { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"],
  );
  const head = b64url(JSON.stringify({ typ: "JWT", alg: "ES256" }));
  const claims = b64url(JSON.stringify({ aud: new URL(endpoint).origin, exp: Math.floor(now / 1000) + 12 * 3600, sub: v.subject }));
  const sig = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, enc.encode(`${head}.${claims}`));
  return `vapid t=${head}.${claims}.${b64url(sig)}, k=${v.publicKey}`;
}

/** One push. `topic` (at most 32 URL-safe characters) makes a newer message replace an undelivered older one. */
export async function sendWebPush(
  sub: WebSubscription, payload: string, v: Vapid,
  opts: { ttl: number; urgency: "high" | "normal"; topic?: string },
): Promise<Response> {
  return fetch(sub.endpoint, {
    method: "POST",
    headers: {
      Authorization: await vapidAuthorization(sub.endpoint, v),
      "Content-Encoding": "aes128gcm",
      "Content-Type": "application/octet-stream",
      TTL: String(Math.max(0, Math.round(opts.ttl))),
      Urgency: opts.urgency,
      ...(opts.topic ? { Topic: opts.topic } : {}),
    },
    body: await encrypt(sub, payload),
  });
}
