import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "jsr:@supabase/supabase-js@2";
import { BRANDS } from "./brands.ts";

// Ruma server-authoritative recyclable verifier — continuous scan.
// The camera stays open while the user walks around their waste. Each frame is a
// `preview`: the server scores it, returns the items it saw and SIGNS that count.
// `commit` replays those signed observations and writes the one detection — so the
// client can never inflate a count it did not earn, without any per-frame DB write.
// - Hides the vision model key (never ships in the APK).
// - Anti-spoof: liveness motion gate per frame + VLM screen/photo detection.
// - Sole writer of `detections` + `detection_items`; points computed here.
//   Points still SETTLE on rider pickup (settle_pickup) — a scan alone never pays.
// - `health` (operators only): pings every provider and lists the models each key can use.
//
// Deploy: supabase functions deploy detect-material --project-ref <ref>
// Secrets: GROQ_API_KEY (or grok_api_camera) + GEMINI_API_KEY.

// ---- Vision providers: the "always answer" protocol ------------------------
// 1. Several providers, each on its OpenAI-compatible endpoint, in preference order.
// 2. Every request caps its output (max_tokens). Groq's free tier meters 1000 output
//    tokens/min per model and counts the RESERVATION, not what was used: uncapped, one
//    call reserved the whole window ("Request too large"); at 500, two frames used up
//    the minute. A frame's JSON is ~70–200 tokens, so 256 fits and leaves room.
// 3. Circuit breaker (table vision_provider_status): a provider that is rate-limited
//    or down sits out its cooldown instead of costing seconds on every frame.
// 4. Hedged requests: if the first provider has not answered in HEDGE_MS, the next
//    one starts in parallel and the first valid answer wins. Free tiers are slow at
//    random (Groq measured 1.7 s and 10.8 s on the same frame); waiting on each in turn
//    is what made the scanner look frozen.
// 5. `health` lists the models each key can reach, to swap a retired model quickly.
const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
const GROQ_KEYS = ["GROQ_API_KEY", "grok_api_camera"]; // the key lives under either name
const GEMINI_KEYS = ["GEMINI_API_KEY"];

const MAX_OUTPUT_TOKENS = 256;
// Gemini meters requests, not reserved output tokens, so it can afford a full answer:
// four bottles of four brands is four items, and at 256 the JSON was cut mid-item
// (finish_reason "length") — the frame came back empty, and only answers lazy enough
// to lump every bottle under one brand ("Gatorade x4") fitted. Groq keeps 256 (its
// free tier meters the reservation against 1000 output tokens/min).
const GEMINI_OUTPUT_TOKENS = 1024;

type Provider = {
  name: string;
  url: string;
  model: string;
  secrets: string[];
  timeoutMs: number;
  maxTokens?: number;
  extra?: Record<string, unknown>;
};

// Order = preference. Gemini first since 2026-11-01: on the same 3-bottle frame Groq
// qwen answered 5 every time and Gemini 3 every time (8/8), and the scan takes the MAX
// per group, so one over-count inflates the whole scan. Latency is close (Gemini lite
// 1.6–2.5 s, qwen 0.8–3 s) and Groq's free tier only carries ~4 frames/min anyway.
// qwen stays as the last fallback: a frame it over-counts beats "detector ocupado".
// (qwen3.6 answers invalid JSON; gemini-2.5-flash times out — both left out.)
const PROVIDERS: Provider[] = [
  // Latest first: on a clear label 2.5-flash-lite gave no brand in 5 of 5 tries (2026-11-03); latest named it.
  { name: "gemini-lite-latest", url: GEMINI_URL, model: "gemini-flash-lite-latest", secrets: GEMINI_KEYS, timeoutMs: 10000, maxTokens: GEMINI_OUTPUT_TOKENS },
  { name: "gemini-lite", url: GEMINI_URL, model: "gemini-2.5-flash-lite", secrets: GEMINI_KEYS, timeoutMs: 10000, maxTokens: GEMINI_OUTPUT_TOKENS, extra: { reasoning_effort: "none" } },
  { name: "gemini-3.5-lite", url: GEMINI_URL, model: "gemini-3.5-flash-lite", secrets: GEMINI_KEYS, timeoutMs: 10000, maxTokens: GEMINI_OUTPUT_TOKENS },
  { name: "groq-qwen", url: GROQ_URL, model: "qwen/qwen3.8-27b", secrets: GROQ_KEYS, timeoutMs: 8000, maxTokens: 460 },
];

/** The whole vision call's budget: a frame is only useful while the user still points at it. */
const VISION_BUDGET_MS = 15000;
/** Start the next provider in parallel if the current one is still silent after this. */
const HEDGE_MS = 3000;

/** How long a provider sits out after each kind of failure (seconds). */
function cooldownFor(status: number, retryAfterS: number | null): number {
  if (status === 429) return Math.min(Math.max(retryAfterS ?? 30, 5), 120); // honour its own clock
  if (status === 401 || status === 403) return 3600; // bad key: an operator must fix it
  if (status === 400 || status === 404) return 600;  // model gone / param refused: config bug
  return 30;                                          // timeout, 5xx, empty answer
}

/** The provider's key, from whichever of its secret names is set. */
function keyOf(provider: { secrets: string[] }): string | undefined {
  for (const name of provider.secrets) {
    const value = Deno.env.get(name);
    if (value) return value;
  }
  return undefined;
}

