import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import { runInNewContext } from "node:vm";
import { webcrypto } from "node:crypto";
import test from "node:test";

// Exercise the actual handler offline; neither Supabase nor vision gets a request.
// The brand catalog is a sibling module; inline it ahead of the handler.
const brands = readFileSync(new URL("./brands.ts", import.meta.url), "utf8").replace(/^export /gm, "");
const source = readFileSync(new URL("./index.ts", import.meta.url), "utf8")
  .replace(/^import .*;\r?\n/gm, "");
const js = stripTypeScriptTypes(brands + "\n" + source + `
globalThis.helpers = { normBox2d, sign, canonical, verifyObservation, constantTimeEqual, OBSERVATION_TTL_MS, unusableAnswer, normBrand, PROMPT, foldPhotos, brandFromText, evidencedBrand };
`);

function harness(visionFetch) {
  const calls = [];
  const state = { error: null };
  const stored = {
    id: "saved-detection", status: "scheduled", total_points: 17, total_value: 0.02,
    items: [{ material_id: "material", code: "PET", name: "Saved material", quantity: 2,
      confidence: 0.9, quality_score: 0.7, points: 17 }],
  };
  const admin = {
    from(table) {
      if (table === "vision_provider_status") return {
        select: async () => ({ data: [] }), upsert: async () => ({}),
      };
      assert.ok(["materials", "generator_profiles"].includes(table));
      return { select() { return { eq() {
        if (table === "materials") return { data: [
          { id: "material", code: "PET", name: "Current material", points_per_unit: 400 },
          { id: "alu", code: "ALU", name: "Aluminium", points_per_unit: 301 },
        ] };
        return { maybeSingle: async () => ({ data: { streak_multiplier: 1 } }) };
      } }; } };
    },
    async rpc(name, args) {
      calls.push({ name, args });
      return { data: state.error ? null : stored, error: state.error };
    },
  };
  const context = {
    TextEncoder, Request, Response, crypto: webcrypto, btoa, atob, console,
    fetch: visionFetch, AbortSignal, setTimeout, clearTimeout,
    Deno: { env: { get: (name) => name === "SUPABASE_SERVICE_ROLE_KEY" ? "secret" : "dummy" },
      serve: (handler) => { context.handler = handler; } },
    createClient: (_url, key) => key === "secret" ? admin : {
      auth: { getUser: async () => ({ data: { user: { id: "user-a" } } }) },
    },
  };
  runInNewContext(js, context);
  return { ...context.helpers, calls, state, stored, handler: context.handler };
}

const items = [{ code: "PET", quantity: 2, quality: 0.7, brand: null }];

test("signature binds user, session, issue time and items", async () => {
  const h = harness();
  const now = Date.now();
  const token = await h.sign("secret", "user-a", "session", now, items);
  assert.match(token, /^\d+\.[A-Za-z0-9+/]{43}=$/);
  assert.equal(await h.verifyObservation("secret", "user-a", "session", items, token), null);
  for (const [user, session, observed, signed] of [
    ["user-b", "session", items, token],
    ["user-a", "other", items, token],
    ["user-a", "session", [{ ...items[0], quantity: 3 }], token],
    ["user-a", "session", items, token.replace(String(now), String(now + 1))],
  ]) assert.equal(await h.verifyObservation("secret", user, session, observed, signed), "bad_observation");
});

test("expired and malformed tokens have distinct errors", async () => {
  const h = harness();
  const old = await h.sign("secret", "user-a", "session", Date.now() - h.OBSERVATION_TTL_MS - 1, items);
  assert.equal(await h.verifyObservation("secret", "user-a", "session", items, old), "stale_observation");
  for (const token of [null, "", "NaN.mac", "Infinity.mac", "1.2.mac", old.split(".")[1], "9".repeat(400) + "." + old.split(".")[1]]) {
    assert.equal(await h.verifyObservation("secret", "user-a", "session", items, token), "bad_observation");
  }
  assert.equal(h.constantTimeEqual(new Uint8Array([1, 2]), new Uint8Array([1, 2])), true);
  assert.equal(h.constantTimeEqual(new Uint8Array([1, 2]), new Uint8Array([1, 3])), false);
  assert.equal(h.constantTimeEqual(new Uint8Array([1]), new Uint8Array([1, 0])), false);
});

