// The PWA's twin of data/MakiRepository.kt: every table, RPC and function name lives here.
// The client asks and the database decides: triggers and RPCs own points and money.
import { createClient } from "@supabase/supabase-js";
import { SUPABASE_ANON_KEY, SUPABASE_URL } from "./config";

export const sb = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

/* eslint-disable @typescript-eslint/no-explicit-any */
export type Row = Record<string, any>;

let uid: string | null = null;
sb.auth.onAuthStateChange((_e, s) => { uid = s?.user.id ?? null; });
export const myId = () => uid;

/** Spanish messages for what a person can act on; the rest keeps the server's words. */
export function humanize(e: unknown): string {
  const msg = String((e as Error)?.message ?? e ?? "");
  if (/invalid login/i.test(msg)) return "Correo o contraseña incorrectos.";
  if (/email not confirmed/i.test(msg)) return "Confirma tu correo antes de entrar.";
  if (/already registered/i.test(msg)) return "Ese correo ya tiene una cuenta. Inicia sesión.";
  if (/password should be/i.test(msg)) return "La contraseña debe tener al menos 6 caracteres.";
  if (/insufficient points/i.test(msg)) return "Saldo insuficiente para este canje.";
  if (/failed to fetch|networkerror|load failed/i.test(msg)) return "Sin conexión. Revisa tu internet e intenta otra vez.";
  return msg || "Algo salió mal. Intenta otra vez.";
}

async function q<T = Row[]>(p: PromiseLike<{ data: unknown; error: unknown }>): Promise<T> {
  const { data, error } = await p;
  if (error) throw error;
  return data as T;
}

async function need(): Promise<string> {
  if (uid) return uid;
  const { data } = await sb.auth.getSession();
  uid = data.session?.user.id ?? null;
  if (!uid) throw new Error("Tu sesión expiró. Vuelve a entrar.");
  return uid;
}

/* ---------- Session ---------- */

const PENDING = "ruma.pendingAddress";
export type PendingAddress = { line: string; lat: number | null; lng: number | null };
export const pendingAddress = (): PendingAddress | null => {
  try { return JSON.parse(localStorage.getItem(PENDING) ?? "null"); } catch { return null; }
};
export const setPendingAddress = (a: PendingAddress | null) => {
  try { if (a) localStorage.setItem(PENDING, JSON.stringify(a)); else localStorage.removeItem(PENDING); } catch { /* private mode */ }
};

/** Sign-up may need email confirmation: the address then lands on the first real login. */
async function claimPendingAddress() {
  const a = pendingAddress();
  if (!a?.line) return;
  if (await loadPrimaryAddress()) { setPendingAddress(null); return; }
  await savePrimaryAddress(null, a.line, a.lat, a.lng);
  setPendingAddress(null);
}

export async function signIn(email: string, password: string) {
  const { data, error } = await sb.auth.signInWithPassword({ email: email.trim(), password });
  if (error) throw error;
  uid = data.user.id;
  await claimPendingAddress().catch(() => undefined);
}

/** True when signed straight in; false when the email needs confirming. */
export async function signUp(email: string, password: string, role: string, name: string, address: PendingAddress | null) {
  const { data, error } = await sb.auth.signUp({
    email: email.trim(), password,
    options: { data: { role, full_name: name.trim(), display_name: name.trim() } },
  });
  if (error) throw error;
  // With email confirmation on, GoTrue answers a repeated email with success and no identities, and sends nothing.
  if (data.user?.identities?.length === 0) throw new Error("already registered");
  if (role === "generador" && address?.line) setPendingAddress(address);
  if (!data.session) return false;
  uid = data.session.user.id;
  await claimPendingAddress().catch(() => undefined);
  return true;
}

export const signOut = () => sb.auth.signOut();

export async function loadMyRole(): Promise<string | null> {
  const r = await q(sb.from("profiles").select("role").eq("id", await need()));
  return r[0]?.role ?? null;
}

/* ---------- Profile ---------- */

export async function loadGeneratorProfile() {
  const r = await q(sb.from("generator_profiles").select("points_balance,points_pending,cash_earned_total,current_streak_days,co2_avoided_kg,recycled_kg,water_saved_l,trees_equivalent,profiles(display_name,full_name)").eq("profile_id", await need()));
  return r[0] ?? null;
}
export async function loadMyProfile() {
  const r = await q(sb.from("profiles").select("display_name,phone,avatar_url").eq("id", await need()));
  return r[0] ?? null;
}
export const updateMyProfile = async (displayName: string, phone: string | null) =>
  q(sb.from("profiles").update({ display_name: displayName, phone }).eq("id", await need()));
export async function myEmail() {
  const { data } = await sb.auth.getUser();
  return data.user?.email ?? "";
}

/* ---------- Addresses ---------- */

