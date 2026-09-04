package com.example.maki.ui.screens.generator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.maki.data.CollectionCenterDto
import com.example.maki.data.DetectResultDto
import com.example.maki.data.DetectedItemDto
import com.example.maki.data.DetectionDto
import com.example.maki.data.GeneratorProfileDto
import com.example.maki.data.LeaderboardEntryDto
import com.example.maki.data.MakiApi
import com.example.maki.data.MaterialPriceDto
import com.example.maki.data.MakiRepository
import com.example.maki.data.NotificationDto
import com.example.maki.data.PickupDto
import com.example.maki.data.PickupItemDto
import com.example.maki.data.RedemptionDto
import com.example.maki.data.RedemptionOptionDto
import com.example.maki.data.RoutePlanner
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** UI state for the generator (Eco-Hero) home + profile header. */
data class GeneratorUiState(
    val displayName: String = "Fam. Nureña",
    val initials: String = "FN",
    val points: Int = 4250,
    val streakDays: Int = 21,
    val cashEarned: String = "S/12.50",
    val co2: String = "12.4 kg",
    val recycled: String = "48 kg",
    val water: String = "3.2 m³",
    val moneyValue: String = "S/4.25",
)

// ---- Per-screen UI models ----

data class WalletUi(
    val balancePoints: Int,
    val balanceCash: String,
    val progress: Float,
    val nextRedeemHint: String,
    val options: List<RedeemOptionUi>,
    val history: List<RedeemHistoryUi>,
)
data class RedeemOptionUi(
    val kind: String,
    val title: String,
    val rule: String,
    val suggested: Boolean,
    val optionId: String = "",
    val cost: Int = 0,
    val monetaryValue: Double = 0.0,
    val affordable: Boolean = true,
)
data class RedeemHistoryUi(val title: String, val date: String, val amount: String)

data class RankingsUi(
    val boardTitle: String,
    val entries: List<RankEntryUi>,
    val rewards: List<RewardUi>,
)
data class RankEntryUi(
    val rank: Int,
    val name: String,
    val initial: String,
    val points: String,
    val streak: String?,
    val extra: String?,
    val isYou: Boolean,
)
data class RewardUi(val rank: Int, val text: String)

data class PickupFormUi(
    val items: List<PickupFormItemUi>,
    val addressLine: String,
    val addressId: String? = null,
)
data class PickupFormItemUi(val code: String, val label: String, val initialQty: Int, val materialId: String = "")

data class TrackingUi(
    val headerTitle: String,
    val riderLine: String,
    val etaText: String,
    val itemsText: String,
    val statusMessage: String,
    val pickupId: String? = null,
    val canComplete: Boolean = false,
    // Live map: where the family is, where the rider is right now, and how far.
    val homeLat: Double? = null,
    val homeLng: Double? = null,
    val riderLat: Double? = null,
    val riderLng: Double? = null,
    val distanceText: String? = null,
) {
    val hasMap: Boolean get() = homeLat != null && homeLng != null
}

data class CenterUi(
    val name: String,
    val address: String,
    val open: Boolean,
    val materials: String,   // "PET, Aluminio, Cartón"
    val lat: Double?,
    val lng: Double?,
    val phone: String?,
)

data class HistoryUi(val sections: List<HistorySectionUi>)
data class HistorySectionUi(val label: String, val entries: List<HistoryEntryUi>)
data class HistoryEntryUi(
    val isRedeem: Boolean,
    val code: String,            // material code or "redeem"
    val title: String,
    val meta: String,            // "+92 pts · S/0.34" or redeem date
    val statusText: String?,
    val statusKind: String?,     // "done" | "assigned" | null
    val note: String?,
    val amount: String?,
)

data class NotifUi(
    val kind: String,
    val title: String,
    val body: String,
    val time: String,
    val unread: Boolean,
    val highlighted: Boolean,
    val id: Long = 0,
)

data class ChallengeUi(
    val title: String,        // "Reto: La Gran Aplastada"
    val rewardText: String,   // "+100 pts"
    val progress: Float,      // 0..1 for the bar
    val progressText: String, // "8 / 15 botellas · +100 pts al completar"
    val completed: Boolean,
)

