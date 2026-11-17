// send-push: delivers one public.notifications row to the user's devices.
//   Android  -> Firebase Cloud Messaging (HTTP v1), tokens with platform = 'android'.
//   Web/iOS  -> standard Web Push (VAPID + aes128gcm, ./webpush.ts), subscriptions with platform = 'web'.
//
// The DATABASE decides what is sent and when (supabase/sql/2026-11-12_push_reglas.sql): only notifications whose
// `push` column is not 'none' get a push_queue row, quiet hours are applied there, and pg_cron calls this
// function again (4 attempts, growing wait) until it stamps push_queue.sent_at. This function only delivers.
//
// Called by public.push_dispatch() through pg_net, signed with PUSH_HOOK_SECRET in the x-push-secret header;
// deploy with --no-verify-jwt because pg_net carries no user JWT.
// Secrets (scripts/setup-push-secrets.sh): PUSH_HOOK_SECRET, FCM_SERVICE_ACCOUNT (the Firebase service-account
// JSON, raw or base64), VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT. A channel without its secret is
// reported, not sent.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { sendWebPush, type Vapid } from "./webpush.ts";

type ServiceAccount = { project_id: string; client_email: string; private_key: string; token_uri: string };
/** ok: delivered. gone: the device no longer exists, forget it. retry: worth another attempt. failed: retrying cannot help. */
type Outcome = "ok" | "gone" | "retry" | "failed";

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const hookSecret = Deno.env.get("PUSH_HOOK_SECRET") ?? "";
const account: ServiceAccount | null = (() => {
  try {
    const raw = (Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "").trim();
    if (!raw) return null;
    const sa = JSON.parse(raw.startsWith("{") ? raw : atob(raw));
    return sa.client_email && sa.private_key && sa.project_id ? sa : null;
  } catch { return null; }
})();
const vapid: Vapid | null = Deno.env.get("VAPID_PUBLIC_KEY") && Deno.env.get("VAPID_PRIVATE_KEY")
  ? {
    publicKey: Deno.env.get("VAPID_PUBLIC_KEY")!, privateKey: Deno.env.get("VAPID_PRIVATE_KEY")!,
    // Apple rejects a subject that is not a real https or mailto address.
    subject: Deno.env.get("VAPID_SUBJECT") || "https://ruma-recicla.vercel.app",
  }
  : null;

/** The one Android channel for pickup news, created by the app (NotificationHelper). */
const CHANNEL = "ruma_recojos";

const b64url = (data: ArrayBuffer | string) => {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data);
  let s = ""; for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

let cached: { token: string; until: number } | null = null;

