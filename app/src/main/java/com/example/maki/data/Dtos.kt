package com.example.maki.data

import kotlinx.serialization.Serializable

// ---- Auth ----
@Serializable
data class CredentialsDto(val email: String, val password: String)

/**
 * Sign-up body. [data] lands in `raw_user_meta_data`, which the `handle_new_user`
 * trigger reads to create the profile row and its role extension (generator_profiles
 * / rider_profiles) — so the role must be chosen before the account is created.
 */
@Serializable
data class SignUpDto(
    val email: String,
    val password: String,
    val data: SignUpMetaDto,
)

@Serializable
data class SignUpMetaDto(
    val role: String,
    val full_name: String,
    val display_name: String,
    val phone: String? = null,
)

@Serializable
data class RefreshDto(val refresh_token: String)

@Serializable
data class SessionDto(
    // Blank when sign-up needs an email confirmation: GoTrue then returns the
    // bare user object with no tokens at all.
    val access_token: String = "",
    val refresh_token: String? = null,
    val user: UserDto? = null,
)

@Serializable
data class UserDto(val id: String, val email: String? = null)

// ---- Domain (PostgREST rows) ----
@Serializable
data class ProfileDto(
    val display_name: String? = null,
    val full_name: String? = null,
)

@Serializable
data class GeneratorProfileDto(
    val points_balance: Int = 0,
    val cash_earned_total: Double = 0.0,
    val current_streak_days: Int = 0,
    val co2_avoided_kg: Double = 0.0,
    val recycled_kg: Double = 0.0,
    val water_saved_l: Double = 0.0,
    val trees_equivalent: Double = 0.0,
    val profiles: ProfileDto? = null,
)

// ---- Wallet ----
@Serializable
data class RedemptionOptionDto(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val kind: String = "",
    val description: String? = null,
    val points_per_unit: Double = 0.0,
    val min_points: Int = 0,
    val unit_value: Double = 0.0,
    val icon: String? = null,
    val color: String? = null,
    val sort_order: Int = 0,
)

@Serializable
data class RedemptionDto(
    val kind: String = "",
    val points_spent: Int = 0,
    val monetary_value: Double = 0.0,
    val status: String? = null,
    val created_at: String = "",
    val redemption_options: OptionNameDto? = null,
)

@Serializable
data class OptionNameDto(val name: String? = null, val icon: String? = null)

// ---- Rankings ----
@Serializable
data class LeaderboardPeriodDto(
    val id: String,
    val scope: String = "",
    val period_type: String = "",
    val block_label: String? = null,
    val period_start: String? = null,
    val period_end: String? = null,
    val status: String? = null,
)

@Serializable
data class LeaderboardEntryDto(
    val rank: Int = 0,
    val display_name: String? = null,
    val points: Int = 0,
    val streak_days: Int = 0,
    val delta: Int = 0,
    val user_id: String = "",
)

@Serializable
data class RankingRewardDto(
    val rank: Int = 0,
    val reward_multiplier: Double = 1.0,
    val reward_points: Int = 0,
    val description: String? = null,
)

// ---- Materials / Address ----
@Serializable
data class MaterialDto(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val icon: String? = null,
    val color: String? = null,
    val points_per_unit: Double = 0.0,
    val sort_order: Int = 0,
)