data class DetectionResultUi(
    val items: List<DetectedItemUi>,
    val totalPoints: Int,
    val totalValue: String,
    val streakText: String,
    val marketPrice: String? = null,   // "S/1.20/kg" of the top material
    val marketPct: String? = null,     // "+3%"
    val marketNote: String? = null,    // "El PET subió 3% esta semana."
    val shareText: String = "",        // for the social share intent
)
data class DetectedItemUi(
    val code: String,
    val name: String,
    val qty: String,      // "x8"
    val meta: String,     // "Confianza 92% · Calidad 0.9"
    val note: String?,
    /** What the AI counted, kept so the user can see what they are correcting. */
    val detectedQty: Int = 0,
)

/** The Eco-Rider dispatch just confirmed for a pickup. */
data class DispatchUi(val riderName: String, val etaMinutes: Int?, val searching: Boolean)

class GeneratorViewModel : ViewModel() {

    var state by mutableStateOf(GeneratorUiState())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var wallet by mutableStateOf<WalletUi?>(null)
        private set
    var rankings by mutableStateOf<RankingsUi?>(null)
        private set
    var pickupForm by mutableStateOf<PickupFormUi?>(null)
        private set
    var tracking by mutableStateOf<TrackingUi?>(null)
        private set
    var history by mutableStateOf<HistoryUi?>(null)
        private set
    var notifications by mutableStateOf<List<NotifUi>?>(null)
        private set
    /** Result of the most recent verified scan, shown on the result screen. */
    var lastDetection by mutableStateOf<DetectionResultUi?>(null)
        private set
    /** Live market prices per material (drives the result card + price alerts). */
    var prices by mutableStateOf<List<MaterialPriceDto>>(emptyList())
        private set
    /** Collection centers for the acopio map. */
    var centers by mutableStateOf<List<CenterUi>?>(null)
        private set
    /** Active reto with the user's progress, shown on the result screen. */
    var challenge by mutableStateOf<ChallengeUi?>(null)
        private set
    /** The detection just saved this session; non-null means "pedir recojo" links it. */
    private var lastDetectionId: String? = null

    /**
     * Quantities the user corrected on the result screen, per material code. The AI
     * counts what it can see; the person knows what is actually in the bag, and the
     * rider still confirms the physical count, so an edit here is a declaration —
     * never a payout.
     */
    var detectedQty by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** Who is coming and in how long, after a confirmed pickup — drives the dispatch dialog. */
    var dispatch by mutableStateOf<DispatchUi?>(null)
        private set

    /** One-shot user message for the snackbar; cleared by [consumeMessage]. */
    var message by mutableStateOf<String?>(null)
        private set
    /** True while a write action is in flight (disables the triggering button). */
    var busy by mutableStateOf(false)
        private set
    /** Whether the user has any unread notifications (drives the home bell dot). */
    var hasUnread by mutableStateOf(true)
        private set

    fun consumeMessage() { message = null }
    /** Surfaces a UI-originated info message in the shared snackbar. */
    fun notify(text: String) { message = text }

    init {
        // Paint the cached profile immediately, then refresh from the network.
        MakiRepository.cachedProfile()?.let { state = it.toUiState(); loading = false }
        refresh()
    }

    private suspend fun ensureSignedIn() {
        if (!MakiApi.isSignedIn) MakiRepository.signInAsTestGenerator()
    }