async function commit(h, token) {
  return h.handler(new Request("https://offline.invalid", { method: "POST",
    body: JSON.stringify({ mode: "commit", session_id: "session", observations: [{ items, token }] }),
  }));
}

test("commit and retry return persisted values through one RPC per request", async () => {
  const h = harness();
  const token = await h.sign("secret", "user-a", "session", Date.now(), items);
  const first = await (await commit(h, token)).json();
  const retry = await (await commit(h, token)).json();
  assert.deepEqual(first, retry);
  assert.equal(first.status, "scheduled");
  assert.equal(first.total_points, 17);
  assert.equal(first.items[0].name, "Saved material");
  assert.equal(first.items[0].confidence, 0.9);
  assert.equal(first.items[0].quality, 0.7);
  assert.equal(h.calls.length, 2);
  assert.equal(h.calls[0].name, "commit_detection");
  assert.equal(h.calls[0].args.p_generator_id, "user-a");
  assert.equal(h.calls[0].args.p_session_id, "session");
  assert.equal(h.calls[0].args.p_model_version, "unknown");
  assert.equal(h.calls[0].args.p_provenance.attribution, "unknown");
});

const sourceFor = (model) => ({ provider: "fallback", model, pipeline: "photo-v1" });
async function sourced(h, model, group = 0) {
  return { items, group, token: await h.sign("secret", "user-a", "session", Date.now(), items, group, sourceFor(model)) };
}

test("preview fallback round-trips through an unchanged client and retains the reported model version", async () => {
  const requested = [];
  const h = harness(async (_url, init) => {
    requested.push(JSON.parse(init.body).model);
    if (requested.length === 1) return new Response("simulated rate limit", { status: 429 });
    return Response.json({ model: "resolved-version-123", choices: [{ finish_reason: "stop", message: {
      content: JSON.stringify({ items: [{ code: "PET", quantity: 2, quality: 0.7, confidence: 0.9 }], spoof: {} }),
    } }] });
  });
  const response = await send(h, { mode: "preview", frame: "offline", motion: { maxGyroDps: 100, samples: 100 } });
  assert.equal(response.status, 200);
  const preview = await response.json();
  assert.equal(preview.status, "preview");
  assert.equal(requested.length, 2);
  const args = await persisted(h, [{ items: preview.items, token: preview.token, group: 0 }]);
  assert.equal(args.p_model_version, requested[1]);
  assert.equal(args.p_provenance.sources[0].provider, preview.provider);
  assert.equal(args.p_provenance.sources[0].resolved_model, "resolved-version-123");
});

test("v2 persists the signed fallback model, ignoring unsigned client claims", async () => {
  const h = harness();
  const obs = await sourced(h, "actual-fallback-model");
  obs.provider = "forged-primary";
  obs.model = "forged-primary";
  const args = await persisted(h, [obs, obs]);
  assert.equal(args.p_model_version, "actual-fallback-model");
  assert.deepEqual(args.p_provenance, { version: 2, attribution: "single",
    sources: [sourceFor("actual-fallback-model")], signed_observations: 2, legacy_observations: 0 });
});

test("mixed models are a separate cohort; any legacy observation makes attribution unknown", async () => {
  const h = harness();
  const args = await persisted(h, [await sourced(h, "z", 0), await sourced(h, "a", 1)]);
  assert.equal(args.p_model_version, "mixed");
  assert.equal(args.p_provenance.attribution, "mixed");
  assert.deepEqual(args.p_provenance.sources.map((s) => s.model), ["a", "z"]);
  const legacy = harness();
  const unknown = await persisted(legacy, [await sourced(legacy, "a"), await observation(legacy, items, 1)]);
  assert.equal(unknown.p_model_version, "unknown");
  assert.equal(unknown.p_provenance.legacy_observations, 1);
});