/** OAuth access token for FCM from the service account (JWT bearer grant), cached until a minute before expiry. */
async function accessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.until > now + 60) return cached.token;
  const uri = sa.token_uri || "https://oauth2.googleapis.com/token";
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: uri, iat: now, exp: now + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  const res = await fetch(uri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${header}.${claims}.${b64url(sig)}` }),
  });
  if (!res.ok) throw new Error(`oauth ${res.status}`);
  const j = await res.json();
  cached = { token: j.access_token, until: now + (j.expires_in ?? 3600) };
  return cached.token;
}

type Notice = { id: number; user_id: string; kind: string | null; title: string; body: string | null; reference_type: string | null; reference_id: string | null };
type Delivery = { urgent: boolean; ttl: number };

async function sendAndroid(token: string, n: Notice, d: Delivery, bearer: string): Promise<[Outcome, string]> {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${account!.project_id}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      message: {
        token,
        notification: { title: n.title, body: n.body ?? "" },
        android: {
          priority: d.urgent ? "HIGH" : "NORMAL",
          ttl: `${d.ttl}s`,
          // One live notification per pickup: the same tag replaces the previous step instead of stacking.
          notification: { channel_id: CHANNEL, tag: n.reference_id ?? String(n.id) },
        },
        // The app reads these to open the pickup on tap (MainActivity) and to draw it when in front.
        data: { notification_id: String(n.id), kind: n.kind ?? "", reference_type: n.reference_type ?? "", reference_id: n.reference_id ?? "" },
      },
    }),
  });
  if (res.ok) return ["ok", ""];
  const err = await res.text();
  // A token the phone no longer owns (app removed, data cleared) or that belongs to another Firebase project.
  if (res.status === 404 || /UNREGISTERED|SENDER_ID_MISMATCH|not a valid FCM registration token/.test(err)) return ["gone", ""];
  if (res.status === 429 || res.status >= 500) return ["retry", `fcm ${res.status}`];
  return ["failed", `fcm ${res.status}`];
}

async function sendWeb(sub: { token: string; p256dh: string; auth: string }, n: Notice, d: Delivery, url: string): Promise<[Outcome, string]> {
  const payload = JSON.stringify({ title: n.title, body: (n.body ?? "").slice(0, 600), tag: n.reference_id ?? String(n.id), url });
  const res = await sendWebPush({ endpoint: sub.token, p256dh: sub.p256dh, auth: sub.auth }, payload, vapid!, {
    ttl: d.ttl, urgency: d.urgent ? "high" : "normal",
    topic: n.reference_id?.replace(/-/g, ""),
  });
  await res.body?.cancel();
  if (res.ok) return ["ok", ""];
  // 404/410: the subscription ended. 403: it was made with another VAPID key and can never be used by this one.
  if ([403, 404, 410].includes(res.status)) return ["gone", ""];
  if (res.status === 429 || res.status >= 500) return ["retry", `webpush ${res.status}`];
  return ["failed", `webpush ${res.status}`];
}

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const sameSecret = (a: string, b: string) => {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
};

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);
  if (!hookSecret || !sameSecret(req.headers.get("x-push-secret") ?? "", hookSecret)) return json({ error: "forbidden" }, 403);

  const input = await req.json().catch(() => ({}));
  // Which channels have their secrets, without revealing any (docs/push-setup.md, "Comprobar").
  if (input.health) return json({ fcm: !!account, webpush: !!vapid });
  const id = input.id;
  if (id == null) return json({ error: "id" }, 400);

  const { data: queued } = await db.from("push_queue").select("level,expires_at,sent_at").eq("notification_id", id).maybeSingle();
  if (!queued) return json({ skipped: "not queued" });
  if (queued.sent_at) return json({ skipped: "already sent" });
  const ttl = Math.floor((new Date(queued.expires_at).getTime() - Date.now()) / 1000);
  if (ttl <= 0) return json({ skipped: "expired" });
  const done = (patch: Record<string, unknown>) => db.from("push_queue").update(patch).eq("notification_id", id);

  const { data: n } = await db.from("notifications").select("id,user_id,kind,title,body,reference_type,reference_id").eq("id", id).maybeSingle();
  if (!n) return json({ skipped: "not found" });
  const { data: tokens } = await db.from("push_tokens").select("token,platform,p256dh,auth").eq("user_id", n.user_id);
  if (!tokens?.length) {
    await done({ sent_at: new Date().toISOString(), last_error: "sin dispositivos" });
    return json({ skipped: "no tokens" });
  }

  const delivery: Delivery = { urgent: queued.level === "urgent", ttl };
  const web = tokens.filter((t) => t.platform === "web");
  const android = tokens.filter((t) => t.platform !== "web");
  const results: [Outcome, string][] = [];

  if (android.length) {
    if (!account) results.push(["retry", "FCM_SERVICE_ACCOUNT no configurado"]);
    else {
      try {
        const bearer = await accessToken(account);
        results.push(...await Promise.all(android.map(async (t): Promise<[Outcome, string]> => {
          const r = await sendAndroid(t.token, n, delivery, bearer).catch((e): [Outcome, string] => ["retry", String(e?.message ?? e)]);
          if (r[0] === "gone") await db.from("push_tokens").delete().eq("token", t.token);
          return r;
        })));
      } catch (e) { results.push(["retry", String((e as Error)?.message ?? e)]); }
    }
  }
  if (web.length) {
    if (!vapid) results.push(["retry", "VAPID no configurado"]);
    else {
      // Where a tap lands: the family's live map for that pickup, the rider's route.
      const { data: profile } = await db.from("profiles").select("role").eq("id", n.user_id).maybeSingle();
      const url = n.reference_type !== "pickup" ? "/"
        : profile?.role === "eco_rider" ? "/r/ruta" : `/f/seguimiento?id=${n.reference_id}`;
      results.push(...await Promise.all(web.map(async (t): Promise<[Outcome, string]> => {
        const r = await sendWeb(t, n, delivery, url).catch((e): [Outcome, string] => ["retry", String(e?.message ?? e)]);
        if (r[0] === "gone") await db.from("push_tokens").delete().eq("token", t.token);
        return r;
      })));
    }
  }

  const count = (o: Outcome) => results.filter((r) => r[0] === o).length;
  const errors = [...new Set(results.map((r) => r[1]).filter(Boolean))].join("; ").slice(0, 300) || null;
  // Anything worth retrying leaves the row open for pg_cron. A device that already got it may get it twice;
  // the tag makes the second replace the first instead of stacking.
  await done(count("retry") ? { last_error: errors } : { sent_at: new Date().toISOString(), last_error: errors });
  return json({ sent: count("ok"), gone: count("gone"), retry: count("retry"), failed: count("failed"), devices: tokens.length });
});