    /** Runs [block] after ensuring a session, swallowing errors into [error]. */
    private fun load(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                ensureSignedIn()
                block()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    fun refresh() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                ensureSignedIn()
                MakiRepository.loadGeneratorProfile()?.let { state = it.toUiState() }
                runCatching { prices = MakiRepository.loadPrices() }  // best-effort, drives price cards
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun loadWallet() = load {
        if (wallet != null) return@load
        fetchWallet()
    }

    private suspend fun fetchWallet() {
        val options = MakiRepository.loadRedemptionOptions()
        val redemptions = MakiRepository.loadRedemptions()
        wallet = buildWallet(state.points, options, redemptions)
    }

    fun loadRankings() = load {
        if (rankings != null) return@load
        val period = MakiRepository.loadOpenPeriod() ?: return@load
        val entries = MakiRepository.loadLeaderboardEntries(period.id)
        val rewards = MakiRepository.loadRankingRewards(period.scope, period.period_type)
        val me = MakiApi.session?.userId
        val topPoints = entries.firstOrNull()?.points ?: 0
        rankings = RankingsUi(
            boardTitle = period.block_label?.let { "Jr. $it" } ?: "Mi Ranking",
            entries = entries.map { it.toUi(me, topPoints) },
            rewards = rewards.map { RewardUi(it.rank, it.description ?: "x${trimNum(it.reward_multiplier)} puntos") },
        )
    }

    fun loadPickupForm() = load {
        if (pickupForm != null) return@load
        fetchPickupForm()
    }

    private suspend fun fetchPickupForm() {
        val address = MakiRepository.loadPrimaryAddress()
        val materials = MakiRepository.loadMaterials()
        val detection = MakiRepository.loadDetections(limit = 1).firstOrNull()
        val qtyByCode = detection?.detection_items.orEmpty()
            .filter { it.quantity > 0 && it.materials?.code != null }
            .associate { it.materials!!.code!! to it.quantity }
            // A correction made on the result screen wins over what the AI counted.
            .let { detected -> detected + detectedQty.filterKeys { it in detected.keys } }
        // Show the detected materials, or default to the two common recyclables.
        val codes: Collection<String> = if (qtyByCode.isEmpty()) listOf("PET", "ALU") else qtyByCode.keys
        val items = codes.mapNotNull { code ->
            materials.firstOrNull { it.code == code }?.let { m ->
                PickupFormItemUi(
                    code = m.code, label = pickupLabel(m.code, m.name),
                    initialQty = qtyByCode[code] ?: 0, materialId = m.id,
                )
            }
        }
        pickupForm = PickupFormUi(
            items = items,
            addressLine = address?.address_line ?: "Sin dirección registrada",
            addressId = address?.id?.takeIf { it.isNotBlank() },
        )
    }

    fun loadTracking() = load {
        if (tracking != null) return@load
        fetchTracking()
    }

    /**
     * Re-reads the pickup and the rider's current position. Called on a timer while
     * the tracking screen is open — this is the "watch them approach" loop, and it
     * lives in the screen's scope so it stops the moment the user leaves.
     */
    fun refreshTracking() {
        viewModelScope.launch {
            runCatching {
                ensureSignedIn()
                fetchTracking()
            }
        }
    }

    private suspend fun fetchTracking() {
        val pickup = MakiRepository.loadLatestPickup() ?: return
        val rider = pickup.rider_id?.let { MakiRepository.loadRiderProfile(it) }
        val riderName = pickup.rider?.display_name ?: "Eco-Rider"
        val vehicle = vehicleLabel(rider?.vehicle_type)
        val ratingTxt = rider?.rating?.let { " · ★ ${"%.1f".format(Locale.US, it)}" } ?: ""

        val home = (pickup.latitude to pickup.longitude)
            .takeIf { it.first != null && it.second != null }
            ?: (pickup.address?.latitude to pickup.address?.longitude)
        val riderPos = pickup.rider_id
            ?.takeIf { pickup.status !in setOf("completed", "cancelled") }
            ?.let { runCatching { MakiRepository.loadRiderPosition(it) }.getOrNull() }

        // Straight-line distance is honest here: it says "3 cuadras", not a route.
        val km = if (home.first != null && riderPos?.current_latitude != null) {
            RoutePlanner.distanceKm(
                RoutePlanner.LatLng(home.first!!, home.second!!),
                RoutePlanner.LatLng(riderPos.current_latitude, riderPos.current_longitude ?: 0.0),
            )
        } else null
        val liveEta = km?.let { RoutePlanner.etaMinutes(it) }
        val eta = liveEta ?: pickup.eta_minutes

        tracking = TrackingUi(
            headerTitle = trackingHeader(pickup.status),
            riderLine = "$riderName · $vehicle$ratingTxt",
            etaText = if (eta != null && eta > 0) "~$eta min" else (pickup.window_label ?: "En camino"),
            itemsText = itemsSummary(pickup.pickup_items),
            statusMessage = trackingMessage(pickup.status, riderName),
            pickupId = pickup.id,
            canComplete = pickup.status !in setOf("completed", "cancelled"),
            homeLat = home.first,
            homeLng = home.second,
            riderLat = riderPos?.current_latitude,
            riderLng = riderPos?.current_longitude,
            distanceText = km?.let { "A ${RoutePlanner.distanceLabel(it)}" },
        )
    }

    /** Saves the primary address (creating it on first use) and refreshes the form. */
    fun saveAddress(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) { message = "Escribe tu dirección."; return }
        runAction(success = "Dirección actualizada.") {
            MakiRepository.savePrimaryAddress(pickupForm?.addressId, trimmed)
            fetchPickupForm()
        }
    }

