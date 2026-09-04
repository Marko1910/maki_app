import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

// MAKI server-authoritative recyclable verifier.
// - Hides the vision model key (never ships in the APK).
// - Anti-spoof: liveness motion gate + VLM screen/photo detection.
// - Sole writer of `detections` + `detection_items`; points/totals computed here,
//   so a tampered client cannot inflate them. Points still SETTLE on rider pickup
//   (settle_pickup) — a photo alone never pays.
//
// Deploy: supabase functions deploy detect-material --project-ref <ref>
// Secret: GEMINI_API_KEY (Dashboard → Edge Functions → Secrets).

// Gemini via its OpenAI-compatible endpoint (drop-in swap from Groq, 2026-07-05).
const AI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
const MODEL = "gemini-2.5-flash";
const MIN_GYRO_DPS = 8; // require real device motion during the capture window
const MIN_SAMPLES = 5;

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

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const aiKey = Deno.env.get("GEMINI_API_KEY")!;

    const authHeader = req.headers.get("Authorization") ?? "";
    const userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: { user }, error: uerr } = await userClient.auth.getUser();
    if (uerr || !user) return json({ error: "unauthorized" }, 401);

    const payload = await req.json().catch(() => ({}));
    const frames: string[] = Array.isArray(payload.frames)
      ? payload.frames
      : (payload.frame ? [payload.frame] : []);
    const motion = payload.motion ?? {};
    if (frames.length === 0) return json({ error: "no_frame" }, 400);

    // 1) Liveness gate — reject a still capture (screenshot / printed photo held
    //    in front of a stationary phone). A real scan moves the phone around the item.
    const maxGyro = Number(motion.maxGyroDps ?? 0);
    const samples = Number(motion.samples ?? 0);
    if (maxGyro < MIN_GYRO_DPS || samples < MIN_SAMPLES) {
      return json({ rejected: "liveness", message: "Mueve el telefono alrededor del residuo para verificar que es real." });
    }

    // 2) Vision model — multi-item detection + bounding boxes + anti-screen flag.
    //    The boxes are what the app draws over the frozen frame (the YOLO-style
    //    overlay in the Pencil design); a VLM places them well enough for a
    //    single hand-held shot, and an on-device YOLO can replace this later
    //    without changing the response shape.
    const prompt = `Eres el verificador de reciclaje de MAKI. Analiza la imagen real de residuos.\nResponde SOLO JSON: {"items":[{"code":<uno de PET,ALU,VID,CAR,PIL>,"quantity":entero>=1,"confidence":0..1,"quality":0..1,"box":[x0,y0,x1,y1]}],"spoof":{"screen":booleano,"reason":texto}}.\nReglas: incluye solo materiales reciclables visibles con esos codigos (PET=botella plastica, ALU=lata, VID=vidrio, CAR=carton, PIL=pilas); ignora lo demas.\nquality = limpieza/estado (0 sucio o irreconocible, 1 limpio e integro).\nbox = recuadro que encierra ese material, en fracciones 0..1 del ancho y alto de la imagen: x0,y0 = esquina superior izquierda; x1,y1 = esquina inferior derecha. Si hay varias unidades del mismo material, encierra el grupo completo.\nSi la imagen parece una foto de una PANTALLA, una foto de otra foto, o una imagen impresa (no un objeto fisico real frente a la camara), pon spoof.screen=true y items=[].`;
    const content = [
      { type: "text", text: prompt },
      ...frames.slice(0, 2).map((f) => ({ type: "image_url", image_url: { url: `data:image/jpeg;base64,${f}` } })),
    ];
    const aiResp = await fetch(AI_URL, {
      method: "POST",
      headers: { Authorization: `Bearer ${aiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: MODEL,
        temperature: 0,
        response_format: { type: "json_object" },
        messages: [{ role: "user", content }],
      }),
    });
    if (!aiResp.ok) {
      const detail = await aiResp.text().catch(() => "");
      console.error("vision_failed", aiResp.status, detail.slice(0, 500));
      return json({ error: "vision_failed", status: aiResp.status }, 502);
    }
    const aiJson = await aiResp.json();
    let parsed: any = {};
    try { parsed = JSON.parse(aiJson?.choices?.[0]?.message?.content ?? "{}"); } catch { parsed = {}; }

    if (parsed?.spoof?.screen === true) {
      return json({ rejected: "spoof", message: "La imagen parece una foto de una pantalla o impresa. Apunta a residuos reales." });
    }

    const admin = createClient(supabaseUrl, serviceKey);
    const { data: materials } = await admin
      .from("materials").select("id,code,name,points_per_unit").eq("is_active", true);
    const byCode = new Map((materials ?? []).map((m: any) => [m.code, m]));

    const rawItems: any[] = Array.isArray(parsed.items) ? parsed.items : [];
    const items = rawItems.map((it) => {
      const m = byCode.get(String(it.code));
      if (!m) return null;
      const qty = Math.max(1, Math.round(Number(it.quantity) || 1));
      const conf = clamp01(Number(it.confidence));
      const qual = clamp01(Number(it.quality ?? 1));
      // quality scales the award 50%..100% — dirty/crushed items earn less, like an RVM grade.
      const points = Math.round(qty * Number(m.points_per_unit) * (0.5 + 0.5 * qual));
      return {
        material_id: m.id, code: m.code, name: m.name, quantity: qty,
        confidence: conf, quality_score: qual, points, box: normBox(it.box),
      };
    }).filter((x): x is NonNullable<typeof x> => x !== null);

    // One row per material. The model sometimes splits a material into several
    // entries; left alone that becomes duplicate detection_items → duplicate
    // pickup_items, and a per-material quantity edit would then write the same
    // count into each of them.
    const merged = new Map<string, typeof items[number]>();
    for (const it of items) {
      const prev = merged.get(it.code);
      if (!prev) { merged.set(it.code, it); continue; }
      prev.quantity += it.quantity;
      prev.points += it.points;
      prev.confidence = Math.max(prev.confidence, it.confidence);
      prev.quality_score = Math.max(prev.quality_score, it.quality_score);
      // Keep the box that covers both groups, so the overlay still frames everything.
      if (prev.box && it.box) {
        prev.box = [
          Math.min(prev.box[0], it.box[0]), Math.min(prev.box[1], it.box[1]),
          Math.max(prev.box[2], it.box[2]), Math.max(prev.box[3], it.box[3]),
        ];
      } else {
        prev.box = prev.box ?? it.box;
      }
    }
    items.length = 0;
    items.push(...merged.values());

    if (items.length === 0) {
      return json({ rejected: "no_material", message: "No detecte materiales reciclables claros. Acerca el residuo e intenta de nuevo." });
    }

    const totalPoints = items.reduce((s, it) => s + it.points, 0);
    const totalValue = Math.round((totalPoints / 1000) * 100) / 100;

    const { data: gp } = await admin
      .from("generator_profiles").select("streak_multiplier").eq("profile_id", user.id).maybeSingle();
    const streakMult = Number(gp?.streak_multiplier ?? 1);

    const { data: det, error: derr } = await admin.from("detections").insert({
      generator_id: user.id,
      status: "pending",
      model_version: MODEL,
      total_points: totalPoints,
      total_value: totalValue,
      streak_multiplier: streakMult,
    }).select("id").single();
    if (derr || !det) return json({ error: "persist_failed", detail: derr?.message }, 500);

    const rows = items.map((it) => ({
      detection_id: det.id,
      material_id: it.material_id,
      quantity: it.quantity,
      confidence: it.confidence,
      quality_score: it.quality_score,
      points: it.points,
    }));
    const { error: ierr } = await admin.from("detection_items").insert(rows);
    if (ierr) return json({ error: "items_failed", detail: ierr.message }, 500);

    return json({
      detection_id: det.id,
      status: "pending",
      total_points: totalPoints,
      total_value: totalValue,
      items: items.map((it) => ({
        code: it.code, name: it.name, quantity: it.quantity,
        confidence: it.confidence, quality: it.quality_score, points: it.points,
        material_id: it.material_id, box: it.box,
      })),
    });
  } catch (e) {
    return json({ error: "exception", detail: String(e) }, 500);
  }
});