test("v2 metadata, downgrade, scope, future timestamp and malformed tokens cannot reach persistence", async () => {
  const h = harness();
  const obs = await sourced(h, "actual-model");
  const parts = obs.token.split(".");
  const forged = [...parts];
  forged[2] = btoa(JSON.stringify(sourceFor("forged-model")));
  const future = await h.sign("secret", "user-a", "session", Date.now() + 120000, items, 0, sourceFor("a"));
  for (const token of [forged.join("."), `${parts[1]}.${parts[3]}`, future, "v2.123.bad=.bad", "x".repeat(2049)]) {
    assert.equal((await commit(h, token)).status, 400);
  }
  const wrongGroup = await send(h, { mode: "commit", observations: [{ ...obs, group: 1 }] });
  assert.equal(wrongGroup.status, 400);
  assert.equal(h.calls.length, 0);
});

test("invalid observation never reaches persistence; RPC failure uses one error path", async () => {
  const h = harness();
  const foreign = await h.sign("secret", "user-b", "session", Date.now(), items);
  const bad = await commit(h, foreign);
  assert.equal(bad.status, 400);
  assert.deepEqual(await bad.json(), { error: "bad_observation" });
  assert.equal(h.calls.length, 0);
  h.state.error = { message: "Item validation failed" };
  const valid = await h.sign("secret", "user-a", "session", Date.now(), items);
  const failed = await commit(h, valid);
  assert.equal(failed.status, 500);
  assert.deepEqual(await failed.json(), { error: "persist_failed", detail: "Item validation failed" });
});

const pet = (quantity, quality = 1, brand = null) => ({ code: "PET", quantity, quality, brand });
const alu = (quantity, quality = 1) => ({ code: "ALU", quantity, quality, brand: null });
async function observation(h, rows, group = 0) {
  return { items: rows, group, token: await h.sign("secret", "user-a", "session", Date.now(), rows, group) };
}
async function send(h, payload) {
  return h.handler(new Request("https://offline.invalid", { method: "POST",
    body: JSON.stringify({ session_id: "session", ...payload }),
  }));
}
async function persisted(h, observations) {
  const response = await send(h, { mode: "commit", observations });
  assert.equal(response.status, 200, JSON.stringify(await response.json()));
  assert.equal(h.calls.length, 1);
  assert.equal(h.calls[0].name, "commit_detection");
  // The fake RPC always returns `stored`; only its arguments prove aggregation.
  return JSON.parse(JSON.stringify(h.calls[0].args));
}
function expectItems(args, expected, points) {
  assert.deepEqual(args.p_items, expected.map(([id, quantity, quality, itemPoints, brands = {}]) => ({
    material_id: id, quantity, confidence: 1, quality_score: quality, points: itemPoints, brands,
  })));
  assert.equal(args.p_total_points, points);
}

test("A: new friend's bottle in the same group keeps MAX 3 (anti double count)", async () => {
  const h = harness();
  // Without '+ Agregar más' the server cannot distinguish a new bottle from a repeat.
  const args = await persisted(h, [await observation(h, [pet(3)]), await observation(h, [pet(1)])]);
  expectItems(args, [["material", 3, 1, 1200]], 1200);
});

test("B: '+ Agregar más' adds the friend's group: 3 + 1 = 4", async () => {
  const h = harness();
  const args = await persisted(h, [await observation(h, [pet(3)]), await observation(h, [pet(1)], 1)]);
  expectItems(args, [["material", 4, 1, 1600]], 1600);
});

test("C: ten frames of three bottles in one group count 3, not 30", async () => {
  const h = harness();
  const frames = await Promise.all(Array.from({ length: 10 }, () => observation(h, [pet(3)])));
  expectItems(await persisted(h, frames), [["material", 3, 1, 1200]], 1200);
});

test("D: split PET rows sum within a frame; a later photo repeating a brand adds only what grew", async () => {
  const h = harness();
  const rows = [pet(2, 1, "Coca-Cola"), pet(1, 1, "Inca Kola")];
  const args = await persisted(h, [await observation(h, rows), await observation(h, rows),
    await observation(h, [pet(1, 1, "Coca-Cola")], 1)]);
  expectItems(args, [["material", 3, 1, 1200, { "Coca-Cola": 2, "Inca Kola": 1 }]], 1200);
});

