import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

// MAKI educational recycling agent. Grounds a chat model in Peru's container rules +
// the user's real context (points, recent scans, live market prices) so the chat is a
// genuine context-aware agent, not a canned FAQ. Keys stay server-side.
//
// Provider strategy (2026-09-03): Gemini 2.5 Flash primary — a chat turn can afford its
// latency and its answers are the better read — with Groq as the fallback for Gemini's
// 503s. The mirror of detect-material, which needs Groq's ~1s to count a live frame.
// (Groq retired llama-3.3-70b; gpt-oss-120b is the current fast text model there.)
//
// Deploy: supabase functions deploy maki-assistant --project-ref <ref>
// Secrets: GROQ_API_KEY + GEMINI_API_KEY (Dashboard → Edge Functions → Secrets).

const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const GROQ_MODEL = "openai/gpt-oss-120b";
const GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
const GEMINI_MODEL = "gemini-2.5-flash";

const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}

/** One OpenAI-compatible chat call; null on any failure so the caller can fall back. */
async function chat(
  url: string,
  key: string,
  model: string,
  messages: unknown[],
  extra: Record<string, unknown> = {},
): Promise<string | null> {
  try {
    const resp = await fetch(url, {
      method: "POST",
      headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({ model, temperature: 0.4, max_tokens: 1024, messages, ...extra }),
    });
    if (!resp.ok) {
      console.error("chat_failed", model, resp.status, (await resp.text().catch(() => "")).slice(0, 300));
      return null;
    }
    const j = await resp.json();
    return j?.choices?.[0]?.message?.content?.trim() || null;
  } catch (e) {
    console.error("chat_error", model, String(e));
    return null;
  }
}

const RULES = `Reglas de reciclaje en Peru (contenedores):\n- AMARILLO: plasticos (PET, botellas), latas, tetrapak.\n- AZUL: papel y carton (secos, sin grasa).\n- VERDE: vidrio (enjuagado, sin tapas metalicas).\n- Puntos especiales: pilas/baterias y residuos peligrosos (NUNCA al tacho comun).`;

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const groqKey = Deno.env.get("GROQ_API_KEY") ?? Deno.env.get("grok_api_camera") ?? "";
    const geminiKey = Deno.env.get("GEMINI_API_KEY") ?? "";

    const authHeader = req.headers.get("Authorization") ?? "";
    const userClient = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: authHeader } } });
    const { data: { user }, error: uerr } = await userClient.auth.getUser();
    if (uerr || !user) return json({ error: "unauthorized" }, 401);

    const payload = await req.json().catch(() => ({}));
    const incoming: any[] = Array.isArray(payload.messages) ? payload.messages : [];
    const messages = incoming
      .filter((m) => m && (m.role === "user" || m.role === "assistant") && typeof m.content === "string")
      .slice(-8);
    if (messages.length === 0) return json({ error: "no_message" }, 400);

    // Personalisation context (best-effort, service role).
    const admin = createClient(supabaseUrl, serviceKey);
    const [gpRes, detRes, priceRes] = await Promise.all([
      admin.from("generator_profiles").select("points_balance,current_streak_days").eq("profile_id", user.id).maybeSingle(),
      admin.from("detections").select("total_points,detection_items(quantity,materials(name))").eq("generator_id", user.id).order("detected_at", { ascending: false }).limit(3),
      admin.from("v_material_prices").select("name,price_per_kg,pct_change"),
    ]);
    const gp: any = gpRes.data;
    const priceLines = (priceRes.data ?? [])
      .map((p: any) => `${p.name}: S/${p.price_per_kg}/kg (${Number(p.pct_change) >= 0 ? "+" : ""}${p.pct_change}% esta semana)`)
      .join("; ");
    const recent = (detRes.data ?? [])
      .map((d: any) => (d.detection_items ?? []).map((i: any) => `${i.quantity} ${i.materials?.name ?? ""}`.trim()).join(", "))
      .filter((s: string) => s.length > 0)
      .join(" | ") || "sin escaneos aun";

    const system = `Eres Maki, el agente educativo de reciclaje de la app MAKI (Peru). Responde en espanol, claro y breve (maximo 4 frases), tono cercano y motivador. Ayudas a clasificar residuos, resuelves dudas y motivas a reciclar.\n${RULES}\nPrecios de mercado actuales: ${priceLines}.\nContexto del usuario: ${gp ? `${gp.points_balance} Eco-Puntos, racha de ${gp.current_streak_days} dias` : "usuario nuevo"}. Escaneos recientes: ${recent}.\nSi preguntan por precios, usa SOLO los precios dados. Cuando convenga, sugiere acciones de la app (escanear un residuo o pedir un recojo). No inventes precios ni datos que no tengas.`;

    const msgs = [{ role: "system", content: system }, ...messages.map((m) => ({ role: m.role, content: m.content }))];

    // Gemini first (better conversational answers); Groq catches its 503s transparently.
    const reply = (geminiKey ? await chat(GEMINI_URL, geminiKey, GEMINI_MODEL, msgs, { reasoning_effort: "low" }) : null)
      ?? (groqKey ? await chat(GROQ_URL, groqKey, GROQ_MODEL, msgs) : null);
    if (!reply) return json({ error: "assistant_failed" }, 502);
    return json({ reply });
  } catch (e) {
    return json({ error: "exception", detail: String(e) }, 500);
  }
});