    /** Always fetched fresh: progress moves server-side whenever a pickup settles. */
    fun loadChallenge() = load {
        val all = MakiRepository.loadChallenges().map { it.toUi() }
        challenge = all.firstOrNull { !it.completed } ?: all.firstOrNull()
    }

    fun loadCenters() = load {
        if (centers != null) return@load
        centers = MakiRepository.loadCenters().map { it.toUi() }
    }

    fun loadHistory() = load {
        if (history != null) return@load
        val detections = MakiRepository.loadDetections()
        val redemptions = MakiRepository.loadRedemptions()
        history = buildHistory(detections, redemptions)
    }

    fun loadNotifications() = load {
        if (notifications != null) return@load
        val list = MakiRepository.loadNotifications()
        notifications = list.mapIndexed { i, n -> n.toUi(highlighted = i == 0 && !n.is_read) }
        hasUnread = list.any { !it.is_read }
    }

    // ---- Write actions ----

    /** Runs a write [block], showing [success] (or the error) in the snackbar. */
    private fun runAction(success: String, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                ensureSignedIn()
                block()
                message = success
                onSuccess()
            } catch (e: Exception) {
                message = e.message ?: "Algo salió mal"
            } finally {
                busy = false
            }
        }
    }

    /** Spends points on the chosen redemption option, then refreshes balance + wallet. */
    fun redeem(option: RedeemOptionUi) {
        if (!option.affordable) { message = "Saldo insuficiente para este canje."; return }
        runAction(success = "¡Canje realizado con éxito!") {
            MakiRepository.createRedemption(
                optionId = option.optionId.ifBlank { null },
                kind = option.kind,
                pointsSpent = option.cost,
                monetaryValue = option.monetaryValue,
            )
            MakiRepository.loadGeneratorProfile()?.let { state = it.toUiState() }
            fetchWallet()
        }
    }

    /** Creates a pickup request with the selected items, then invokes [onDone] (navigate). */
    /**
     * Creates the pickup, then dispatches it to the nearest available Eco-Rider —
     * the taxi-app move: the family asks once and someone is already on the way,
     * instead of the request sitting on a board. [onDone] navigates to tracking.
     */
    fun createPickup(selections: List<Pair<String, Int>>, windowLabel: String, onDone: () -> Unit) {
        if (selections.none { it.second > 0 }) { message = "Agrega al menos un material al recojo."; return }
        val form = pickupForm
        val detId = lastDetectionId
        runAction(success = "¡Recojo solicitado!", onSuccess = onDone) {
            val pickupId = if (detId != null) {
                // Loop: link the verified detection so its points settle on collection.
                val id = MakiRepository.createPickupFromDetection(form?.addressId, scheduledDateFor(windowLabel), windowLabel)
                lastDetectionId = null
                // The RPC copies the AI's counts; re-state what the user actually has.
                if (id != null) runCatching { MakiRepository.updateReportedQuantities(id, selections) }
                id
            } else {
                MakiRepository.createPickup(
                    addressId = form?.addressId,
                    scheduledDate = scheduledDateFor(windowLabel),
                    windowLabel = windowLabel,
                    items = selections,
                )
            }
            // Dispatch must never sink the request: if it fails, the pickup still
            // exists as 'requested' and any rider can take it from the board.
            val assigned = pickupId?.let { runCatching { MakiRepository.assignNearestRider(it) }.getOrNull() }
            dispatch = DispatchUi(
                riderName = assigned?.rider_name ?: "Un Eco-Rider",
                etaMinutes = assigned?.eta_minutes,
                searching = assigned == null,
            )
            tracking = null  // invalidate caches so Tracking/History reload the new pickup
            history = null
            detectedQty = emptyMap()
        }
    }

    /** Confirms the active pickup as collected → settle_pickup credits the points. */
    fun completeActivePickup() {
        val id = tracking?.pickupId ?: run { message = "No hay un recojo activo."; return }
        runAction(success = "¡Recojo confirmado! Tus Eco-Puntos fueron acreditados.") {
            MakiRepository.completePickup(id)
            tracking = null   // reload so it shows completed
            history = null
            MakiRepository.loadGeneratorProfile()?.let { state = it.toUiState() }  // points just credited
        }
    }

    /** Marks every notification read; updates the list + home bell. */
    fun markNotificationsRead() = runAction(success = "Notificaciones marcadas como leídas") {
        if (notifications?.any { it.unread } != true) return@runAction
        MakiRepository.markAllNotificationsRead()
        notifications = notifications?.map { it.copy(unread = false, highlighted = false) }
        hasUnread = false
    }

    /** Marks a single notification read (silent, optimistic). */
    fun markNotificationRead(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            try {
                ensureSignedIn()
                MakiRepository.markNotificationRead(id)
                notifications = notifications?.map { if (it.id == id) it.copy(unread = false, highlighted = false) else it }
                hasUnread = notifications?.any { it.unread } ?: false
            } catch (_: Exception) { /* best-effort */ }
        }
    }

    /** Called by the camera once a scan is verified + persisted server-side. */
    fun onDetectionSaved(result: DetectResultDto) {
        lastDetection = result.toUi(state.streakDays, prices)
        lastDetectionId = result.detection_id   // "pedir recojo" will link this detection
        detectedQty = result.items.associate { it.code to it.quantity }
        pickupForm = null   // the new detection seeds the pickup form…
        history = null      // …and appears in history
    }

    /** Corrects the count for one material on the result screen (never below zero). */
    fun adjustDetectedQty(code: String, delta: Int) {
        val current = detectedQty[code] ?: return
        val next = (current + delta).coerceIn(0, 999)
        if (next == current) return
        detectedQty = detectedQty + (code to next)
        pickupForm = null   // the form must pick the corrected count up
    }

    /** Dismisses the "un Eco-Rider va en camino" dialog. */
    fun consumeDispatch() { dispatch = null }

    /** Clears the session + cached data so the next launch lands on Login. */
    fun signOut() {
        MakiRepository.signOut()
    }
}