export async function loadPrimaryAddress() {
  const r = await q(sb.from("addresses").select("id,label,address_line,reference,block_label,is_primary,latitude,longitude").eq("profile_id", await need()).order("is_primary", { ascending: false }).limit(1));
  return r[0] ?? null;
}
/** An edit typed by hand must not erase the coordinates dispatch relies on. */
export async function savePrimaryAddress(addressId: string | null, line: string, lat?: number | null, lng?: number | null) {
  const coords = lat != null && lng != null ? { latitude: lat, longitude: lng } : {};
  if (addressId) await q(sb.from("addresses").update({ address_line: line, ...coords }).eq("id", addressId));
  else await q(sb.from("addresses").insert({ profile_id: await need(), address_line: line, is_primary: true, ...coords }));
}

/* ---------- Wallet ---------- */

export const loadRedemptionOptions = () => q(sb.from("redemption_options").select("id,code,name,kind,description,points_per_unit,min_points,unit_value,icon,color,sort_order,partner").eq("is_active", true).order("sort_order"));
export const loadRedemptions = async () => q(sb.from("redemptions").select("kind,points_spent,monetary_value,status,created_at,redemption_options(name,icon)").eq("user_id", await need()).order("created_at", { ascending: false }));
/** DB triggers validate the balance and post the ledger. */
export const createRedemption = async (optionId: string | null, kind: string, pointsSpent: number, monetaryValue: number) =>
  q(sb.from("redemptions").insert({ user_id: await need(), option_id: optionId, kind, points_spent: pointsSpent, monetary_value: monetaryValue }));

/* ---------- Rankings, challenges, badges ---------- */

export async function loadOpenPeriod(role: "generador" | "eco_rider") {
  const r = await q(sb.from("leaderboard_periods").select("id,scope,period_type,block_label,period_start,period_end,status").eq("status", "open").eq("participant_role", role).order("period_start", { ascending: false }).limit(1));
  return r[0] ?? null;
}
export const loadLeaderboardEntries = (periodId: string) => q(sb.from("leaderboard_entries").select("rank,display_name,points,streak_days,delta,user_id").eq("period_id", periodId).order("rank"));
export const loadRankingRewards = (scope: string, periodType: string) => q(sb.from("ranking_rewards").select("rank,reward_multiplier,reward_points,description").eq("is_active", true).eq("scope", scope).eq("period_type", periodType).order("rank"));
export const loadChallenges = () => q(sb.from("challenges").select("id,name,description,target_value,target_unit,reward_points,user_challenges(progress,is_completed)").eq("is_active", true).order("created_at"));
export const loadMyAchievements = async () => q(sb.from("user_achievements").select("progress,unlocked,achievements(name,icon,tier)").eq("user_id", await need()).order("unlocked", { ascending: false }).order("unlocked_at", { ascending: false, nullsFirst: false }).limit(12));

/* ---------- Market ---------- */

export const loadMaterials = () => q(sb.from("materials").select("id,code,name,icon,color,points_per_unit,points_per_kg,sort_order").eq("is_active", true).order("sort_order"));
export const loadPrices = () => q(sb.from("v_material_prices").select("code,name,price_per_kg,pct_change"));
export const loadMaterialDemand = () => q(sb.from("v_material_demand").select("code,name,centros,mejor_precio,stock_kg,demanda"));
export const loadMarketNews = () => q(sb.from("market_news").select("id,kind,title,summary,material_code,source,url,published_at").eq("is_active", true).order("published_at", { ascending: false }).limit(20));
export const loadCenters = () => q(sb.from("collection_centers").select("id,name,address_line,latitude,longitude,phone,is_open,center_materials(buy_price_per_kg,materials(code,name))").order("name"));

/* ---------- Notifications ---------- */

export const loadNotifications = async () => q(sb.from("notifications").select("id,kind,title,body,is_read,created_at,reference_type").eq("user_id", await need()).order("created_at", { ascending: false }));
export const markAllNotificationsRead = async () => q(sb.from("notifications").update({ is_read: true }).eq("user_id", await need()).eq("is_read", false));
export const markNotificationRead = async (id: number) => q(sb.from("notifications").update({ is_read: true }).eq("id", id).eq("user_id", await need()));

/* ---------- Pickups: family ---------- */

const ITEMS = "pickup_items(reported_quantity,confirmed_quantity,points,materials(id,name,code,icon))";
const PICKUP = `id,code,status,scheduled_date,window_label,eta_minutes,generator_points_awarded,rider_id,created_at,completed_at,latitude,longitude,distance_m,${ITEMS},rider:profiles!pickups_rider_id_fkey(display_name,phone),address:addresses!pickups_address_id_fkey(address_line,latitude,longitude)`;
export const ACTIVE = ["requested", "assigned", "en_route", "arriving", "collecting"];