for (const variant of ["0 to 1", "1 to absent"]) {
  test(`E: fraud ${variant} rejects the entire commit before RPC`, async () => {
    const h = harness();
    const forged = await observation(h, [pet(1)], variant === "0 to 1" ? 0 : 1);
    if (variant === "0 to 1") forged.group = 1;
    else delete forged.group;
    const response = await send(h, { mode: "commit", observations: [await observation(h, [pet(3)]), forged] });
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), { error: "bad_observation" });
    assert.equal(h.calls.length, 0);
  });
}

for (const group of [10, -1, 1.5, "x"]) {
  test(`F: invalid group ${JSON.stringify(group)} rejects commit and preview offline`, async () => {
    const h = harness();
    const obs = await observation(h, [pet(1)]);
    obs.group = group;
    for (const motion of [undefined, { maxGyroDps: 30, samples: 40 }]) {
      const preview = await send(h, { mode: "preview", group, frames: ["offline-placeholder"], motion });
      assert.equal(preview.status, 400);
      assert.deepEqual(await preview.json(), { error: "bad_group" });
    }
    const response = await send(h, { mode: "commit", observations: [obs] });
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), { error: "bad_observation" });
    assert.equal(h.calls.length, 0);
  });
}

test("G: old app omits group and uses the exact legacy HMAC without |g0", async () => {
  const h = harness();
  const rows = [pet(3)];
  const iat = Date.now();
  const legacy = `user-a|session|${iat}|PET:3:1.000:`;
  assert.equal(h.canonical("user-a", "session", iat, rows, 0), legacy);
  // Independent legacy signing prevents sign/verify changing together from hiding a regression.
  const key = await webcrypto.subtle.importKey("raw", new TextEncoder().encode("secret"),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const mac = await webcrypto.subtle.sign("HMAC", key, new TextEncoder().encode(legacy));
  const token = `${iat}.${Buffer.from(mac).toString("base64")}`;
  expectItems(await persisted(h, [{ items: rows, token }]), [["material", 3, 1, 1200]], 1200);
});

test("H: points use each group's own quality: 1200 + 200 = 1400", async () => {
  const h = harness();
  expectItems(await persisted(h, [await observation(h, [pet(3, 1)]), await observation(h, [pet(1, 0)], 1)]),
    [["material", 4, 1, 1400]], 1400);
});

test("I: observation 120 counts; 121 and later are ignored including invalid tokens", async () => {
  const h = harness();
  const first = await observation(h, [pet(1)]);
  const frames = Array.from({ length: 119 }, () => first);
  frames.push(await observation(h, [pet(3)]));
  frames.push(await observation(h, [pet(99)], 1));
  frames.push({ items: [pet(999)], token: "invalid", group: 10 });
  expectItems(await persisted(h, frames), [["material", 3, 1, 1200]], 1200);
});

test("J: an empty group adds neither material nor points; all-empty returns no_material", async () => {
  const h = harness();
  expectItems(await persisted(h, [await observation(h, [], 1), await observation(h, [pet(3)]),
    await observation(h, [], 1)]), [["material", 3, 1, 1200]], 1200);
  const empty = harness();
  const response = await send(empty, { mode: "commit", observations: [await observation(empty, [], 9)] });
  assert.equal(response.status, 200);
  assert.equal((await response.json()).rejected, "no_material");
  assert.equal(empty.calls.length, 0);
});

test("K: mixed PET/ALU take independent maxima and sum groups, rounding points per group", async () => {
  const h = harness();
  const args = await persisted(h, [
    await observation(h, [pet(3), alu(1, 0)]),
    await observation(h, [pet(1), alu(3, 0)]),
    await observation(h, [pet(1, 0), alu(1, 0)], 9),
  ]);
  // ALU: round(3 * 301 * .5) + round(1 * 301 * .5) = 452 + 151.
  expectItems(args, [["material", 4, 1, 1400], ["alu", 4, 0, 603]], 2003);
});

test("a cut or non-JSON answer never wins the provider race", () => {
  const h = harness();
  const answer = (content, finish_reason = "stop") => ({ choices: [{ message: { content }, finish_reason }] });
  // What the phone hit on 2026-11-01: four brands, JSON cut at max_tokens.
  assert.equal(h.unusableAnswer(answer('{"items":[{"code":"PET","quantity":1,"brand":"Concordia"},{"co', "length")), "truncated");
  assert.equal(h.unusableAnswer(answer("Veo cuatro botellas")), "invalid_json");
  assert.equal(h.unusableAnswer(answer("42")), "invalid_json");
  assert.equal(h.unusableAnswer(answer('{"items":[],"spoof":{"screen":false}}')), null);
});

test("brand readings land on the catalog's official name", () => {
  const h = harness();
  // What Gemini wrote for the same Agua Fiel bottle across the phone tests.
  // The label itself reads "Aguafiel" (Hielosnorte, Trujillo): that is the official name.
  for (const reading of ["Agua Fiel", "AGUA FIEL", "Acuafiel", "agua-fiel", "Aguafie1"]) assert.equal(h.normBrand(reading), "Aguafiel");
  assert.equal(h.normBrand("coca cola"), "Coca-Cola");
  // Official names are fixed points, or the signature would change between preview and commit.
  for (const name of ["Aguafiel", "Coca-Cola", "Loa", "Concordia", "Gatorade"]) assert.equal(h.normBrand(name), name);
  // Short fragments get no fuzzy slack ("Ciel" is not Cielo), unknown brands are kept as read.
  assert.equal(h.normBrand("Ciel"), "Ciel");
  assert.equal(h.normBrand("MARCA NUEVA"), "Marca Nueva");
  // Halfway between two real brands is no brand to book: kept as read, never a guess.
  assert.equal(h.normBrand("Aguaciel"), "Aguaciel");
  assert.equal(h.normBrand("Coca Kola"), "Coca-Cola");
  assert.equal(h.normBrand("sin marca"), null);
  // The model is told what each label looks like, not just a list of names.
  assert.match(h.PROMPT, /CATALOGO/);
  assert.match(h.PROMPT, /- Aguafiel: .*TAPA ROJA/);
  // Low-confidence entries normalise names but never teach the model a look.
  assert.doesNotMatch(h.PROMPT, /- Backus Ice: /);
});

test("a second photo adds only what is new", () => {
  const { foldPhotos } = harness();
  const p = (quantity, brands = {}) => ({ quantity, quality: 1, brands });
  const three = { Concordia: 1, Loa: 1, Aguafiel: 1 };
  const q = (photos) => foldPhotos(photos).quantity;
  // Only the new bottle in the second photo.
  assert.equal(q([p(3, three), p(1, { Gatorade: 1 })]), 4);
  // Phone test: the same three framed again with the Gatorade — 4, not 7.
  assert.equal(q([p(3, three), p(4, { ...three, Gatorade: 1 })]), 4);
  // The same photo twice adds nothing.
  assert.equal(q([p(3, three), p(3, three)]), 3);
  // Part of the pile plus a new one.
  assert.equal(q([p(3, three), p(3, { Loa: 1, Aguafiel: 1, Gatorade: 1 })]), 4);
  // A brand that grew from one photo to the next.
  assert.equal(q([p(1, { Loa: 1 }), p(3, { Loa: 3 })]), 3);
  // A label unreadable before, legible now: the same bottle, still 4.
  assert.equal(q([p(4, { Concordia: 1, Gatorade: 1 }), p(4, { Concordia: 1, Gatorade: 1, Loa: 1 })]), 4);
  // Hidden label stays hidden, a new brand joins the pile.
  assert.equal(q([p(3, { Concordia: 1, Aguafiel: 1 }), p(4, { Concordia: 1, Aguafiel: 1, Gatorade: 1 })]), 4);
  // One unreadable already counted; next photo: same pile, that one unreadable and a new one.
  assert.equal(q([p(2, { Loa: 1 }), p(3, { Loa: 1, Gatorade: 1 })]), 3);
  // A label legible before, covered now: the same two bottles.
  assert.equal(q([p(2, { Loa: 1, Aguafiel: 1 }), p(2, { Loa: 1 })]), 2);
  // No brand in common and nothing readable: separate waste, they add up.
  assert.equal(q([p(3), p(2)]), 5);
  // Never above the plain sum: this is what points are paid on.
  const fold = foldPhotos([p(2, { Loa: 5 }), p(2, { Cielo: 5 })]);
  assert.ok(fold.quantity <= 4);
  assert.equal(foldPhotos([p(3, three), p(4, { ...three, Gatorade: 1 })]).added.join(), "3,1");
});

test("label text decides the brand only when one brand claims it", () => {
  const { brandFromText, normBrand } = harness();
  assert.equal(brandFromText("Fiel a tu Salud"), "Aguafiel");
  assert.equal(brandFromText("FIEL A TU SALUD 625 ml"), "Aguafiel");
  // Pieces of the logo count only for text copied off the label.
  assert.equal(brandFromText("guafie"), null);
  assert.equal(brandFromText("guafie", true), "Aguafiel");
  assert.equal(brandFromText("GATOR", true), "Gatorade");
  // Words on every bottle, nothing to go on, or two brands at once.
  for (const words of ["Agua", "Sin Gas", "Agua Mineral Natural", "", "   ", null]) assert.equal(brandFromText(words, true), null);
  assert.equal(brandFromText("Concordia Gatorade", true), null);
  // "Loa" as a word, never inside another.
  assert.equal(brandFromText("Loa"), "Loa");
  assert.equal(brandFromText("Paloma"), null);
  // A reading the names miss still lands on the catalog; the near-miss guards hold.
  assert.equal(normBrand("uafiel"), "Aguafiel");
  assert.equal(normBrand("Ciel"), "Ciel");
  assert.equal(normBrand("Aguaciel"), "Aguaciel");
});

test("the catalog never lets one text name two brands", () => {
  const brands = readFileSync(new URL("./brands.ts", import.meta.url), "utf8");
  const key = (s) => s.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]+/g, " ").trim();
  const list = JSON.parse(brands.slice(brands.indexOf("= [") + 2, brands.lastIndexOf("]") + 1));
  const owners = new Map();
  for (const b of list) for (const x of [...(b.texts ?? []), ...(b.fragments ?? [])]) {
    const k = key(x);
    assert.ok(!owners.has(k) || owners.get(k) === b.name, `${x}: ${owners.get(k)} and ${b.name}`);
    owners.set(k, b.name);
  }
});