// ---- Mapping helpers ----

private fun GeneratorProfileDto.toUiState(): GeneratorUiState {
    val name = profiles?.display_name ?: profiles?.full_name ?: "Eco-Hero"
    return GeneratorUiState(
        displayName = name,
        initials = initialsOf(name),
        points = points_balance,
        streakDays = current_streak_days,
        cashEarned = "S/%.2f".format(Locale.US, cash_earned_total),
        co2 = "%.1f kg".format(Locale.US, co2_avoided_kg),
        recycled = "%.0f kg".format(Locale.US, recycled_kg),
        water = "%.1f m³".format(Locale.US, water_saved_l / 1000.0),
        moneyValue = "S/%.2f".format(Locale.US, points_balance / 1000.0),
    )
}

private fun buildWallet(
    balance: Int,
    options: List<RedemptionOptionDto>,
    redemptions: List<RedemptionDto>,
): WalletUi {
    val cash = options.firstOrNull { it.kind == "cash" }
    val target = cash?.min_points ?: 5000
    val progress = if (target > 0) (balance.toFloat() / target).coerceIn(0f, 1f) else 0f
    val remaining = (target - balance).coerceAtLeast(0)
    val cashValue = if (cash != null && cash.points_per_unit > 0)
        cash.min_points / cash.points_per_unit * cash.unit_value else 5.0
    val hint = "Faltan ${groupInt(remaining)} pts para tu próximo canje de S/%.2f".format(Locale.US, cashValue)

    return WalletUi(
        balancePoints = balance,
        balanceCash = "= S/%.2f".format(Locale.US, balance / 1000.0),
        progress = progress,
        nextRedeemHint = hint,
        options = options.mapIndexed { i, o ->
            val mv = if (o.kind == "cash" && o.points_per_unit > 0) o.min_points / o.points_per_unit * o.unit_value else 0.0
            RedeemOptionUi(
                kind = o.kind, title = o.name, rule = o.description ?: "", suggested = i == 0,
                optionId = o.id, cost = o.min_points, monetaryValue = mv, affordable = balance >= o.min_points,
            )
        },
        history = redemptions.map {
            val name = it.redemption_options?.name ?: redeemKindLabel(it.kind)
            val title = if (it.monetary_value > 0) "$name · S/%.2f".format(Locale.US, it.monetary_value) else name
            RedeemHistoryUi(title = title, date = MakiTime.dayMonth(it.created_at), amount = "−${groupInt(it.points_spent)} pts")
        },
    )
}