@Serializable
data class AddressDto(
    val id: String = "",
    val label: String? = null,
    val address_line: String = "",
    val reference: String? = null,
    val block_label: String? = null,
    val is_primary: Boolean = false,
    // Most pickups have no coordinates of their own; the address does.
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class NewAddress(
    val profile_id: String,
    val address_line: String,
    val is_primary: Boolean = true,
    // Set on sign-up from the device fix: dispatch needs a point, not a string.
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class AddressPatch(
    val address_line: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

// ---- Notifications ----
@Serializable
data class NotificationDto(
    val id: Long = 0,
    val kind: String = "",
    val title: String = "",
    val body: String? = null,
    val is_read: Boolean = false,
    val created_at: String = "",
)

// ---- Pickups (tracking + history) ----
@Serializable
data class MaterialNameDto(val id: String? = null, val name: String? = null, val code: String? = null, val icon: String? = null)

@Serializable
data class PickupItemDto(
    val reported_quantity: Int = 0,
    val confirmed_quantity: Int? = null,
    val points: Int = 0,
    val materials: MaterialNameDto? = null,
)

@Serializable
data class RiderNameDto(val display_name: String? = null)

@Serializable
data class PickupDto(
    val id: String? = null,
    val code: String? = null,
    val status: String = "",
    val scheduled_date: String? = null,
    val window_label: String? = null,
    val eta_minutes: Int? = null,
    val generator_points_awarded: Int? = null,
    val rider_id: String? = null,
    val rider_earnings: Double? = null,
    val total_weight_kg: Double? = null,
    val created_at: String? = null,
    val completed_at: String? = null,
    // Rider history/route extras: outcome of the stop and where it is.
    val match_status: String? = null,   // exact | partial | none
    val condition: String? = null,      // good | regular | bad
    val notes: String? = null,
    val cancel_reason: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distance_m: Double? = null,
    val pickup_items: List<PickupItemDto> = emptyList(),
    val rider: RiderNameDto? = null,
    // Rider-side embeds (null on generator queries).
    val generator: RiderNameDto? = null,
    val address: AddressDto? = null,
)

@Serializable
data class RiderEarningDto(
    val amount: Double = 0.0,
    val created_at: String = "",
    val type: String? = null,
    val description: String? = null,
)

@Serializable
data class RiderProfileDto(
    val rating: Double? = null,
    val vehicle_type: String? = null,
    val rating_count: Int = 0,
    val total_collections: Int = 0,
    val total_recycled_kg: Double = 0.0,
    val co2_avoided_kg: Double = 0.0,
    val earnings_balance: Double = 0.0,
    val earnings_lifetime: Double = 0.0,
    val monthly_goal: Double? = null,
)

// ---- Rider route (routes + route_stops → the optimised stop list) ----
@Serializable
data class RouteStopDto(
    val sequence: Int = 0,
    val status: String = "",            // pending | en_route | arrived | collected | skipped
    val eta_minutes: Int? = null,
    val distance_from_prev_m: Double? = null,
    val pickups: PickupDto? = null,
)

@Serializable
data class RouteDto(
    val id: String = "",
    val status: String = "",            // planned | active | completed | cancelled
    val route_date: String? = null,
    val total_stops: Int = 0,
    val total_distance_m: Double? = null,
    val total_duration_min: Int? = null,
    val route_stops: List<RouteStopDto> = emptyList(),
)

// ---- Achievements (rider badges) ----
@Serializable
data class AchievementDto(
    val name: String = "",
    val icon: String? = null,
    val tier: String? = null,
)

@Serializable
data class UserAchievementDto(
    val progress: Double = 0.0,
    val unlocked: Boolean = false,
    val achievements: AchievementDto? = null,
)

// ---- Detections (history) ----
@Serializable
data class DetectionItemDto(
    val quantity: Int = 0,
    val confidence: Double? = null,
    val quality_score: Double? = null,
    val points: Int = 0,
    val materials: MaterialNameDto? = null,
)

@Serializable
data class DetectionDto(
    val status: String = "",
    val total_points: Int = 0,
    val total_value: Double = 0.0,
    val total_weight_kg: Double = 0.0,
    val detected_at: String = "",
    val detection_items: List<DetectionItemDto> = emptyList(),
)

// ---- Write payloads ----
@Serializable
data class NewRedemption(
    val user_id: String,
    val option_id: String?,
    val kind: String,
    val points_spent: Int,
    val monetary_value: Double,
)

@Serializable
data class NewPickup(
    val generator_id: String,
    val address_id: String? = null,
    val scheduled_date: String? = null,
    val window_label: String? = null,
)

@Serializable
data class NewPickupItem(
    val pickup_id: String,
    val material_id: String,
    val reported_quantity: Int,
)

// ---- Dispatch (assign_nearest_rider RPC) + live rider position ----

@Serializable
data class RpcAssignRider(val p_pickup_id: String)

/** One row from assign_nearest_rider; empty list = no rider available right now. */
@Serializable
data class AssignedRiderDto(
    val rider_id: String? = null,
    val rider_name: String? = null,
    val distance_m: Double? = null,
    val eta_minutes: Int? = null,
)

/** Where the assigned rider is right now (RLS exposes it only to their generator). */
@Serializable
data class RiderPositionDto(
    val current_latitude: Double? = null,
    val current_longitude: Double? = null,
    val last_location_at: String? = null,
)

@Serializable
data class RiderLocationPatch(
    val current_latitude: Double,
    val current_longitude: Double,
    val last_location_at: String,
    /** App open = on shift, the way a delivery app reads availability. */
    val is_available: Boolean = true,
)

// ---- AI scan (server-authoritative, via the detect-material Edge Function) ----

/** Liveness motion summary captured on-device during the scan window. */
@Serializable
data class MotionDto(val maxGyroDps: Double, val samples: Int, val windowMs: Long)

/** What the server counted in one frame, signed by it — the client only relays it back. */
@Serializable
data class ScanObsItemDto(val code: String, val quantity: Int, val quality: Double)

@Serializable
data class ScanObservationDto(val items: List<ScanObsItemDto>, val token: String)

/**
 * One call to detect-material. `preview` scores a frame of the live scan (nothing is
 * saved); `commit` closes the session and persists the detection from the signed
 * observations gathered along the way.
 */
@Serializable
data class DetectRequest(
    val session_id: String,
    val mode: String,
    val frames: List<String> = emptyList(),
    val motion: MotionDto? = null,
    val observations: List<ScanObservationDto> = emptyList(),
)

@Serializable
data class DetectedItemDto(
    val code: String = "",
    val name: String = "",
    val quantity: Int = 0,
    val confidence: Double = 0.0,
    val quality: Double = 0.0,
    val points: Int = 0,
    /** Material row id, so the pickup can be built without re-looking-up the catalog. */
    val material_id: String? = null,
    /** [x0,y0,x1,y1] as 0..1 fractions of the captured frame; null when the model gave none. */
    val box: List<Double>? = null,
)

/**
 * Response of detect-material. On success carries the persisted detection;
 * on an anti-spoof/empty result [rejected]+[message] are set (HTTP 200, nothing saved).
 */
@Serializable
data class DetectResultDto(
    val detection_id: String? = null,
    val status: String? = null,
    val total_points: Int = 0,
    val total_value: Double = 0.0,
    val items: List<DetectedItemDto> = emptyList(),
    /** Preview only: the server's signature over this frame's count. */
    val token: String? = null,
    /** How long to wait before the next frame when the detector is rate-limited. */
    val retry_after_ms: Long? = null,
    val rejected: String? = null,   // "liveness" | "spoof" | "no_material" | "busy"
    val message: String? = null,
    val error: String? = null,
)

// ---- Assistant agent (maki-assistant Edge Function) ----

@Serializable
data class ChatTurnDto(val role: String, val content: String)

@Serializable
data class AssistantRequest(val messages: List<ChatTurnDto>)

@Serializable
data class AssistantReplyDto(val reply: String? = null, val error: String? = null)

// ---- Challenges (retos; progress advances server-side on pickup settle) ----

@Serializable
data class UserChallengeDto(val progress: Double = 0.0, val is_completed: Boolean = false)

@Serializable
data class ChallengeDto(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val target_value: Double = 0.0,
    val target_unit: String? = null,
    val reward_points: Int = 0,
    val user_challenges: List<UserChallengeDto> = emptyList(),
)

// ---- Chat history (chat_conversations + chat_messages tables) ----

@Serializable
data class ChatConversationDto(
    val id: String = "",
    val title: String? = null,
    val last_message_at: String? = null,
    val created_at: String = "",
)

@Serializable
data class ChatMessageDto(val role: String = "user", val content: String = "")

@Serializable
data class NewConversation(val user_id: String, val title: String?)

@Serializable
data class NewChatMessage(val conversation_id: String, val role: String, val content: String)

// ---- Market prices (v_material_prices view) ----

@Serializable
data class MaterialPriceDto(
    val code: String = "",
    val name: String = "",
    val price_per_kg: Double = 0.0,
    val pct_change: Double = 0.0,
)

// ---- Collection centers (acopio map) ----

@Serializable
data class CenterMaterialDto(val buy_price_per_kg: Double = 0.0, val materials: MaterialNameDto? = null)

@Serializable
data class CollectionCenterDto(
    val id: String = "",
    val name: String = "",
    val address_line: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val phone: String? = null,
    val is_open: Boolean = true,
    val center_materials: List<CenterMaterialDto> = emptyList(),
)

// ---- RPC args (scan→pickup→points loop) ----

@Serializable
data class RpcCreatePickup(
    val p_address_id: String? = null,
    val p_scheduled_date: String? = null,
    val p_window_label: String? = null,
)

@Serializable
data class RpcCompletePickup(val p_pickup_id: String)