/** The one still in flight, else the newest: "latest" alone hid a pickup on its way. */
export async function loadTrackedPickup(pickupId?: string | null) {
  const id = await need();
  if (pickupId) return (await q(sb.from("pickups").select(PICKUP).eq("id", pickupId).eq("generator_id", id).limit(1)))[0] ?? null;
  const live = await q(sb.from("pickups").select(PICKUP).eq("generator_id", id).in("status", ACTIVE).order("created_at", { ascending: false }).limit(1));
  if (live[0]) return live[0];
  return (await q(sb.from("pickups").select(PICKUP).eq("generator_id", id).order("created_at", { ascending: false }).limit(1)))[0] ?? null;
}
export const loadPickups = async (limit = 20) => q(sb.from("pickups").select(PICKUP).eq("generator_id", await need()).order("created_at", { ascending: false }).limit(limit));
export const cancelPickup = (id: string) => q(sb.from("pickups").update({ status: "cancelled", cancel_reason: "Cancelado por la familia" }).eq("id", id));
export const loadRiderProfile = async (riderId: string) => (await q(sb.from("rider_profiles").select(RIDER_PROFILE).eq("profile_id", riderId)))[0] ?? null;
export const loadRiderPosition = async (riderId: string) => (await q(sb.from("rider_profiles").select("current_latitude,current_longitude,last_location_at").eq("profile_id", riderId)))[0] ?? null;
export const loadDetections = async (limit = 20) => q(sb.from("detections").select("status,total_points,total_value,total_weight_kg,detected_at,detection_items(quantity,confidence,quality_score,points,materials(id,name,code,icon))").eq("generator_id", await need()).order("detected_at", { ascending: false }).limit(limit));

export type At = { lat: number; lng: number } | null;

export async function createPickup(addressId: string | null, scheduledDate: string, windowLabel: string, items: [string, number][], at: At) {
  const rows = await q(sb.from("pickups").insert({ generator_id: await need(), address_id: addressId, scheduled_date: scheduledDate, window_label: windowLabel, latitude: at?.lat, longitude: at?.lng }).select("id"));
  const pickupId: string = rows[0].id;
  const lines = items.filter(([, n]) => n > 0).map(([material_id, reported_quantity]) => ({ pickup_id: pickupId, material_id, reported_quantity }));
  if (lines.length) await q(sb.from("pickup_items").insert(lines));
  return pickupId;
}
/** Links the verified scan so its points settle on collection. */
export async function createPickupFromDetection(addressId: string | null, scheduledDate: string, windowLabel: string, at: At) {
  const id = await q<string | null>(sb.rpc("create_pickup_from_detection", { p_address_id: addressId, p_scheduled_date: scheduledDate, p_window_label: windowLabel, p_latitude: at?.lat ?? null, p_longitude: at?.lng ?? null }));
  return id || null;
}
/** Only the declaration: the rider's confirmed_quantity is what settles, so this never pays. */
export const updateReportedQuantities = async (pickupId: string, items: [string, number][]) => {
  for (const [m, n] of items) await q(sb.from("pickup_items").update({ reported_quantity: n }).eq("pickup_id", pickupId).eq("material_id", m));
};
export async function assignNearestRider(pickupId: string) {
  const r = await q(sb.rpc("assign_nearest_rider", { p_pickup_id: pickupId }));
  return r?.[0]?.rider_id ? r[0] : null;
}

/* ---------- Scan ---------- */

export type Motion = { maxGyroDps: number; samples: number; windowMs: number };
export type Observation = { items: { code: string; quantity: number; quality: number; brand?: string | null }[]; token: string; group: number };

async function callFunction(name: string, body: Row): Promise<Row> {
  const { data, error } = await sb.functions.invoke(name, { body });
  if (error) {
    const ctx = (error as { context?: Response }).context;
    const detail = ctx && typeof ctx.json === "function" ? await ctx.json().catch(() => null) : null;
    throw new Error(detail?.error ?? detail?.message ?? error.message);
  }
  return data;
}
export const previewFrame = (sessionId: string, frame: string, motion: Motion, group: number) =>
  callFunction("detect-material", { session_id: sessionId, mode: "preview", frames: [frame], motion, group });
export const commitScan = (sessionId: string, observations: Observation[]) =>
  callFunction("detect-material", { session_id: sessionId, mode: "commit", observations });
export async function askAssistant(messages: { role: string; content: string }[]) {
  const r = await callFunction("maki-assistant", { messages });
  if (!r.reply) throw new Error("El asistente no respondió.");
  return r.reply as string;
}

/* ---------- Chat history ---------- */