private fun LeaderboardEntryDto.toUi(me: String?, topPoints: Int): RankEntryUi {
    val mine = user_id == me
    val nm = display_name ?: "Vecino"
    val showStreak = rank == 1 || mine
    val extra = if (mine && rank > 1) "A ${topPoints - points} pts del 1° lugar" else null
    return RankEntryUi(
        rank = rank,
        name = nm,
        initial = surnameInitial(nm),
        points = groupInt(points),
        streak = if (showStreak && streak_days > 0) "Racha $streak_days días" else null,
        extra = extra,
        isYou = mine,
    )
}

private fun buildHistory(detections: List<DetectionDto>, redemptions: List<RedemptionDto>): HistoryUi {
    val sections = mutableListOf<HistorySectionUi>()
    detections
        .groupBy { MakiTime.dayLabel(it.detected_at) }
        .forEach { (label, dets) ->
            sections += HistorySectionUi(label, dets.map { it.toHistoryEntry() })
        }
    if (redemptions.isNotEmpty()) {
        sections += HistorySectionUi(
            "CANJES",
            redemptions.map {
                val name = it.redemption_options?.name ?: redeemKindLabel(it.kind)
                val title = if (it.monetary_value > 0) "Canje $name S/%.2f".format(Locale.US, it.monetary_value) else "Canje $name"
                HistoryEntryUi(
                    isRedeem = true, code = "redeem", title = title,
                    meta = MakiTime.dayMonth(it.created_at), statusText = null, statusKind = null,
                    note = null, amount = "−${groupInt(it.points_spent)} pts",
                )
            },
        )
    }
    return HistoryUi(sections)
}

private fun DetectionDto.toHistoryEntry(): HistoryEntryUi {
    val title = itemsSummaryFromDetection(detection_items) + " detectados"
    val meta = "+${groupInt(total_points)} pts · S/%.2f".format(Locale.US, total_value)
    val (statusText, statusKind) = when (status) {
        "collected" -> "Recojo completado" to "done"
        "matched", "assigned" -> "Recojo asignado" to "assigned"
        else -> "Detectado" to null
    }
    val firstCode = detection_items.firstOrNull()?.materials?.code ?: "PET"
    return HistoryEntryUi(
        isRedeem = false, code = firstCode, title = title, meta = meta,
        statusText = statusText, statusKind = statusKind, note = null, amount = null,
    )
}