test("a brand stands only on the text read off its label", () => {
  const { evidencedBrand } = harness();
  // Phone photo: label covered down to one letter, the model still said Cielo.
  assert.equal(evidencedBrand("Cielo", "a"), null);
  assert.equal(evidencedBrand("Cielo", ""), null);
  assert.equal(evidencedBrand("Cielo", undefined), null);
  assert.equal(evidencedBrand("San Luis", "Sin Gas"), null);
  // Read, it stands; and the text wins over the claim.
  assert.equal(evidencedBrand("Aguafiel", "Aguafiel"), "Aguafiel");
  assert.equal(evidencedBrand("Aguafiel", "Agua Fiel Sin Gas"), "Aguafiel");
  assert.equal(evidencedBrand(null, "Fiel a tu Salud"), "Aguafiel");
  assert.equal(evidencedBrand("Cielo", "guafiel"), "Aguafiel");
  assert.equal(evidencedBrand("Gloria", "GLORIA"), "Gloria");
  // Vocé: phone photo with a clear label, read with or without the accent.
  assert.equal(evidencedBrand(null, "AGUA VOCE Sin Gas"), "Vocé");
  assert.equal(evidencedBrand("Vocé", "Agua Vocé 650 ml"), "Vocé");
  assert.equal(evidencedBrand("Vocé", "650 ml"), null);
  // Off the catalog: only when its name is what was read.
  assert.equal(evidencedBrand("Marca Nueva", "MARCA NUEVA 500 ml"), "Marca Nueva");
  assert.equal(evidencedBrand("Marca Nueva", "500 ml"), null);
});

test("box_2d is read in Gemini's order, so a lying bottle is not transposed", () => {
  const { normBox2d } = harness();
  // Real answer for a bottle lying across the photo: rows 371..632, columns 0..997.
  assert.deepEqual([...normBox2d([371, 0, 632, 997])], [0, 0.371, 0.997, 0.632]);
  assert.equal(normBox2d([1, 2]), null);
});
