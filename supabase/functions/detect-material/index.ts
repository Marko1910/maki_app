import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

// MAKI server-authoritative recyclable verifier — continuous scan.
// The camera stays open while the user walks around their waste. Each frame is a
// `preview`: the server scores it, returns the items it saw and SIGNS that count.
// `commit` replays those signed observations and writes the one detection — so the
// client can never inflate a count it did not earn, without any per-frame DB write.
// - Hides the vision model key (never ships in the APK).
// - Anti-spoof: liveness motion gate per frame + VLM screen/photo detection.
// - Sole writer of `detections` + `detection_items`; points computed here.
//   Points still SETTLE on rider pickup (settle_pickup) — a scan alone never pays.
//
// Deploy: supabase functions deploy detect-material --project-ref <ref>
// Secret: GEMINI_API_KEY (Dashboard → Edge Functions → Secrets).

// Gemini via its OpenAI-compatible endpoint (drop-in swap from Groq, 2026-07-05).
const AI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
// Primary, then the lighter sibling: 2.5-flash returns 503 "high demand" often enough
// on the free tier to break a scan, and flash-lite has its own capacity pool.
const MODELS = ["gemini-2.5-flash", "gemini-2.5-flash-lite"];
const MIN_GYRO_DPS = 8; // require real device motion during the capture window
const MIN_SAMPLES = 5;
const MAX_OBSERVATIONS = 60; // one scan session's worth of frames

const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });
}

function clamp01(n: number): number {
  return Number.isNaN(n) ? 0 : Math.max(0, Math.min(1, n));
}

/** [x0,y0,x1,y1] in 0..1 image fractions, or null when the model omitted/garbled it. */
function normBox(raw: unknown): number[] | null {
  if (!Array.isArray(raw) || raw.length < 4) return null;
  let [x0, y0, x1, y1] = raw.slice(0, 4).map((n) => clamp01(Number(n)));
  if (x1 < x0) [x0, x1] = [x1, x0];
  if (y1 < y0) [y0, y1] = [y1, y0];
  // A degenerate box would draw as a line; drop it rather than show a glitch.
  if (x1 - x0 < 0.02 || y1 - y0 < 0.02) return null;
  return [x0, y0, x1, y1].map((n) => Math.round(n * 1000) / 1000);
}

// ---- Signed observations -------------------------------------------------
// A preview result is only worth what the server itself saw, so it leaves with an
// HMAC over (session, code, quantity, quality). The client stores the tokens and
// hands them back on commit; a tampered quantity fails the check.
// ponytail: HMAC over the service key beats a `scan_sessions` table — no schema
// change, no per-frame write, no abandoned-session cleanup.
const encoder = new TextEncoder();

type Counted = { code: string; quantity: number; quality: number };

function canonical(sessionId: string, items: Counted[]): string {
  const parts = items
    .map((i) => `${i.code}:${Math.round(i.quantity)}:${Number(i.quality).toFixed(3)}`)
    .sort();
  return `${sessionId}|${parts.join(",")}`;
}

async function sign(secret: string, sessionId: string, items: Counted[]): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const mac = await crypto.subtle.sign("HMAC", key, encoder.encode(canonical(sessionId, items)));
  return btoa(String.fromCharCode(...new Uint8Array(mac)));
}