private fun DetectResultDto.toUi(streakDays: Int, prices: List<MaterialPriceDto>): DetectionResultUi {
    val top = items.firstOrNull()
    val price = prices.firstOrNull { it.code == top?.code }?.takeIf { it.price_per_kg > 0 }
    val pctStr = price?.let { (if (it.pct_change >= 0) "+" else "") + "%.0f%%".format(Locale.US, it.pct_change) }
    val verb = if ((price?.pct_change ?: 0.0) >= 0) "subió" else "bajó"
    val itemsLabel = items.joinToString(", ") { "${it.quantity} ${it.name.ifBlank { it.code }}" }
    return DetectionResultUi(
        items = items.map { it.toUi() },
        totalPoints = total_points,
        totalValue = "S/%.2f".format(Locale.US, total_value),
        streakText = if (streakDays > 0) "Racha de $streakDays días" else "Detección verificada",
        marketPrice = price?.let { "S/%.2f/kg".format(Locale.US, it.price_per_kg) },
        marketPct = pctStr,
        marketNote = price?.let { "El ${it.name} $verb ${"%.0f".format(Locale.US, kotlin.math.abs(it.pct_change))}% esta semana." },
        shareText = "♻️ Acabo de reciclar $itemsLabel con MAKI y gané +$total_points Eco-Puntos. ¡Únete y recicla conmigo!",
    )
}

private fun DetectedItemDto.toUi(): DetectedItemUi {
    val meta = "Confianza ${(confidence * 100).toInt()}%" +
        if (quality > 0) " · Calidad %.1f".format(Locale.US, quality) else ""
    return DetectedItemUi(
        code = code,
        name = name.ifBlank { code },
        qty = "x$quantity",
        meta = meta,
        note = if (quality >= 0.8) "“Limpio y en buen estado”" else null,
        detectedQty = quantity,
    )
}

private fun com.example.maki.data.ChallengeDto.toUi(): ChallengeUi {
    val mine = user_challenges.firstOrNull()
    val prog = mine?.progress ?: 0.0
    val done = mine?.is_completed == true
    val target = target_value.coerceAtLeast(1.0)
    val unit = target_unit ?: "unidades"
    return ChallengeUi(
        title = "Reto: $name",
        rewardText = "+$reward_points pts",
        progress = if (done) 1f else (prog / target).toFloat().coerceIn(0f, 1f),
        progressText = if (done) "¡Completado! +$reward_points pts acreditados"
        else "${trimNum(prog)} / ${trimNum(target)} $unit · +$reward_points pts al completar",
        completed = done,
    )
}

private fun CollectionCenterDto.toUi(): CenterUi = CenterUi(
    name = name,
    address = address_line ?: "Dirección no disponible",
    open = is_open,
    materials = center_materials.mapNotNull { it.materials?.name }.distinct().joinToString(", ").ifBlank { "Varios materiales" },
    lat = latitude,
    lng = longitude,
    phone = phone,
)

private fun NotificationDto.toUi(highlighted: Boolean) = NotifUi(
    kind = kind,
    title = title,
    body = body ?: "",
    time = MakiTime.relative(created_at),
    unread = !is_read,
    highlighted = highlighted,
    id = id,
)

// ---- Small format helpers ----

/** Two-letter avatar initials, e.g. "Fam. Nureña" -> "FN". */
private fun initialsOf(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "EH" }

/** Single-letter initial from the most meaningful (last) word, e.g. "Fam. Quispe" -> "Q". */
internal fun surnameInitial(name: String): String =
    name.split(" ").filter { it.isNotBlank() }
        .lastOrNull()?.firstOrNull()?.uppercase() ?: "?"