/** "retry-after: 7" or Groq's "Please try again in 7.3s". */
function retryAfterSeconds(resp: Response, body: string): number | null {
  const header = Number(resp.headers.get("retry-after"));
  if (Number.isFinite(header) && header > 0) return header;
  const m = body.match(/try again in ([\d.]+)\s*s/i);
  return m ? Math.ceil(Number(m[1])) : null;
}

const MIN_GYRO_DPS = 8; // require real device motion during the capture window
const MIN_SAMPLES = 5;
const MAX_OBSERVATIONS = 120; // one scan session's worth of frames, across all its groups
const OBSERVATION_TTL_MS = 30 * 60 * 1000;
// "+ Agregar más": a session is a run of groups. Within a group the same objects reappear
// frame after frame (MAX); different groups are different objects (SUM). The group rides
// inside the signature, so a frame can't be moved to another group after the fact.
// ponytail: re-scanning the same bottles as a new group is not detected here — the rider
// checks the count at the door and the acopio pays by weight, which bounds that.
const MAX_GROUPS = 10;

type Photo = { quantity: number; quality: number; brands: Record<string, number> };

/**
 * One material's photos, oldest first, folded into the pile they show. A photo with no
 * brand seen before is new waste and adds up (the friend's bottle: 3 + 1 = 4). A photo
 * repeating a counted brand is the same pile framed again: it only adds what grew, per
 * brand and per unreadable label (3, then those 3 plus a Gatorade: 4, not 7). Never adds
 * more than the photo showed, so the fold can't pay above the sum of the signed counts.
 * ponytail: two bottles of one brand in separate photos read as one; the family fixes it
 * with + on the result, and payment is by weight anyway. Bottle identity would need boxes.
 */
function foldPhotos(photos: Photo[]) {
  const brands: Record<string, number> = {};
  let unnamed = 0, quantity = 0;
  const added: number[] = [];
  for (const photo of photos) {
    const samePile = Object.keys(photo.brands).some((b) => b in brands);
    const named = Object.values(photo.brands).reduce((sum, n) => sum + n, 0);
    const shownUnnamed = Math.max(0, photo.quantity - named);
    // Same pile: a counted brand missing from this photo may be an unreadable one in it.
    const covered = Object.entries(brands).reduce((sum, [b, n]) => sum + Math.max(0, n - (photo.brands[b] ?? 0)), 0);
    let newlyNamed = 0;
    for (const [brand, units] of Object.entries(photo.brands)) {
      const extra = samePile ? Math.max(0, units - (brands[brand] ?? 0)) : units;
      brands[brand] = (brands[brand] ?? 0) + extra;
      newlyNamed += extra;
    }
    // Same pile: a label that became legible may be a bottle already counted as unreadable.
    const grew = samePile
      ? Math.max(0, newlyNamed + Math.max(0, shownUnnamed - covered) - unnamed)
      : newlyNamed + shownUnnamed;
    const add = Math.min(grew, photo.quantity);
    unnamed = Math.max(0, unnamed + add - newlyNamed);
    quantity += add;
    added.push(add);
  }
  return { quantity, brands, added };
}

/** The group a request names (absent = 0, the only group older apps know), or null if invalid. */
function groupOf(raw: unknown): number | null {
  if (raw === undefined || raw === null) return 0;
  // A JSON number only: Number(true) or Number("") would slip in as groups 1 and 0.
  if (typeof raw !== "number") return null;
  return Number.isInteger(raw) && raw >= 0 && raw < MAX_GROUPS ? raw : null;
}

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
/** Gemini's native detection order: [ymin, xmin, ymax, xmax] on a 0..1000 grid. Asking for x first got the
 * box transposed on a lying bottle (a vertical strip down the middle instead of the bottle). */
function normBox2d(raw: unknown): number[] | null {
  if (!Array.isArray(raw) || raw.length < 4) return null;
  const [y0, x0, y1, x1] = raw.slice(0, 4).map(Number);
  return normBox([x0 / 1000, y0 / 1000, x1 / 1000, y1 / 1000]);
}

function normBox(raw: unknown): number[] | null {
  if (!Array.isArray(raw) || raw.length < 4) return null;
  const values = raw.slice(0, 4).map(Number);
  // Qwen answers on a 0..1000 grid, Gemini in 0..1 fractions — normalise both.
  const scale = values.some((n) => n > 1) ? 1000 : 1;
  let [x0, y0, x1, y1] = values.map((n) => clamp01(n / scale));
  if (x1 < x0) [x0, x1] = [x1, x0];
  if (y1 < y0) [y0, y1] = [y1, y0];
  // A degenerate box would draw as a line; drop it rather than show a glitch.
  if (x1 - x0 < 0.02 || y1 - y0 < 0.02) return null;
  return [x0, y0, x1, y1].map((n) => Math.round(n * 1000) / 1000);
}

// ---- Signed observations -------------------------------------------------
// A preview result is only worth what the server itself saw, so it leaves with an
// HMAC over the caller, the session, the issue time and the counts. Signing the
// session alone let another account replay the same payload for as long as it liked,
// and no per-frame database write is needed to stop that.
const encoder = new TextEncoder();

type Counted = { code: string; quantity: number; quality: number; brand?: string | null };

/** Letters and digits only, lowercase, no accents: the key two readings of one label share. */
function brandKey(s: string): string {
  return s.toLocaleLowerCase("es").normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[^a-z0-9]/g, "");
}

const BRAND_BY_KEY = new Map<string, string>();
for (const b of BRANDS) for (const n of [b.name, ...(b.aliases ?? [])]) BRAND_BY_KEY.set(brandKey(n), b.name);

