# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

MAKI: a native Android app (Kotlin + Jetpack Compose, package `com.example.maki`) plus a Supabase
backend, for household recycling in Peru. Two actors share one codebase:

- **generador** ("Eco-Hero") — scans waste with the camera, earns points, redeems them for cash.
- **eco_rider** — collects the waste on a route, confirms it, and gets paid per pickup.

Built for a hackathon (team DeepCoders); the pitch script and slide assets live in `presentacion/`.
All user-facing copy is Spanish; code and KDoc are English.

**The product is called Ruma to users** — app label, UI copy, notifications, the
assistant's name, the panel's title. The code keeps the MAKI names on purpose: the
package `com.example.maki`, every `Maki*` identifier, `MAKITheme`, `Theme.MAKI`, the
`*@maki.test` accounts and the `maki-assistant` function slug. Don't rename those; a
new user-facing string says Ruma.

## Commands

Run from the repo root. PowerShell uses `.\gradlew.bat`; the Bash tool uses `./gradlew`.

```powershell
.\gradlew.bat :app:compileDebugKotlin        # fastest correctness check — the usual inner loop
.\gradlew.bat :app:assembleDebug             # build the APK
.\gradlew.bat :app:installDebug              # push to the connected device
.\gradlew.bat :app:testDebugUnitTest         # JVM unit tests
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.maki.RoutePlannerTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*untangles*"     # a single test method
.\gradlew.bat :app:connectedDebugAndroidTest # instrumented tests (needs a device/emulator)
.\gradlew.bat :app:lintDebug
```

Edge Functions (Supabase CLI, run against a project ref — see below):

```powershell
supabase functions deploy detect-material --project-ref mbbdrmicjobydprvbpsj
supabase functions deploy maki-assistant  --project-ref mbbdrmicjobydprvbpsj
```

The Gradle daemon toolchain is pinned to **JDK 21** (`gradle/gradle-daemon-jvm.properties`); the app
itself compiles to Java 11 bytecode, minSdk 24 / targetSdk 36.

## Two Supabase projects — staging first, always

| | project ref | holds |
|---|---|---|
| `MAKI_APP_STAGING` | `mbbdrmicjobydprvbpsj` | test data — what `data/SupabaseConfig.kt` points at |
| `MAKI_APP_PRODUCTION` | `kqwyygxpcsmworvmjqdv` | real data |

Every schema change, migration and function deploy is built and verified in STAGING, then replayed
to PRODUCTION. Never apply an unverified change to production. Pointing the app at production means
swapping `URL` + `ANON_KEY` in `SupabaseConfig` — the anon key is meant to ship; RLS is what protects
the data.