export const loadConversations = async () => q(sb.from("chat_conversations").select("id,title,last_message_at,created_at").eq("user_id", await need()).order("last_message_at", { ascending: false, nullsFirst: false }).limit(30));
export const loadChatMessages = (id: string) => q(sb.from("chat_messages").select("role,content").eq("conversation_id", id).in("role", ["user", "assistant"]).order("id"));
export async function createConversation(title: string) {
  const r = await q(sb.from("chat_conversations").insert({ user_id: await need(), title }).select("id"));
  return r[0].id as string;
}
export const saveChatMessage = (conversationId: string, role: string, content: string) => q(sb.from("chat_messages").insert({ conversation_id: conversationId, role, content }));

/* ---------- Rider ---------- */

const RIDER_PROFILE = "rating,vehicle_type,rating_count,total_collections,total_recycled_kg,co2_avoided_kg,earnings_balance,earnings_lifetime,monthly_goal,is_available,rider_code";
const RIDER_PICKUP = `id,code,status,scheduled_date,window_label,eta_minutes,rider_earnings,total_weight_kg,generator_points_awarded,rider_id,created_at,completed_at,match_status,condition,notes,cancel_reason,latitude,longitude,distance_m,${ITEMS},generator:profiles!pickups_generator_id_fkey(display_name,phone),address:addresses!pickups_address_id_fkey(address_line,reference,latitude,longitude)`;

export const loadMyRiderProfile = async () => loadRiderProfile(await need());
export const loadMyName = async () => (await q(sb.from("profiles").select("display_name").eq("id", await need())))[0]?.display_name ?? null;
export const loadAvailablePickups = () => q(sb.from("pickups").select(RIDER_PICKUP).eq("status", "requested").is("rider_id", null).order("created_at"));
export const loadMyAssignedPickups = async () => q(sb.from("pickups").select(RIDER_PICKUP).eq("rider_id", await need()).in("status", ["assigned", "en_route", "arriving", "collecting"]).order("created_at"));
export const loadRiderHistory = async (limit = 40) => q(sb.from("pickups").select(RIDER_PICKUP).eq("rider_id", await need()).in("status", ["completed", "cancelled", "failed", "no_match"]).order("created_at", { ascending: false }).limit(limit));
export const loadRiderEarnings = async (limit = 200) => q(sb.from("rider_earnings").select("amount,created_at,type,description").eq("rider_id", await need()).order("created_at", { ascending: false }).limit(limit));

/** Zero rows back means another rider won the race; the fee is priced by trigger, never here. */
export async function acceptPickup(pickupId: string) {
  const r = await q(sb.from("pickups").update({ rider_id: await need(), status: "assigned" }).eq("id", pickupId).eq("status", "requested").is("rider_id", null).select("id"));
  return r.length > 0;
}
export const setPickupStatus = (id: string, status: string) => q(sb.from("pickups").update({ status }).eq("id", id));
export const releasePickup = (id: string) => q(sb.rpc("release_pickup", { p_pickup_id: id }));
export const confirmQuantities = async (id: string, items: [string, number][]) => {
  for (const [m, n] of items) await q(sb.from("pickup_items").update({ confirmed_quantity: n }).eq("pickup_id", id).eq("material_id", m));
};
export const completePickup = (id: string) => q(sb.rpc("complete_pickup", { p_pickup_id: id }));

/** Position only: the shift stays whatever the rider last chose. */
export const publishRiderLocation = async (lat: number, lng: number) =>
  q(sb.from("rider_profiles").update({ current_latitude: lat, current_longitude: lng, last_location_at: new Date().toISOString() }).eq("profile_id", await need()));
/** A PATCH matching no row is not an error, so ask for the row back and check it came. */
export async function setRiderAvailability(available: boolean) {
  const r = await q(sb.from("rider_profiles").update({ is_available: available }).eq("profile_id", await need()).select("profile_id"));
  if (!r.length) throw new Error("No pudimos cambiar tu turno: tu perfil de Eco-Rider no respondió.");
}
export const setRiderShiftStart = async (at: Date | null) =>
  q(sb.from("rider_profiles").update({ shift_start_at: at ? at.toISOString() : null }).eq("profile_id", await need()));

/** dp=is.null is the anti-join for "in no delivery yet"; create_delivery has the final word. */
export const loadUndeliveredPickups = async () => q(sb.from("pickups").select(`${RIDER_PICKUP},dp:delivery_pickups(pickup_id)`).eq("rider_id", await need()).eq("status", "completed").is("dp", null).order("completed_at"));
export const loadMyDeliveries = async () => q(sb.from("deliveries").select("id,code,status,total_kg,rider_amount,departed_at,settled_at,center:collection_centers(name),delivery_pickups(pickup_id)").eq("rider_id", await need()).order("created_at", { ascending: false }).limit(10));
export const createDelivery = (centerId: string, pickupIds: string[]) => q(sb.rpc("create_delivery", { p_center_id: centerId, p_pickup_ids: pickupIds }));