/** Calls the vision model, retrying the transient capacity errors that break a scan. */
async function callVision(aiKey: string, content: unknown[]): Promise<any> {
  let lastStatus = 0;
  for (let attempt = 0; attempt < MODELS.length + 1; attempt++) {
    const model = MODELS[Math.min(attempt, MODELS.length - 1)];
    const resp = await fetch(AI_URL, {
      method: "POST",
      headers: { Authorization: `Bearer ${aiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model,
        temperature: 0,
        response_format: { type: "json_object" },
        messages: [{ role: "user", content }],
      }),
    });
    if (resp.ok) return await resp.json();
    lastStatus = resp.status;
    const detail = (await resp.text().catch(() => "")).slice(0, 300);
    console.error("vision_attempt", model, lastStatus, detail);
    // 503 high demand / 429 rate limit / 5xx are worth another shot on the next model;
    // a 400/401 is our bug or a bad key and will fail identically every time.
    if (lastStatus !== 429 && lastStatus !== 503 && lastStatus < 500) break;
    await new Promise((r) => setTimeout(r, 500 * (attempt + 1)));
  }
  throw new Error(`vision_failed_${lastStatus}`);
}

const PROMPT = `Eres el verificador de reciclaje de MAKI. Analiza la imagen real de residuos.
Responde SOLO JSON: {"items":[{"code":<uno de PET,ALU,VID,CAR,PIL>,"quantity":entero>=1,"confidence":0..1,"quality":0..1,"box":[x0,y0,x1,y1]}],"spoof":{"screen":booleano,"reason":texto}}.
Reglas: incluye solo materiales reciclables visibles con esos codigos (PET=botella plastica, ALU=lata, VID=vidrio, CAR=carton, PIL=pilas); ignora lo demas.
quantity = cuantas unidades de ese material se ven en ESTA imagen (si ves 4 botellas, quantity=4).
quality = limpieza/estado (0 sucio o irreconocible, 1 limpio e integro).
box = recuadro que encierra ese material, en fracciones 0..1 del ancho y alto de la imagen: x0,y0 = esquina superior izquierda; x1,y1 = esquina inferior derecha. Si hay varias unidades del mismo material, encierra el grupo completo.
Si la imagen parece una foto de una PANTALLA, una foto de otra foto, o una imagen impresa (no un objeto fisico real frente a la camara), pon spoof.screen=true y items=[].`;

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const aiKey = Deno.env.get("GEMINI_API_KEY") ?? "";
    // A missing secret used to surface as a generic "check your connection" on the
    // phone and cost days of debugging; say it out loud instead.
    if (!aiKey) return json({ error: "server_misconfig", detail: "GEMINI_API_KEY no esta configurada en los secrets" }, 500);

    const authHeader = req.headers.get("Authorization") ?? "";
    const userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: { user }, error: uerr } = await userClient.auth.getUser();
    if (uerr || !user) return json({ error: "unauthorized" }, 401);

    const payload = await req.json().catch(() => ({}));
    const sessionId = String(payload.session_id ?? "");
    if (!sessionId) return json({ error: "no_session" }, 400);

    const admin = createClient(supabaseUrl, serviceKey);
    const { data: materials } = await admin
      .from("materials").select("id,code,name,points_per_unit").eq("is_active", true);
    const byCode = new Map((materials ?? []).map((m: any) => [m.code, m]));
    const pointsFor = (code: string, qty: number, quality: number) =>
      // quality scales the award 50%..100% — dirty/crushed items earn less, like an RVM grade.
      Math.round(qty * Number(byCode.get(code)?.points_per_unit ?? 0) * (0.5 + 0.5 * quality));

    // ---- commit: close the session from the signed frames -----------------
    if (payload.mode === "commit") {
      const observations: any[] = Array.isArray(payload.observations)
        ? payload.observations.slice(0, MAX_OBSERVATIONS)
        : [];
      // Best count per material across the walk-around. MAX, never SUM: the same
      // bottle shows up in many frames, and summing would pay for it once per frame.
      const best = new Map<string, Counted>();
      for (const obs of observations) {
        const items: Counted[] = (Array.isArray(obs?.items) ? obs.items : []).map((i: any) => ({
          code: String(i.code),
          quantity: Math.max(1, Math.round(Number(i.quantity) || 1)),
          quality: clamp01(Number(i.quality)),
        }));
        if (items.length === 0) continue;
        if (await sign(serviceKey, sessionId, items) !== String(obs.token ?? "")) {
          return json({ error: "bad_observation" }, 400);
        }
        for (const it of items) {
          if (!byCode.has(it.code)) continue;
          const prev = best.get(it.code);
          if (!prev) best.set(it.code, { ...it });
          else {
            prev.quantity = Math.max(prev.quantity, it.quantity);
            prev.quality = Math.max(prev.quality, it.quality);
          }
        }
      }
      if (best.size === 0) {
        return json({ rejected: "no_material", message: "No detecte materiales reciclables. Recorre tus residuos e intenta de nuevo." });
      }

      const items = [...best.values()].map((it) => {
        const m = byCode.get(it.code)!;
        return {
          material_id: m.id as string, code: m.code as string, name: m.name as string,
          quantity: it.quantity, quality_score: it.quality, points: pointsFor(it.code, it.quantity, it.quality),
        };
      });
      const totalPoints = items.reduce((s, it) => s + it.points, 0);
      const totalValue = Math.round((totalPoints / 1000) * 100) / 100;

      const { data: gp } = await admin
        .from("generator_profiles").select("streak_multiplier").eq("profile_id", user.id).maybeSingle();

      const { data: det, error: derr } = await admin.from("detections").insert({
        generator_id: user.id,
        status: "pending",
        model_version: MODELS[0],
        total_points: totalPoints,
        total_value: totalValue,
        streak_multiplier: Number(gp?.streak_multiplier ?? 1),
      }).select("id").single();
      if (derr || !det) return json({ error: "persist_failed", detail: derr?.message }, 500);

      const { error: ierr } = await admin.from("detection_items").insert(items.map((it) => ({
        detection_id: det.id,
        material_id: it.material_id,
        quantity: it.quantity,
        confidence: 1,
        quality_score: it.quality_score,
        points: it.points,
      })));
      if (ierr) return json({ error: "items_failed", detail: ierr.message }, 500);

      return json({
        detection_id: det.id,
        status: "pending",
        total_points: totalPoints,
        total_value: totalValue,
        items: items.map((it) => ({
          code: it.code, name: it.name, quantity: it.quantity,
          confidence: 1, quality: it.quality_score, points: it.points,
          material_id: it.material_id, box: null,
        })),
      });
    }

    // ---- preview: score one frame of the live scan ------------------------
    const frames: string[] = Array.isArray(payload.frames)
      ? payload.frames
      : (payload.frame ? [payload.frame] : []);
    if (frames.length === 0) return json({ error: "no_frame" }, 400);

    // 1) Liveness gate — reject a still capture (screenshot / printed photo held
    //    in front of a stationary phone). A real scan moves the phone around the item.
    const motion = payload.motion ?? {};
    if (Number(motion.maxGyroDps ?? 0) < MIN_GYRO_DPS || Number(motion.samples ?? 0) < MIN_SAMPLES) {
      return json({ rejected: "liveness", message: "Mueve el telefono alrededor del residuo para verificar que es real." });
    }

    // 2) Vision model — multi-item detection + bounding boxes + anti-screen flag.
    //    The boxes are what the app draws over the live frame (the YOLO-style overlay
    //    in the Pencil design); an on-device YOLO can replace this later without
    //    changing the response shape.
    const content = [
      { type: "text", text: PROMPT },
      ...frames.slice(0, 2).map((f) => ({ type: "image_url", image_url: { url: `data:image/jpeg;base64,${f}` } })),
    ];
    let aiJson: any;
    try {
      aiJson = await callVision(aiKey, content);
    } catch (_e) {
      // A busy model is a retryable hiccup, not a failed scan: the camera stays open
      // and the next frame tries again, so this must not read as an error to the user.
      return json({ rejected: "busy", message: "El detector esta saturado. Sigue apuntando, reintentando…" });
    }
    let parsed: any = {};
    try { parsed = JSON.parse(aiJson?.choices?.[0]?.message?.content ?? "{}"); } catch { parsed = {}; }

    if (parsed?.spoof?.screen === true) {
      return json({ rejected: "spoof", message: "La imagen parece una foto de una pantalla o impresa. Apunta a residuos reales." });
    }

    // One entry per material: the model sometimes splits a material into several,
    // which would double-count the same objects inside a single frame.
    const merged = new Map<string, any>();
    for (const it of (Array.isArray(parsed.items) ? parsed.items : [])) {
      const m = byCode.get(String(it.code));
      if (!m) continue;
      const qty = Math.max(1, Math.round(Number(it.quantity) || 1));
      const conf = clamp01(Number(it.confidence));
      const qual = clamp01(Number(it.quality ?? 1));
      const box = normBox(it.box);
      const prev = merged.get(m.code);
      if (!prev) {
        merged.set(m.code, { material_id: m.id, code: m.code, name: m.name, quantity: qty, confidence: conf, quality: qual, box });
        continue;
      }
      prev.quantity += qty;
      prev.confidence = Math.max(prev.confidence, conf);
      prev.quality = Math.max(prev.quality, qual);
      // Keep the box that covers both groups, so the overlay still frames everything.
      if (prev.box && box) {
        prev.box = [
          Math.min(prev.box[0], box[0]), Math.min(prev.box[1], box[1]),
          Math.max(prev.box[2], box[2]), Math.max(prev.box[3], box[3]),
        ];
      } else {
        prev.box = prev.box ?? box;
      }
    }
    const items = [...merged.values()];
    if (items.length === 0) {
      return json({ rejected: "no_material", message: "Sin materiales en este cuadro. Acerca el residuo." });
    }

    const counted: Counted[] = items.map((it) => ({ code: it.code, quantity: it.quantity, quality: it.quality }));
    return json({
      status: "preview",
      token: await sign(serviceKey, sessionId, counted),
      total_points: items.reduce((s, it) => s + pointsFor(it.code, it.quantity, it.quality), 0),
      items: items.map((it) => ({
        code: it.code, name: it.name, quantity: it.quantity,
        confidence: it.confidence, quality: it.quality, points: pointsFor(it.code, it.quantity, it.quality),
        material_id: it.material_id, box: it.box,
      })),
    });
  } catch (e) {
    return json({ error: "exception", detail: String(e) }, 500);
  }
});