There is no `supabase/migrations/`: schema and catalog changes are dated, hand-run scripts in
`supabase/sql/`, executed in the project's SQL Editor. Each script's header records where it has
been applied (e.g. "APLICADO en STAGING … Falta repetirlo en producción") and any step that must run
on its own (`ALTER TYPE … ADD VALUE` can't share a transaction with its first use) — update that
header when you replay it.

## Architecture

### Client layers

- **`data/`** — a hand-rolled Supabase client over PostgREST + GoTrue; there is no supabase-kt SDK.
  - `MakiApi` — object holding one in-memory `Session`, OkHttp with long read timeouts (the Edge
    Functions hold the request while a model runs). Exposes `getRaw` / `post` / `patch` /
    `callFunction`, plus `humanize()` which turns Postgres/Edge errors into Spanish the user can act on.
  - `Dtos.kt` — every `@Serializable` row and RPC body.
  - `MakiRepository` — object where **every PostgREST path and RPC name lives**. Screens and
    ViewModels never build a URL; add the DTO here and the call here.
  - `MakiPrefs` — SharedPreferences: session tokens, `onboardingSeen`, the address captured during
    onboarding before an account exists, and a cached profile JSON for instant first paint.
- **`ui/screens/<role>/<Role>ViewModel.kt`** — one ViewModel per role graph. State is Compose
  `mutableStateOf` properties with `private set` (not StateFlow), one per section, each with an
  idempotent `loadX()`. `load { }` swallows errors into `error`; `runAction { }` drives `busy` plus a
  one-shot `message` the Root consumes as a snackbar. The exception is `ui/screens/market/`
  (Mercado Vivo: prices, demand, news) — one `MarketViewModel` both Roots instantiate rather than a
  copy per role. Shared helpers (`MakiTime`, `POINTS_PER_SOL`, `groupInt`) live at the bottom of
  `GeneratorViewModel.kt` and are imported from there.
- **Background work** is WorkManager in `data/`: `EcoTipsWorker` (daily eco notification, enqueued
  in `MainActivity`) and `RiderShiftWorker` (turns the rider available at their booked shift hour).
- **`navigation/`** — a two-tier NavHost. `AppRoot` runs splash → onboarding → login → role →
  register, then hosts `GeneratorRoot` or `RiderRoot`; each of those is its own NavHost + Scaffold
  and places the shared nav bar once. Route constants live in `AppRoutes` / `GenRoutes` /
  `RiderRoutes`; each role's `bottomBarRoutes` decides which screens keep the bar — camera, chat and
  the rider's navigation/confirm/completed screens are immersive and hide it. Tab switches go through
  `NavExt.switchTab` so tab state is preserved.
- Screens are composables taking state + lambdas. Each keeps a `private val SampleX` fallback so
  `@Preview` and an offline device still render something.

### Server (`supabase/functions/`, Deno)

- **`detect-material`** — the photo scan, server-authoritative end to end. The app takes one photo,
  shows "Tu material está siendo analizado por IA", and sends it as a `preview`, which scores it and
  returns an HMAC token over (session, group, code, quantity, quality, brand) — the brand is signed for
  analytics (`v_brand_zone`) but never pays points. Each photo is its own `group` ("Otra foto");
  `commit` replays the signed observations, takes the **MAX** per material inside a group and folds the
  groups with `foldPhotos` (mirrored by `pileTally` on the phone, so the counter says what gets booked): a photo
  with no brand seen before is new waste and adds up; one repeating a counted brand is the same pile framed
  again and adds only what grew, so 3 then those 3 plus one is 4, not 7. It is the only writer of `detections` /
  `detection_items`. Two bottles of one brand in separate photos read as one: the family corrects it with the
  per-brand steppers on the result, which restate `reported_quantity` and never pay. (It replaced a live scan of ~24 frames/min, which
  double-counted and capped the free vision tier at ~3 concurrent users.) Brands come from the
  researched catalog `detect-material/brands.ts` (54 Peruvian brands): `aliases` normalise any
  reading to the official name (a reading equally close to two brands stays as read), `look`
  goes into the prompt so a half-hidden label is recognised by its design, and `texts` / `fragments` (what is
  printed on each label) let `brandFromText` name the brand from the words the model copied in `texto`, only
  when exactly one brand claims them. `evidencedBrand` drops any brand the copied text doesn't back: told not to
  guess, the model still named covered labels. The prompt never lists bare example
  names, which primed the model into answering them. Anti-spoof is a gyroscope motion gate per frame
  (the app remembers a sweep for 8 s, `LivenessSensors`) plus a VLM screen/photo check.
  **The "always answer" protocol for the vision APIs** (free tiers are rate-limited and slow at
  random): several providers in preference order (Gemini lite models, then Groq qwen — qwen over-counted
  a 3-bottle frame as 5, and `commit` takes the MAX); every call
  caps `max_tokens` (Groq meters the *reservation* against 1000 output tokens/min); a circuit
  breaker in table `vision_provider_status` benches a provider for its cooldown after a 429 /
  timeout / 404; hedged requests start the next provider if the current one is silent for 3 s.
  `{"mode":"health"}` (operators only) pings every provider and lists the models each key can
  reach — use it to replace a retired model. Check provider health with
  `select * from vision_provider_status`.
- **`maki-assistant`** — chat agent grounded in Peru's container rules plus the caller's real context
  (points, recent detections, live `v_material_prices`). Gemini primary, Groq fallback — a chat turn
  can afford the latency.
- Model keys exist **only** as Edge Function secrets (`GROQ_API_KEY` or `grok_api_camera`,
  `GEMINI_API_KEY`); nothing vision-related ships in the APK. The `GROQ_API_KEY` still sitting in
  `local.properties` is a dead leftover from the old client-side call.

### Vision count measurement (live in STAGING since 2026-11-06, not in production)

`supabase/docs/vision-measurement.md` records the research, limitations and test commands.
`supabase/docs/HANDOFF-claude-vision-measurement.md` is the explicit continuation checklist:
the SQL and the detector are applied in STAGING; production still has neither.
Apply `supabase/sql/2026-11-03_vision_measurement.sql` in staging **before** deploying the
updated detector; its header tracks deployment status. V2 opaque tokens sign the winning
provider, requested/reported model and pipeline revision. `detections.provenance` is written
atomically by `commit_detection`; legacy observations remain unknown and mixed sources stay
separate. Private `ruma_analytics` snapshots preserve IA/declaration/rider counts at first
completion, before acopio overwrites the live quantity. This measures operational agreement,
not independent model accuracy. Bump `PIPELINE_VERSION` when prompt/catalog/counting rules change.

### Points are never client-authoritative

A scan writes a `pending` detection with the points it computed. When the **rider** completes the
pickup (`rpc/complete_pickup` — the RPC refuses the family) `settle_pickup` books them as
`points_pending` and pays the rider's fee; they become spendable only when the acopio weighs the
delivery (`settle_delivery`). The generator can correct `reported_quantity` — a declaration — and the
rider records `confirmed_quantity` at the door; neither pays anybody. Redemptions are a plain insert
into `redemptions`; DB triggers validate the balance and post the ledger. Keep new money/points paths
on this shape: the client asks, the database decides.

The rider opens that delivery from "Entregar en acopio" through **rpc/create_delivery**, which
validates ownership and `completed` status while holding the pickups locked; direct INSERT on
`deliveries` / `delivery_pickups` is revoked, and `settle_delivery` now refuses a batch with no
pickups linked (it used to book the whole gross as platform margin).

### Pickup lifecycle and live tracking

`requested → assigned → en_route → arriving → collecting → completed` (plus `cancelled` / `failed`).
Dispatch is Uber-style: `rpc/assign_nearest_rider` hands the pickup to the closest available rider
within 20 km; a null result leaves it on the open board, where any rider can claim it (and see the
family and address). `trg_pickup_guard` decides which direct PATCHes are legal (family: cancel only;
rider: forward only — completion via `rpc/complete_pickup`, hand-back via `rpc/release_pickup`) and
`trg_pickup_notify` writes a notification to both sides on every step. Tracking is polling, not
realtime — the rider heartbeats fresh fixes into `rider_profiles` every `LOCATION_HEARTBEAT_MS`
(`RiderRoot`) while the generator re-reads the pickup every `TRACKING_POLL_MS` (`GeneratorRoot`).
Street paths and the fastest stop order come from `data/StreetRouter` (OSRM on
routing.openstreetmap.de, no key); `RoutePlanner` (nearest neighbour + 2-opt, crow-flies) is the
offline fallback. Test accounts for every actor: see the seed in STAGING (`*@maki.test`, `Maki12345!`).

Routing investigation and implementation phases: `docs/ecorider-routing-research.md`.
Public endpoint probes passed on 2026-11-03, including the exact open Trip parameters.
The implementation status and verification checklist are in `docs/ecorider-navigation-implementation.md`.
Navigation now retries stationary failures, invalidates obsolete responses, includes
rider→active-stop, and supports in-app maneuvers/foreground voice on Android and PWA.
GPS freshness/accuracy gates guidance; Maps remains optional on Android.
The 1.3 ETA factor is a static estimate, not live traffic.

## PWA (`pwa/`)

The web version for people on iPhone, for **both** actors (family and rider), on the same STAGING backend.
React + Vite, `supabase-js`, Leaflet; no state library. From `pwa/`: `npm run dev`, `npm run build:staging`
(STAGING database, marked on every screen) and `npm run build` (production; refuses to build without
`.env.production.local`, so it can never fall back to staging). `node scripts/check-fold.ts` checks the photo tally.
Deployed on Vercel (account mrproductions, Hobby) as prebuilt files, so no source or `.env*` is uploaded: copy `dist` to a folder
outside the repo named after the project and run `vercel deploy . --prod --yes` there. `ruma-staging.vercel.app` is staging; `ruma-recicla.vercel.app` is production, still a "Muy pronto" page.
Hobby is non-commercial only: move to Pro or another host before operating with real money. Netlify free pins a badge over the bottom bars, which is why it was left.

- `src/lib/api.ts` is the twin of `MakiRepository`: every table, RPC and function name lives there, and the
  same rule holds: the client asks, the database decides. A change to a call in one usually belongs in the other.
- `src/lib/fold.ts` mirrors `foldPhotos` in `detect-material`, as `pileTally` does on Android.
- The role swaps CSS variables only (`[data-role="rider"]` in `styles.css`), so screens are shared when they can be.
- Designs live in the Stitch project "Ruma PWA"; the logo in `design/ruma/logo/`.
- iOS cannot run anything with the PWA closed: no background location, and a booked shift starts on the first
  open after its hour. The motion permission must be requested from a tap.

## Conventions

- **Design tokens only.** `ui/theme/` holds the whole system and a screen that writes its own
  `Color(0x…)` or `fontSize` is a bug: `MakiColors` (roles, gradient stops, tints, the muted
  `On*Soft` text for each gradient), `MakiType` — the scale **by the job** each style does
  (`metricHero`, `cardTitle`, `rowTitle`, `body`, `overline`…), and `MakiSpace` / `MakiRadius` /
  `MakiElevation` / `MakiGradient` in `MakiDesign.kt`. `MakiFont` is **DM Sans** and `MakiDisplayFont` **Bricolage Grotesque** (the PWA's fonts), shipped as
  static instances in `res/font` (not the variable file: Compose only applies weight variations
  from API 26 and minSdk is 24, so on Android 7 every weight would collapse to Regular).
  Don't reach for `MaterialTheme.colorScheme` or `MaterialTheme.typography`. Single light theme —
  dark mode is deliberately off.
- **Five sizes, two weights.** `MakiType` resolves 15 named roles onto 12/15/20/26/40 sp and
  Medium/ExtraBold only. It used to spread over eleven sizes and five weights, which is why no
  screen had a hierarchy: with eleven sizes everything competes. Add a role, never a size.
- **Colour is 10%, not half the screen.** Neutrals carry the UI; the accent is reserved for the
  one gradient hero per screen, status chips, the active tab and the primary button. Icon badges
  in `MetricTile`/`NavRow` are deliberately neutral: two hundred brand-tinted squares is the
  single loudest tell that a screen came from a template.
- **Surfaces are planes of light.** `Modifier.makiScreen()` paints `MakiGradient.screen` (a 16%
  luminance fall, top to bottom) and `makiCard()` is translucent white plus a hairline, so a card
  picks up the colour under it. No blur: `RenderEffect` needs API 31 and minSdk is 24.
  Measured on device: card 1.22:1 against its background, all three text levels pass WCAG AA.
- **Composite modifiers over copied chains.** `Modifier.makiCard()`, `.makiHero(brush, glow=)`,
  `.makiOutlined(color)`, plus `HeroCard`, `MetricTile`, `NavRow`, `StatusChip` and
  `ProgressTrack` in `MakiUi.kt`. One hero per screen: two heroes is none.
- **One reusable nav component.** `MakiNavBar(items, currentRoute, accent, onSelect)` in
  `MakiUi.kt`. Never write a per-screen or per-flow nav bar, and no hamburger on a home screen —
  account actions belong in Perfil.
- **Shared states, never a blank screen.** `MakiLoading`, `MakiEmpty`, `MakiError` (with retry),
  `SoftCard`, `DetailHeader`, `SectionLabel` in `MakiScaffold.kt`; `PrimaryButton`,
  `SecondaryButton`, `IconBadge`, `MakiInputField`, `Modifier.makiShadow` in `MakiUi.kt`. Lists are
  `LazyColumn` and always resolve to one of loading / empty / error.
- **No `java.time`** (minSdk 24). Date formatting goes through `MakiTime` (SimpleDateFormat/Calendar)
  at the bottom of `GeneratorViewModel.kt`.
- Both ViewModels call `ensureSignedIn()`, which falls back to a hardcoded test account
  (`generador@maki.test` / `rider@maki.test`) — a demo shortcut, not something to build on.
- Maps are osmdroid (no API key) through the single `ui/components/OsmMap.kt`.
- `TrainingDataStore` files camera captures under `filesDir/dataset/<label>/` for a future YOLO
  training set; it is data collection only, no on-device inference exists yet.

## Local skills

`.agents/skills/` (pinned in `skills-lock.json`) carries `mobile-android-design` — including the
navigation guide this repo's nav architecture follows — `android-testing`, and
`supabase-postgres-best-practices`. Read the relevant one before reworking UI, tests, or schema.