/** Levenshtein distance — the brand keys are a few letters, so the table is tiny. */
function editDistance(a: string, b: string): number {
  let prev = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
    prev = cur;
  }
  return prev[b.length];
}

/**
 * The catalog's official name for a label reading, or null if it isn't a known brand.
 * Tolerates an OCR slip ("Acuafiel" → Agua Fiel): one letter on short names, two on long
 * ones. Short readings ("Ciel") get no slack: too many brands are one letter apart there.
 */
function catalogBrand(reading: string): string | null {
  const key = brandKey(reading);
  if (!key) return null;
  const exact = BRAND_BY_KEY.get(key);
  if (exact) return exact;
  if (key.length < 5) return null;
  const slack = key.length >= 8 ? 2 : 1;
  let best: string | null = null;
  let bestDistance = slack + 1;
  let tied = false;
  for (const [k, name] of BRAND_BY_KEY) {
    if (Math.abs(k.length - key.length) > slack) continue;
    const d = editDistance(key, k);
    if (d < bestDistance) { bestDistance = d; best = name; tied = false; }
    else if (d === bestDistance && name !== best) tied = true;
  }
  // Equally close to two brands ("Aguaciel": Aguafiel or Agua Cielo?) is not a reading.
  return tied ? null : best;
}

/** Marca normalizada: "coca cola", "COCA-COLA" y "Coca Cola" son la misma. */
function spacedText(s: string): string {
  return s.toLocaleLowerCase("es").normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]+/g, " ").trim();
}

/**
 * The one brand a piece of label text belongs to, or null if none or two claim it: the words
 * the model copied off a half-hidden label, or a reading the catalog's names miss. Whole words
 * first, so "Loa" doesn't fire inside another word; [withFragments] also tries pieces of the
 * logos, and only for text copied off the label. The model reads, this decides: no guessing.
 */
function brandFromText(raw: unknown, withFragments = false): string | null {
  const spaced = spacedText(String(raw ?? "").slice(0, 120));
  if (!spaced) return null;
  const only = (names: string[]) => (names.length === 1 ? names[0] : null);
  const inWords = BRANDS.filter((b) => b.texts?.some((t) => ` ${spaced} `.includes(` ${spacedText(t)} `))).map((b) => b.name);
  if (inWords.length > 0 || !withFragments) return only(inWords);
  const compact = spaced.replace(/ /g, "");
  return only(BRANDS.filter((b) => b.fragments?.some((f) => compact.includes(f))).map((b) => b.name));
}

function normBrand(raw: unknown): string | null {
  const t = String(raw ?? "").trim().replace(/\s+/g, " ").slice(0, 40);
  if (!t || /^(null|none|n\/a|ninguna|sin marca|desconocida)$/i.test(t)) return null;
  // "Aguafiel", "Acuafiel" y "AGUA FIEL" son la misma etiqueta: el nombre oficial del catálogo.
  const known = catalogBrand(t) ?? brandFromText(t);
  if (known) return known;
  // Fuera del catálogo: "COCA COLA" y "coca cola" tienen que caer en la misma fila del reporte.
  return t
    .toLocaleLowerCase("es")
    .split(/([ -])/)
    .map((p) => (p === " " || p === "-" ? p : p.charAt(0).toLocaleUpperCase("es") + p.slice(1)))
    .join("");
}

function canonical(userId: string, sessionId: string, iat: number, items: Counted[], group = 0): string {
  const parts = items
    .map((i) => `${i.code}:${Math.round(i.quantity)}:${Number(i.quality).toFixed(3)}:${i.brand ?? ""}`)
    .sort();
  // Group 0 keeps the pre-group form, so tokens issued to an older app still verify.
  return `${userId}|${sessionId}|${iat}${group ? `|g${group}` : ""}|${parts.join(",")}`;
}

// Bump when the prompt, catalog or counting rules change; model aliases can drift too.
const PIPELINE_VERSION = "ruma-photo-2026-11-18-v3"; // box_2d [ymin,xmin,ymax,xmax], whole container
type Provenance = { provider: string; model: string; pipeline: string; resolved_model?: string };

function tokenParts(token: unknown): { iat: number; mac: string; metadata: string; source: Provenance | null } | null {
  if (typeof token !== "string" || token.length > 2048) return null;
  const legacy = /^(\d+)\.([A-Za-z0-9+/]{43}=)$/.exec(token);
  if (legacy) return { iat: Number(legacy[1]), mac: legacy[2], metadata: "", source: null };
  const v2 = /^v2\.(\d+)\.([A-Za-z0-9+/=]+)\.([A-Za-z0-9+/]{43}=)$/.exec(token);
  if (!v2) return null;
  try {
    const source = JSON.parse(atob(v2[2]));
    if (!source || !["model,pipeline,provider", "model,pipeline,provider,resolved_model"].includes(Object.keys(source).sort().join()) ||
        (source.resolved_model !== undefined && (typeof source.resolved_model !== "string" || source.resolved_model.length === 0 || source.resolved_model.length > 160)) ||
        ![source.provider, source.model, source.pipeline].every((v) => typeof v === "string" && v.length > 0 && v.length <= 160)) return null;
    return { iat: Number(v2[1]), mac: v2[3], metadata: v2[2], source };
  } catch { return null; }
}