internal fun groupInt(n: Int): String = "%,d".format(Locale.US, n)
private fun trimNum(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

private fun redeemKindLabel(kind: String): String = when (kind) {
    "cash" -> "Yape / Plin"
    "donation" -> "Donación"
    "store_discount" -> "Descuento Tiendas"
    else -> "Canje"
}

private fun pickupLabel(code: String?, name: String?): String = when (code) {
    "PET" -> "botellas PET"
    "ALU" -> "latas de aluminio"
    "VID" -> "botellas de vidrio"
    "CAR" -> "cajas de cartón"
    "PIL" -> "pilas"
    else -> name ?: "material"
}

internal fun shortMaterial(code: String?): String = when (code) {
    "PET" -> "PET"
    "ALU" -> "latas"
    "VID" -> "vidrios"
    "CAR" -> "cartones"
    "PIL" -> "pilas"
    else -> "items"
}

internal fun itemsSummary(items: List<PickupItemDto>): String =
    items.takeIf { it.isNotEmpty() }
        ?.joinToString(" + ") { "${it.confirmed_quantity ?: it.reported_quantity} ${shortMaterial(it.materials?.code)}" }
        ?: "Residuos"

private fun itemsSummaryFromDetection(items: List<com.example.maki.data.DetectionItemDto>): String =
    items.takeIf { it.isNotEmpty() }
        ?.joinToString(" + ") { "${it.quantity} ${shortMaterial(it.materials?.code)}" }
        ?: "Residuos"

/** yyyy-MM-dd for the chosen window label: "Hoy" -> today, anything else -> tomorrow. */
private fun scheduledDateFor(windowLabel: String): String {
    val cal = Calendar.getInstance()
    if (!windowLabel.startsWith("Hoy", ignoreCase = true)) cal.add(Calendar.DAY_OF_MONTH, 1)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

private fun vehicleLabel(v: String?): String = when (v) {
    "moto" -> "Moto"
    "bici", "bicicleta" -> "Bici"
    "auto" -> "Auto"
    "a_pie" -> "A pie"
    else -> "Vehículo"
}

private fun trackingHeader(status: String): String = when (status) {
    "requested" -> "Buscando Eco-Rider…"
    "assigned" -> "Eco-Rider asignado"
    "en_route", "arriving" -> "Tu Eco-Rider está llegando"
    "collecting" -> "Recolectando tus residuos"
    "completed" -> "Recojo completado"
    "cancelled" -> "Recojo cancelado"
    else -> "Tu recojo"
}

private fun trackingMessage(status: String, rider: String): String = when (status) {
    "requested" -> "Buscando un Eco-Rider disponible cerca de ti."
    "assigned", "en_route", "arriving" -> "$rider ya salió. ¡Ten listos tus residuos!"
    "collecting" -> "$rider está recogiendo tus residuos."
    "completed" -> "$rider recogió tus residuos. ¡Gracias por reciclar!"
    "cancelled" -> "Este recojo fue cancelado."
    else -> "Seguimiento de tu recojo."
}

/** API-safe date formatting (minSdk 24, no java.time). */
object MakiTime {
    private val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val monthsEs = arrayOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    private val weekdaysEs = arrayOf("DOMINGO", "LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO")

    private fun parse(ts: String): Date? = try { parser.parse(ts.take(19)) } catch (e: Exception) { null }

    /** Epoch millis for an ISO stamp, or null when it cannot be parsed (date filters). */
    fun millis(ts: String): Long? = parse(ts)?.time

    fun dayMonth(ts: String): String {
        val d = parse(ts) ?: return ""
        val c = Calendar.getInstance().apply { time = d }
        return "${c.get(Calendar.DAY_OF_MONTH)} ${monthsEs[c.get(Calendar.MONTH)]}"
    }

    fun dayLabel(ts: String): String {
        val d = parse(ts) ?: return ""
        return when (daysAgo(d)) {
            0 -> "HOY"
            1 -> "AYER"
            else -> weekdaysEs[Calendar.getInstance().apply { time = d }.get(Calendar.DAY_OF_WEEK) - 1]
        }
    }

    fun relative(ts: String): String {
        val d = parse(ts) ?: return ""
        val diffMin = (System.currentTimeMillis() - d.time) / 60000
        return when {
            diffMin < 1 -> "Ahora"
            diffMin < 60 -> "Hace ${diffMin}m"
            daysAgo(d) == 0 -> "Hace ${diffMin / 60}h"
            daysAgo(d) == 1 -> "Ayer"
            else -> dayMonth(ts)
        }
    }

    private fun daysAgo(d: Date): Int {
        val day = Calendar.getInstance().apply { time = d; clearTime() }
        val today = Calendar.getInstance().apply { clearTime() }
        return ((today.timeInMillis - day.timeInMillis) / 86_400_000L).toInt()
    }

    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
}