async function sign(secret: string, userId: string, sessionId: string, iat: number, items: Counted[], group = 0, source?: Provenance): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const metadata = source ? btoa(JSON.stringify(source)) : "";
  const message = canonical(userId, sessionId, iat, items, group) + (source ? `|v2|${metadata}` : "");
  const mac = await crypto.subtle.sign("HMAC", key, encoder.encode(message));
  return `${source ? `v2.${iat}.${metadata}` : iat}.${btoa(String.fromCharCode(...new Uint8Array(mac)))}`;
}

function constantTimeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
  return difference === 0;
}

async function verifyObservation(secret: string, userId: string, sessionId: string, items: Counted[], token: unknown, group = 0): Promise<string | null> {
  const parsed = tokenParts(token);
  if (!parsed) return "bad_observation";
  const { iat } = parsed;
  if (!Number.isSafeInteger(iat)) return "bad_observation";
  if (iat > Date.now() + 60_000) return "bad_observation";
  if (Date.now() - iat > OBSERVATION_TTL_MS) return "stale_observation";
  const expected = await sign(secret, userId, sessionId, iat, items, group, parsed.source ?? undefined);
  return constantTimeEqual(encoder.encode(expected), encoder.encode(token as string)) ? null : "bad_observation";
}

// ---- Provider calls + circuit breaker ------------------------------------

type Attempt = {
  provider: string;
  model: string;
  ok: boolean;
  status: number;
  ms: number;
  error?: string;
  body?: any;
  cooldownS?: number;
};

/** Why a 200 answer can't be used — cut at max_tokens or not JSON — or null if it can. */
function unusableAnswer(body: any): string | null {
  if (body?.choices?.[0]?.finish_reason === "length") return "truncated";
  try {
    const parsed = JSON.parse(body?.choices?.[0]?.message?.content ?? "");
    return parsed && typeof parsed === "object" ? null : "invalid_json";
  } catch {
    return "invalid_json";
  }
}

/** One request to one provider, never throws: the outcome is data. */
async function callProvider(p: Provider, content: unknown[], timeoutMs: number): Promise<Attempt> {
  const key = keyOf(p);
  const base = { provider: p.name, model: p.model };
  if (!key) return { ...base, ok: false, status: 0, ms: 0, error: "no_key" };
  const t0 = Date.now();
  try {
    const resp = await fetch(p.url, {
      method: "POST",
      headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: p.model,
        temperature: 0,
        max_tokens: p.maxTokens ?? MAX_OUTPUT_TOKENS,
        response_format: { type: "json_object" },
        messages: [{ role: "user", content }],
        ...p.extra,
      }),
      signal: AbortSignal.timeout(timeoutMs),
    });
    const ms = Date.now() - t0;
    if (!resp.ok) {
      const text = await resp.text().catch(() => "");
      return {
        ...base, ok: false, status: resp.status, ms, error: text.slice(0, 300),
        cooldownS: cooldownFor(resp.status, retryAfterSeconds(resp, text)),
      };
    }
    const body = await resp.json();
    if (!body?.choices?.[0]?.message?.content) {
      return { ...base, ok: false, status: 502, ms, error: "empty_answer", body, cooldownS: cooldownFor(502, null) };
    }
    // A cut or garbled answer used to "win" the race and turn into an empty frame.
    // It says nothing about the provider's health, so no cooldown: just try the next.
    const unusable = unusableAnswer(body);
    if (unusable) return { ...base, ok: false, status: 502, ms, error: unusable, body, cooldownS: 0 };
    return { ...base, ok: true, status: 200, ms, body };
  } catch (e) {
    return { ...base, ok: false, status: 504, ms: Date.now() - t0, error: String(e).slice(0, 200), cooldownS: cooldownFor(504, null) };
  }
}

/** Records an attempt in vision_provider_status (after the response is sent, when possible). */
function note(admin: SupabaseClient, a: Attempt) {
  const now = new Date();
  const row: Record<string, unknown> = {
    provider: a.provider,
    last_status: a.status,
    last_latency_ms: a.ms,
    last_error: a.ok ? null : (a.error ?? null),
    cooldown_until: a.ok ? null : new Date(now.getTime() + (a.cooldownS ?? 30) * 1000).toISOString(),
    updated_at: now.toISOString(),
  };
  if (a.ok) row.last_ok_at = now.toISOString();
  if (!a.ok) console.error("vision_attempt", a.provider, a.status, a.ms, a.error ?? "");
  const write = admin.from("vision_provider_status").upsert(row).then(() => {}, () => {});
  const runtime = (globalThis as any).EdgeRuntime;
  if (runtime?.waitUntil) runtime.waitUntil(write);
}

type VisionResult = { body: any; provider: string; model: string } | { retryAfterMs: number };

/**
 * First valid answer wins. Healthy providers go first, cooling ones last. A provider
 * that fails hands over at once; one that is merely slow gets company after HEDGE_MS.
 */
async function callVision(admin: SupabaseClient, content: unknown[]): Promise<VisionResult> {
  const deadline = Date.now() + VISION_BUDGET_MS;
  const { data } = await admin.from("vision_provider_status").select("provider,cooldown_until");
  const until = new Map<string, number>((data ?? []).map((r: any) => [r.provider, r.cooldown_until ? Date.parse(r.cooldown_until) : 0]));
  const now = Date.now();
  const usable = PROVIDERS.filter((p) => keyOf(p));
  const cooling = (p: Provider) => (until.get(p.name) ?? 0) > now;
  const ordered = [
    ...usable.filter((p) => !cooling(p)),
    ...usable.filter(cooling).sort((a, b) => (until.get(a.name) ?? 0) - (until.get(b.name) ?? 0)),
  ];

  const busy = (): VisionResult => {
    // Tell the app when the first provider is back instead of a blind wait.
    const soonest = Math.min(...usable.map((p) => until.get(p.name) ?? 0));
    return { retryAfterMs: Math.min(Math.max(soonest - Date.now(), 3000), 15000) };
  };

  return await new Promise<VisionResult>((resolve) => {
    let next = 0;
    let inFlight = 0;
    let settled = false;
    const finish = (r: VisionResult) => { if (!settled) { settled = true; resolve(r); } };

    const launch = () => {
      if (settled) return;
      const left = deadline - Date.now();
      if (next >= ordered.length || left < 1500) {
        if (inFlight === 0) finish(busy());
        return;
      }
      const p = ordered[next++];
      inFlight++;
      const hedge = setTimeout(launch, HEDGE_MS);
      callProvider(p, content, Math.min(p.timeoutMs, left)).then((a) => {
        inFlight--;
        clearTimeout(hedge);
        note(admin, a); // a late answer still spent quota: record it
        if (settled) return;
        if (a.ok) return finish({ body: a.body, provider: p.name, model: p.model });
        until.set(p.name, Date.now() + (a.cooldownS ?? 30) * 1000);
        launch(); // failed fast: hand over now, don't wait for the hedge timer
      });
    };
    launch();
  });
}

/** Model ids a key can reach (OpenAI-compatible GET /models), or null on failure. */
async function listModels(url: string, key: string | undefined): Promise<string[] | null> {
  if (!key) return null;
  try {
    const r = await fetch(url, { headers: { Authorization: `Bearer ${key}` }, signal: AbortSignal.timeout(5000) });
    const j = await r.json();
    return (j.data ?? []).map((m: any) => String(m.id));
  } catch {
    return null;
  }
}

// No bare example brand names in the prompt: a list ("Cielo", "Loa", "Gatorade"…) primed
// the model to answer them — on the phone it read a half-hidden "…el" (Agua Fiel) as Cielo.
// What it gets instead is the researched catalog (brands.ts) with how each label LOOKS,
// so a match has to agree on the design, not just be a popular name.
/**
 * The brand a model answer has evidence for: what it copied off the label decides, not what
 * it says the brand is. Asked nicely not to guess, it still named "Cielo" on a water whose
 * label a Gatorade covered down to one letter, and "San Luis" on two distant milk bottles.
 * A brand off the catalog stands only if its name is literally in the text.
 */
function evidencedBrand(brand: unknown, texto: unknown): string | null {
  const read = brandFromText(texto) ?? catalogBrand(String(texto ?? "")) ?? brandFromText(texto, true);
  if (read) return read;
  const claimed = normBrand(brand);
  if (!claimed) return null;
  return ` ${spacedText(String(texto ?? ""))} `.includes(` ${spacedText(claimed)} `) ? claimed : null;
}

const BRAND_GUIDE = BRANDS.filter((b) => b.look)
  .map((b) => `- ${b.name}: ${b.look}${b.printed?.length ? ` Impreso: ${b.printed.join(" / ")}.` : ""}`)
  .join("\n");

const PROMPT = `Eres el verificador de reciclaje de MAKI. Analiza la imagen real de residuos.
Responde SOLO JSON: {"items":[{"code":<uno de PET,ALU,VID,CAR,PIL>,"quantity":entero>=1,"confidence":0..1,"quality":0..1,"tapa":texto,"liquido":texto,"etiqueta":texto,"texto":texto,"brand":texto|null,"box_2d":[ymin,xmin,ymax,xmax]}],"spoof":{"screen":booleano,"reason":texto}}.
Reglas: incluye solo materiales reciclables visibles con esos codigos (PET=botella plastica, ALU=lata, VID=vidrio, CAR=carton, PIL=pilas); ignora lo demas.
Cuenta SOLO los residuos que la persona muestra en primer plano (sobre la mesa, el piso o en la mano, cerca de la camara). NO cuentes objetos del fondo (estantes, muebles, otra habitacion), ni lo que esta dentro de bolsas, ni objetos cortados en el borde que no se distinguen.
quantity = cuantas unidades de ese material (y esa marca) se ven en ESTA imagen. Cuenta cada objeto una sola vez.
quality = limpieza/estado (0 sucio o irreconocible, 1 limpio e integro).
brand = la marca DE ESA unidad. Devuelve un item por cada marca distinta: 4 botellas de 4 marcas son 4 items con quantity=1. Nunca pongas a una botella la marca que leiste en otra.
Como decidir la marca: (1) si lees el nombre en la etiqueta, usa ese nombre escrito como en el CATALOGO si esta ahi. (2) Si el nombre no se lee completo, usa una marca del CATALOGO solo si ves parte del nombre o del logo y el diseno visible (colores, logo, tapa, forma) coincide claramente con su descripcion y no con otra parecida. Si la etiqueta esta tapada por otra botella o de espaldas, la tapa o el color del plastico solos no bastan: brand null. (3) Si no, brand null. No completes palabras cortadas con marcas que no esten en el catalogo y no elijas una marca solo porque es comun.
Mira cada envase por separado, de izquierda a derecha, y llena sus campos EN ESTE ORDEN, de lo que ves a lo que concluyes: tapa = color de la tapa (una palabra). liquido = color del contenido (una palabra). etiqueta = colores de la etiqueta y si se ve entera, tapada o de espaldas (hasta 4 palabras). texto = hasta 5 palabras o pedazos de palabra que SI alcanzas a leer en la etiqueta de ESE envase, copiados tal cual aunque esten cortados; "" si no lees nada. Nunca lo completes ni lo inventes: si solo ves una letra, escribe esa letra. Una marca sin su texto leido se descarta. brand va al final: compara lo anterior con el CATALOGO y sus diferencias; si la tapa o los colores contradicen la descripcion de una marca, no es esa marca.
CATALOGO de marcas que se venden en Peru (nombre: como se ve):
${BRAND_GUIDE}
box_2d = recuadro de ESA unidad en escala 0..1000 de la imagen, en el orden [ymin, xmin, ymax, xmax] (primero lo vertical). Encierra el envase COMPLETO: desde la punta de la tapa hasta la base, de borde a borde; nunca solo la tapa ni solo la etiqueta. Si hay varias unidades iguales, encierra el grupo completo.
Si la imagen parece una foto de una PANTALLA, una foto de otra foto, o una imagen impresa (no un objeto fisico real frente a la camara), pon spoof.screen=true y items=[].
Se breve: el campo reason con menos de 10 palabras. Escribe el JSON compacto en una sola linea, sin saltos ni sangria.`;

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    // A missing secret used to surface as a generic "check your connection" on the
    // phone and cost days of debugging; say it out loud instead.
    if (!PROVIDERS.some(keyOf)) {
      return json({ error: "server_misconfig", detail: "Falta la clave de vision (GROQ_API_KEY/grok_api_camera o GEMINI_API_KEY) en los secrets" }, 500);
    }

    const authHeader = req.headers.get("Authorization") ?? "";
    const userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: { user }, error: uerr } = await userClient.auth.getUser();
    if (uerr || !user) return json({ error: "unauthorized" }, 401);

    const payload = await req.json().catch(() => ({}));
    const admin = createClient(supabaseUrl, serviceKey);

    // ---- health: every provider at once, for whoever keeps the scanner alive ----
    if (payload.mode === "health") {
      const { data: me } = await admin.from("profiles").select("role").eq("id", user.id).maybeSingle();
      if (me?.role !== "operador") return json({ error: "forbidden" }, 403);
      const frames: string[] = Array.isArray(payload.frames) ? payload.frames.slice(0, 1) : [];
      const content = frames.length
        ? [{ type: "text", text: PROMPT }, { type: "image_url", image_url: { url: `data:image/jpeg;base64,${frames[0]}` } }]
        : [{ type: "text", text: 'Responde SOLO este JSON: {"ok":true}' }];
      // Candidate Gemini models to compare against the chain on the same frame
      // (e.g. Flash vs Flash-Lite) before promoting one. Not noted in the breaker.
      const trials: Provider[] = (Array.isArray(payload.models) ? payload.models.slice(0, 4) : [])
        .filter((m: unknown) => typeof m === "string" && /^[\w.\-\/]+$/.test(m))
        .map((m: string) => ({
          name: `trial:${m}`, url: GEMINI_URL, model: m, secrets: GEMINI_KEYS, timeoutMs: 25000,
          maxTokens: GEMINI_OUTPUT_TOKENS,
          extra: typeof payload.reasoning_effort === "string" ? { reasoning_effort: payload.reasoning_effort } : undefined,
        }));
      const [attempts, groqModels, geminiModels] = await Promise.all([
        Promise.all([...PROVIDERS, ...trials].map((p) => callProvider(p, content, p.timeoutMs))),
        listModels("https://api.groq.com/openai/v1/models", keyOf({ secrets: GROQ_KEYS })),
        listModels("https://generativelanguage.googleapis.com/v1beta/openai/models", keyOf({ secrets: GEMINI_KEYS })),
      ]);
      attempts.forEach((a) => a.status !== 0 && !a.provider.startsWith("trial:") && note(admin, a));
      return json({
        providers: attempts.map((a) => ({
          provider: a.provider, model: a.model, ok: a.ok, status: a.status, ms: a.ms,
          error: a.error ?? null,
          finish_reason: a.body?.choices?.[0]?.finish_reason ?? null,
          completion_tokens: a.body?.usage?.completion_tokens ?? null,
          answer: String(a.body?.choices?.[0]?.message?.content ?? "").slice(0, 1200),
        })),
        available_models: { groq: groqModels, gemini: geminiModels },
      });
    }

    // ---- learn: read one reference image (tools/brand-learn), operators only ----
    // Same prompt and providers as a real scan, so the tool measures what the family's phone gets.
    // No liveness gate (a catalog photo is still by nature) and nothing is saved.
    if (payload.mode === "learn") {
      const { data: me } = await admin.from("profiles").select("role").eq("id", user.id).maybeSingle();
      if (me?.role !== "operador") return json({ error: "forbidden" }, 403);
      const frame = typeof payload.frame === "string" ? payload.frame : "";
      if (!frame) return json({ error: "no_frame" }, 400);
      const vision = await callVision(admin, [
        { type: "text", text: PROMPT },
        { type: "image_url", image_url: { url: `data:image/jpeg;base64,${frame}` } },
      ]);
      if (!("body" in vision)) return json({ busy: true, retry_after_ms: vision.retryAfterMs });
      let parsed: any = {};
      try { parsed = JSON.parse(vision.body?.choices?.[0]?.message?.content ?? "{}"); } catch { parsed = {}; }
      return json({
        provider: vision.provider, model: vision.model, pipeline: PIPELINE_VERSION,
        spoof: parsed?.spoof?.screen === true,
        items: (Array.isArray(parsed.items) ? parsed.items : []).map((it: any) => ({
          code: String(it.code ?? ""), quantity: Number(it.quantity) || 0,
          brand_raw: it.brand ?? null, texto: it.texto ?? "", etiqueta: it.etiqueta ?? "", tapa: it.tapa ?? "",
          brand: evidencedBrand(it.brand, it.texto),
        })),
      });
    }

    const sessionId = String(payload.session_id ?? "");
    if (!sessionId) return json({ error: "no_session" }, 400);

    const { data: materials } = await admin
      .from("materials").select("id,code,name,points_per_unit").eq("is_active", true);
    const byCode = new Map((materials ?? []).map((m: any) => [m.code, m]));
    // The estimate follows the published rule ("cada botella limpia = 10 pts"): the condition is the
    // rider's call at the door and settle_delivery applies its factor. Scaling by the AI's quality guess
    // showed "≈ 17 pts" for two bottles (2026-11-17). quality stays signed and stored for the dataset.
    const pointsFor = (code: string, qty: number, _quality: number) =>
      Math.round(qty * Number(byCode.get(code)?.points_per_unit ?? 0));

    // ---- commit: close the session from the signed frames -----------------
    if (payload.mode === "commit") {
      const observations: any[] = Array.isArray(payload.observations)
        ? payload.observations.slice(0, MAX_OBSERVATIONS)
        : [];
      // Best count per material across the walk-around. MAX, never SUM: the same
      // bottle shows up in many frames, and summing would pay for it once per frame.
      // That MAX is per group ("+ Agregar más"); the groups then add up, because the
      // friend's bottle scanned on its own must not vanish into max(3, 1) = 3.
      // Per material, and per (material, brand) alongside it: the points come from
      // the material count, the brand tally only rides along for the analytics.
      const best = new Map<string, Counted>();       // "group|CODE" -> best in that group
      const brandBest = new Map<string, number>();   // "group|CODE|Marca" -> units
      const sources = new Map<string, Provenance>();
      let legacyObservations = 0;
      let signedObservations = 0;
      for (const obs of observations) {
        const group = groupOf(obs?.group);
        if (group === null) return json({ error: "bad_observation" }, 400);
        const items: Counted[] = (Array.isArray(obs?.items) ? obs.items : []).map((i: any) => ({
          code: String(i.code),
          quantity: Math.max(1, Math.round(Number(i.quantity) || 1)),
          quality: clamp01(Number(i.quality)),
          brand: normBrand(i.brand),
        }));
        const observationError = await verifyObservation(serviceKey, user.id, sessionId, items, obs.token, group);
        if (observationError) return json({ error: observationError }, 400);
        if (items.length === 0) continue;
        const source = tokenParts(obs.token)!.source;
        if (source) {
          sources.set(JSON.stringify([source.provider, source.model, source.pipeline, source.resolved_model ?? null]), source);
          signedObservations++;
        } else legacyObservations++;
        // Within one frame the same material can arrive split by brand; the count
        // that matters per material is their sum in that frame.
        const perCode = new Map<string, Counted>();
        for (const it of items) {
          if (!byCode.has(it.code)) continue;
          const acc = perCode.get(it.code);
          if (!acc) perCode.set(it.code, { ...it });
          else {
            acc.quantity += it.quantity;
            acc.quality = Math.max(acc.quality, it.quality);
          }
          if (it.brand) {
            const key = `${group}|${it.code}|${it.brand}`;
            brandBest.set(key, Math.max(brandBest.get(key) ?? 0, it.quantity));
          }
        }
        for (const [code, it] of perCode) {
          const key = `${group}|${code}`;
          const prev = best.get(key);
          if (!prev) best.set(key, { ...it });
          else {
            prev.quantity = Math.max(prev.quantity, it.quantity);
            prev.quality = Math.max(prev.quality, it.quality);
          }
        }
      }
      if (best.size === 0) {
        return json({ rejected: "no_material", message: "No detecte materiales reciclables. Recorre tus residuos e intenta de nuevo." });
      }

      // Each photo, oldest first, per material; foldPhotos decides what each one adds.
      const photosByCode = new Map<string, (Photo & { group: number })[]>();
      for (const [key, it] of best) {
        const photos = photosByCode.get(it.code) ?? [];
        photos.push({ group: Number(key.split("|")[0]), quantity: it.quantity, quality: it.quality, brands: {} });
        photosByCode.set(it.code, photos);
      }
      for (const [key, units] of brandBest) {
        const [group, code, ...rest] = key.split("|");
        const brand = rest.join("|"); // a label could itself contain "|"
        const photo = photosByCode.get(code)?.find((p) => p.group === Number(group));
        if (photo) photo.brands[brand] = units;
      }
      const perMaterial = new Map<string, { quantity: number; quality: number; points: number; brands: Record<string, number> }>();
      for (const [code, photos] of photosByCode) {
        photos.sort((a, b) => a.group - b.group);
        const pile = foldPhotos(photos);
        perMaterial.set(code, {
          quantity: pile.quantity,
          brands: pile.brands,
          quality: Math.max(...photos.map((p) => p.quality)),
          // Each photo keeps the quality grade it was actually seen with.
          points: photos.reduce((sum, p, i) => sum + pointsFor(code, pile.added[i], p.quality), 0),
        });
      }

      const items = [...perMaterial].map(([code, it]) => {
        const m = byCode.get(code)!;
        return {
          material_id: m.id as string, code: m.code as string, name: m.name as string,
          quantity: it.quantity, quality_score: it.quality, points: it.points,
          brands: it.brands,
        };
      });
      const totalPoints = items.reduce((s, it) => s + it.points, 0);
      const totalValue = Math.round((totalPoints / 1000) * 100) / 100;

      const { data: gp } = await admin
        .from("generator_profiles").select("streak_multiplier").eq("profile_id", user.id).maybeSingle();

      // Two inserts could leave an orphan that a retry mistook for a saved scan.
      const { data: det, error: derr } = await admin.rpc("commit_detection", {
        p_generator_id: user.id,
        p_session_id: sessionId,
        p_model_version: legacyObservations ? "unknown" : sources.size === 1 ? [...sources.values()][0].model : "mixed",
        p_provenance: {
          version: 2,
          attribution: legacyObservations ? "unknown" : sources.size === 1 ? "single" : "mixed",
          sources: [...sources.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([, value]) => value),
          signed_observations: signedObservations,
          legacy_observations: legacyObservations,
        },
        p_total_points: totalPoints,
        p_total_value: totalValue,
        p_streak: Number(gp?.streak_multiplier ?? 1),
        p_items: items.map((it) => ({
          material_id: it.material_id, quantity: it.quantity, confidence: 1,
          quality_score: it.quality_score, points: it.points, brands: it.brands,
        })),
      });
      if (derr || !det) return json({ error: "persist_failed", detail: derr?.message ?? "No se pudo guardar el escaneo completo." }, 500);

      return json({
        detection_id: det.id,
        status: det.status,
        total_points: det.total_points,
        total_value: det.total_value,
        items: det.items.map((it: any) => ({
          code: it.code, name: it.name, quantity: it.quantity,
          confidence: it.confidence, quality: it.quality_score, points: it.points,
          material_id: it.material_id, box: null,
          brands: it.brands ?? {},   // {"Coca-Cola": 3}: shown to the family on the result
        })),
      });
    }

    // ---- preview: score one frame of the live scan ------------------------
    const frames: string[] = Array.isArray(payload.frames)
      ? payload.frames
      : (payload.frame ? [payload.frame] : []);
    if (frames.length === 0) return json({ error: "no_frame" }, 400);
    const group = groupOf(payload.group);
    if (group === null) return json({ error: "bad_group" }, 400);

    // 1) Liveness gate — reject a still capture (screenshot / printed photo held
    //    in front of a stationary phone). A real scan moves the phone around the item;
    //    the app remembers a sweep for a few seconds, so aiming still right after is fine.
    const motion = payload.motion ?? {};
    if (Number(motion.maxGyroDps ?? 0) < MIN_GYRO_DPS || Number(motion.samples ?? 0) < MIN_SAMPLES) {
      return json({ rejected: "liveness", message: "Mueve un poco el teléfono alrededor del residuo y vuelve a apuntar." });
    }

    // 2) Vision model — multi-item detection + bounding boxes + anti-screen flag.
    const content = [
      { type: "text", text: PROMPT },
      ...frames.slice(0, 2).map((f) => ({ type: "image_url", image_url: { url: `data:image/jpeg;base64,${f}` } })),
    ];
    const vision = await callVision(admin, content);
    if (!("body" in vision)) {
      // Every provider is rate-limited or down: a retryable hiccup, not a failed scan.
      // retry_after says when the first one is back, so the app doesn't burn the quota.
      return json({ rejected: "busy", retry_after_ms: vision.retryAfterMs, message: "El detector está ocupado. Sigue apuntando, reintentando…" });
    }
    let parsed: any = {};
    try { parsed = JSON.parse(vision.body?.choices?.[0]?.message?.content ?? "{}"); } catch { parsed = {}; }

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
      const brand = evidencedBrand(it.brand, it.texto);
      const box = Array.isArray(it.box_2d) ? normBox2d(it.box_2d) : normBox(it.box);
      // Una entrada por material y marca: tres Coca-Cola y una Inca Kola son el
      // mismo PET para los puntos, pero dos lineas distintas para la analitica.
      const key = `${m.code}|${brand ?? ""}`;
      const prev = merged.get(key);
      if (!prev) {
        merged.set(key, { material_id: m.id, code: m.code, name: m.name, quantity: qty, confidence: conf, quality: qual, brand, box });
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

    const counted: Counted[] = items.map((it) => ({ code: it.code, quantity: it.quantity, quality: it.quality, brand: it.brand ?? null }));
    return json({
      status: "preview",
      provider: vision.provider,
      group,
      token: await sign(serviceKey, user.id, sessionId, Date.now(), counted, group,
        { provider: vision.provider, model: vision.model, pipeline: PIPELINE_VERSION,
          ...(typeof vision.body.model === "string" && vision.body.model.length > 0 && vision.body.model.length <= 160
            ? { resolved_model: vision.body.model } : {}) }),
      total_points: items.reduce((s, it) => s + pointsFor(it.code, it.quantity, it.quality), 0),
      items: items.map((it) => ({
        code: it.code, name: it.name, quantity: it.quantity,
        confidence: it.confidence, quality: it.quality, points: pointsFor(it.code, it.quantity, it.quality),
        brand: it.brand ?? null, material_id: it.material_id, box: it.box,
      })),
    });
  } catch (e) {
    return json({ error: "exception", detail: String(e) }, 500);
  }
});
