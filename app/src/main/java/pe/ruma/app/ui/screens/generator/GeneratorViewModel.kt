package pe.ruma.app.ui.screens.generator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pe.ruma.app.data.CollectionCenterDto
import pe.ruma.app.data.SavedPileRowDto
import pe.ruma.app.data.DetectResultDto
import pe.ruma.app.data.DetectedItemDto
import pe.ruma.app.data.DetectionDto
import pe.ruma.app.data.GeneratorProfileDto
import pe.ruma.app.data.LeaderboardEntryDto
import pe.ruma.app.data.RumaApi
import pe.ruma.app.data.MaterialPriceDto
import pe.ruma.app.data.RumaRepository
import pe.ruma.app.data.RumaPrefs
import pe.ruma.app.data.WeekProgress
import pe.ruma.app.data.monthDays
import pe.ruma.app.data.weekProgress
import kotlinx.coroutines.CancellationException
import pe.ruma.app.data.NotificationDto
import pe.ruma.app.data.PickupDto
import pe.ruma.app.data.PickupItemDto
import pe.ruma.app.data.RedemptionDto
import pe.ruma.app.data.RedemptionOptionDto
import pe.ruma.app.data.RoutePlanner
import pe.ruma.app.data.StreetRouter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * UI state for the generator (Eco-Hero) home + profile header. Neutral until the
 * profile loads: these used to be a sample family ("Fam. Nureña", 4,250 pts) that
 * any other account saw as its own while the network was slow or the load failed.
 */
data class GeneratorUiState(
    val displayName: String = "",
    val initials: String = "",
    val points: Int = 0,
    val streakDays: Int = 0,
    val cashEarned: String = "S/0.00",
    val co2: String = "0.0 kg",
    val recycled: String = "0 kg",
    val water: String = "0.0 m³",
    val moneyValue: String = "S/0.00",
    /** Booked on pickup, spendable once the acopio weighs it (settle_delivery). */
    val pendingPoints: Int = 0,
)

// ---- Per-screen UI models ----

/** The pickup in flight, if any. Neutral until loaded. */
data class HomeExtrasUi(
    /** Null when nothing is in flight: the "Eco-Rider en camino" card hides. */
    val activeTitle: String? = null,
    val activeLine: String = "",
)

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
    /** Catalogue code ("MUNI_ARBITRIOS"…): what the wallet styles the card by. */
    val code: String = "",
    /** Who honours it ("Municipalidad"), shown as a partner badge. */
    val partner: String = "",
    val title: String,
    val rule: String,
    val suggested: Boolean,
    val optionId: String = "",
    val cost: Int = 0,
    val monetaryValue: Double = 0.0,
    val affordable: Boolean = true,
    /** Why it can't be redeemed besides the balance ("Ya usaste el 50 % de este año"). */
    val blocked: String? = null,
)
data class RedeemHistoryUi(val title: String, val date: String, val amount: String)

data class RankingsUi(
    val boardTitle: String,
    /** "Semanal · 12 Oct - 18 Oct": which board this actually is. */
    val periodLabel: String = "",
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
    /** Null = the family has no pickup at all (the screen shows an empty state). */
    val pickupId: String? = null,
    /** The family may call it off until the rider reaches the door (the DB enforces it too). */
    val canCancel: Boolean = false,
    val riderPhone: String? = null,
    // Live map: where the family is, where the rider is right now, and how far.
    val homeLat: Double? = null,
    val homeLng: Double? = null,
    val riderLat: Double? = null,
    val riderLng: Double? = null,
    val distanceText: String? = null,
    /** Street path from the rider to the door, when the router answered. */
    val path: List<RoutePlanner.LatLng> = emptyList(),
    /** Set when the rider's last reported position is old enough to mislead. */
    val staleText: String? = null,
    val status: String = "",
    val code: String = "",
    /** "Hoy 3:00 - 6:00 pm", re-read against scheduled_date (the label was written on the day it was asked). */
    val windowText: String = "",
    val addressLine: String = "",
    val riderName: String? = null,
    /** "Moto · ★ 4.8" */
    val riderMeta: String = "",
    val items: List<TrackingItemUi> = emptyList(),
    /** Pedido → Recogido, each with the time it happened once reached. */
    val flowSteps: List<Pair<String, String?>> = emptyList(),
    val flowCurrent: Int = 0,
    val pointsAwarded: Int = 0,
    /** Set once the acopio weighed the batch: the points actually credited. */
    val pointsFinal: Int? = null,
    val estPoints: Int = 0,
    val dateText: String = "",
    val cancelReason: String? = null,
    val timeline: List<Pair<String, String>> = emptyList(),
) {
    val hasMap: Boolean get() = homeLat != null && homeLng != null
    /** Asked for or taken, rider not yet on the way: no map, the calm view. */
    val waiting: Boolean get() = status == "requested" || status == "assigned"
    /** Completed, cancelled or failed: the detail view. */
    val closed: Boolean get() = status.isNotEmpty() && status !in ACTIVE
}

data class OtherPickupUi(val id: String, val title: String, val sub: String, val status: String, val live: Boolean)

data class TrackingItemUi(val name: String, val code: String, val reported: Int, val confirmed: Int?)

data class CenterUi(
    val name: String,
    val address: String,
    val open: Boolean,
    val materials: String,   // "PET, Aluminio, Cartón"
    val lat: Double?,
    val lng: Double?,
    val phone: String?,
    /** From the family's home, when both have coordinates. */
    val km: Double? = null,
)

/**
 * One row of "Precios de hoy": what the acopio pays per kilo, and what that kilo
 * leaves in your wallet as Eco-Puntos.
 */
data class PriceRowUi(
    val code: String,
    val name: String,
    val pricePerKg: String,
    val pointsPerKg: String,
    val yours: String,
    val changePct: String? = null,
    val rising: Boolean = true,
)

/** Eco-Puntos per sol: the Yape/Plin option in `redemption_options` (1 000 pts = S/1.00). */
const val POINTS_PER_SOL = 1000.0

data class HistoryUi(val sections: List<HistorySectionUi>)
data class HistorySectionUi(val label: String, val entries: List<HistoryEntryUi>)
data class HistoryEntryUi(
    val isRedeem: Boolean,
    val code: String,            // material code or "redeem"
    val title: String,
    val meta: String,            // "+92 pts · S/0.34" or redeem date
    val statusText: String?,
    val statusKind: String?,     // "done" | "assigned" | "cancelled" | null
    val note: String?,
    val amount: String?,
    /** Set on pickup rows; an in-flight one opens the live tracking map. */
    val pickupId: String? = null,
)

data class NotifUi(
    val kind: String,
    val title: String,
    val body: String,
    val time: String,
    val unread: Boolean,
    val highlighted: Boolean,
    val id: Long = 0,
    /** About a pickup: tapping it opens the live map. */
    val aboutPickup: Boolean = false,
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
    /** Units per brand read on the labels, for the rows on the result; empty when none was legible. */
    val brandRows: Map<String, Int> = emptyMap(),
)

/** The Eco-Rider dispatch just confirmed for a pickup. */
/** [later] is the window when the pickup is for another day: the rider's minutes away now are not their visit. */
data class DispatchUi(val riderName: String, val etaMinutes: Int?, val searching: Boolean, val later: String? = null)

class GeneratorViewModel : ViewModel() {

    var weeklyGoal by mutableStateOf(RumaPrefs.readWeeklyGoal(RumaApi.session?.userId))
        private set
    var week by mutableStateOf<WeekProgress?>(null)
        private set
    var weekError by mutableStateOf<String?>(null)
        private set
    var homeLoading by mutableStateOf(true)
        private set
    var homeError by mutableStateOf<String?>(null)
        private set
    private var homeInFlight = false
    /** Points credited since the last time this phone showed the balance; the home banner. */
    var creditedGain by mutableStateOf(0)
        private set

    fun creditedSeen() { creditedGain = 0 }
    private var scanDates: List<Date>? = null
    /** Days this month with a scan, for "tu ruma" on the home screen; null until the dates load. */
    var rumaDays by mutableStateOf<Int?>(null)
        private set

    fun chooseWeeklyGoal(days: Int) {
        val userId = RumaApi.session?.userId ?: return
        if (days !in 1..7) return
        RumaPrefs.writeWeeklyGoal(userId, days)
        weeklyGoal = days
        week = scanDates?.let { weekProgress(it, days) }
    }

    var state by mutableStateOf(GeneratorUiState())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var home by mutableStateOf(HomeExtrasUi())
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
    var lastScanPhoto by mutableStateOf<ScanPhoto?>(null)
        private set
    /** Live market prices per material (drives the result card + price alerts). */
    /** Today's market price per material next to the points it pays. */
    var priceRows by mutableStateOf<List<PriceRowUi>>(emptyList())
        private set

    var prices by mutableStateOf<List<MaterialPriceDto>>(emptyList())
        private set
    /** Collection centers for the acopio map. */
    var centers by mutableStateOf<List<CenterUi>?>(null)
        private set
    /** Active reto with the user's progress, shown on the result screen. */
    var challenge by mutableStateOf<ChallengeUi?>(null)
        private set
    /** The detection just saved this session; non-null means "pedir recojo" links it. */
    private var lastDetectionId by mutableStateOf<String?>(null)
    /**
     * "Tu ruma guardada": every scan with no pickup yet (last 30 days, rpc/saved_pile), so a family can
     * scan 3 bottles today and 4 tomorrow and ask for one pickup of 7. Re-read with Home.
     */
    var savedPile by mutableStateOf<List<SavedPileRowDto>>(emptyList())
        private set
    /** What the AI counted on the scan just saved, to carry the result screen's corrections into the pile. */
    private var lastAiQty: Map<String, Int> = emptyMap()
    /** A scan with no pickup yet: the home offers "Pide tu recojo". */
    val hasUnpickedScan: Boolean get() = lastDetectionId != null || savedPile.isNotEmpty()
    /** "7 botellas PET y 2 latas de aluminio guardadas", or null with nothing saved. */
    val savedSummary: String? get() = savedQty().takeIf { it.isNotEmpty() }?.let { qty ->
        val names = savedPile.associate { it.code to it.name }
        qty.entries.sortedByDescending { it.value }.map { (c, n) -> "$n " + pickupLabel(c, names[c]).let { if (n == 1) singular(it) else it } }
            .let { if (it.size > 1) it.dropLast(1).joinToString(", ") + " y " + it.last() else it.single() } +
            if (qty.size == 1 && qty.values.single() == 1) " guardada" else " guardadas"
    }

    /** "botellas PET" → "botella PET": every pickupLabel is a feminine plural on its first word. */
    private fun singular(label: String) = label.replaceFirst(Regex("^(\\p{L}+)s\\b"), "$1")

    /** The pile per material code, with the result screen's corrections to the last scan applied. */
    private fun savedQty(): Map<String, Int> {
        val pile = savedPile.associate { it.code to it.quantity }.toMutableMap()
        if (lastDetectionId != null) detectedQty.forEach { (c, n) -> pile[c] = (pile[c] ?: 0) + n - (lastAiQty[c] ?: 0) }
        return pile.filterValues { it > 0 }
    }
    /** min_points of the cheapest active cash option, for the home's "Ya puedes cobrar"; null until read. */
    var cashMin by mutableStateOf<Int?>(null)
        private set

    /**
     * Quantities the user corrected on the result screen, per material code. The AI
     * counts what it can see; the person knows what is actually in the bag, and the
     * rider still confirms the physical count, so an edit here is a declaration —
     * never a payout.
     */
    var detectedQty by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** Units per brand per material code, as corrected on the result; the sum is [detectedQty]. */
    var detectedBrands by mutableStateOf<Map<String, Map<String, Int>>>(emptyMap())
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
    var hasUnread by mutableStateOf(false)
        private set

    fun consumeMessage() { message = null }
    /** The Root shows the load failure once, then clears it. */
    fun consumeError() { error = null }
    /** Surfaces a UI-originated info message in the shared snackbar. */
    fun notify(text: String) { message = text }

    init {
        // Paint the cached profile immediately, then refresh from the network.
        RumaRepository.cachedProfile()?.let { state = it.toUiState(); loading = false }
        refresh()
    }

    private suspend fun ensureSignedIn() {
        // The demo account is a dev shortcut only: a tester who lost the session must not
        // silently scan and book pickups as the shared generador@ruma.test.
        if (!RumaApi.isSignedIn) { if (pe.ruma.app.BuildConfig.DEBUG) RumaRepository.signInAsTestGenerator() else error("Tu sesión terminó. Cierra sesión y vuelve a entrar.") }
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
                RumaRepository.loadGeneratorProfile()?.let { state = it.toUiState() }
                runCatching { prices = RumaRepository.loadPrices() }  // best-effort, drives price cards
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    /**
     * The home screen's live data. Re-read every time Home shows: it is how the
     * family learns a rider was assigned, arrived, or finished while they were away.
     */
    fun loadHome() {
        if (homeInFlight) return
        homeInFlight = true
        homeError = null
        viewModelScope.launch {
            try {
                ensureSignedIn()
                weeklyGoal = RumaPrefs.readWeeklyGoal(RumaApi.session?.userId)
                val profile = RumaRepository.loadGeneratorProfile() ?: error("No pudimos cargar tu inicio.")
                state = profile.toUiState()
                RumaPrefs.takeBalanceGain(RumaApi.session?.userId, profile.points_balance).takeIf { it > 0 }?.let { creditedGain = it }
                try {
                    scanDates = RumaRepository.loadScanDates()
                    week = weekProgress(scanDates.orEmpty(), weeklyGoal)
                    rumaDays = monthDays(scanDates.orEmpty())
                    weekError = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    scanDates = null
                    week = null
                    rumaDays = null
                    weekError = "No pudimos cargar tu semana. Reintenta para ver tu progreso."
                }
                runCatching { savedPile = RumaRepository.loadSavedPile() }
                runCatching { hasUnread = RumaRepository.loadNotifications().any { !it.is_read } }
                runCatching { cashMin = RumaRepository.loadRedemptionOptions().filter { it.kind == "cash" && it.min_points > 0 }.minOfOrNull { it.min_points } }
                runCatching {
                    val active = RumaRepository.loadTrackedPickup()?.takeIf { it.status in ACTIVE }
                    home = HomeExtrasUi(
                        activeTitle = active?.let { homeActiveTitle(it.status, it.rider?.display_name) },
                        // Keep Android's live-map link instead of the stale dispatch ETA.
                        // The map only exists once the rider is on the way; before that the tap opens the calm view.
                        activeLine = active?.let { "${itemsSummary(it.pickup_items)} · " + if (it.status in setOf("requested", "assigned")) "Toca para ver tu recojo" else "Toca para verlo en el mapa" }.orEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                homeError = e.message ?: "No pudimos cargar tu inicio."
            } finally {
                homeLoading = false
                homeInFlight = false
            }
        }
    }

    /** Re-read on every visit: a balance cached from before the weighing reads as points that never arrived. */
    fun loadWallet() = load {
        RumaRepository.loadGeneratorProfile()?.let { state = it.toUiState() }
        fetchWallet()
    }

    private suspend fun fetchWallet() {
        val options = RumaRepository.loadRedemptionOptions()
        val redemptions = RumaRepository.loadRedemptions()
        wallet = buildWallet(state.points, options, redemptions)
    }

    /** Re-read on every visit: the board is recomputed each minute from real pickups. */
    fun loadRankings() = load {
        // No open period is an answer, not a wait: the screen says so instead of spinning.
        val period = RumaRepository.loadOpenPeriod()
            ?: run { rankings = RankingsUi("Ranking de tu cuadra", entries = emptyList(), rewards = emptyList()); return@load }
        val entries = RumaRepository.loadLeaderboardEntries(period.id)
        val rewards = RumaRepository.loadRankingRewards(period.scope, period.period_type)
        val me = RumaApi.session?.userId
        val topPoints = entries.firstOrNull()?.points ?: 0
        rankings = RankingsUi(
            boardTitle = period.block_label?.let { if (period.scope == "block") "Jr. $it" else "Ranking de $it" } ?: "Mi Ranking",
            periodLabel = listOfNotNull(
                when (period.period_type) {
                    "weekly" -> "Semanal"
                    "monthly" -> "Mensual"
                    else -> period.period_type.replaceFirstChar { it.uppercase() }
                }.takeIf { it.isNotBlank() },
                listOfNotNull(period.period_start, period.period_end)
                    .takeIf { it.size == 2 }
                    ?.let { "${RumaTime.dayMonth(it[0])} - ${RumaTime.dayMonth(it[1])}" },
            ).joinToString(" · "),
            entries = entries.map { it.toUi(me, topPoints) },
            rewards = rewards.map { RewardUi(it.rank, it.description ?: "x${trimNum(it.reward_multiplier)} puntos") },
        )
    }

    fun loadPickupForm() = load {
        if (pickupForm != null) return@load
        fetchPickupForm()
    }

    private suspend fun fetchPickupForm() {
        val address = RumaRepository.loadPrimaryAddress()
        val materials = RumaRepository.loadMaterials()
        // The whole saved pile (every scan with no pickup yet), corrections to the last scan included.
        savedPile = RumaRepository.loadSavedPile()
        val qtyByCode = savedQty()
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

    /**
     * Which pickup the tracking map is following. Null means "whichever is active
     * now", the right answer coming from Home or straight after asking for one;
     * a history row sets its own id so the map — and Cancelar — stay on it.
     */
    private var trackedId by mutableStateOf<String?>(null)

    fun trackPickup(pickupId: String?) {
        if (pickupId != trackedId) { trackedId = pickupId; tracking = null }
    }

    /** Always re-reads: the screen is the entry point to "where is my rider now". */
    fun loadTracking() = load { fetchTracking() }

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

    // Last street path, so the 10 s poll only asks the router again once the rider moved.
    private var road: StreetRouter.Route? = null
    private var roadFrom: RoutePlanner.LatLng? = null
    private var roadPickupId: String? = null

    private suspend fun fetchTracking() {
        val want = trackedId
        val pickup = RumaRepository.loadTrackedPickup(want)
        // Opened another pickup while this read was in flight: its answer is stale and
        // publishing it would point the map — and Cancelar — at the pickup left behind.
        if (want != trackedId) return
        if (pickup == null) { tracking = NoPickupTracking; return }

        val live = pickup.status in LIVE
        val rider = pickup.rider_id?.let { runCatching { RumaRepository.loadRiderProfile(it) }.getOrNull() }
        val riderName = pickup.rider?.display_name ?: "Eco-Rider"
        val ratingTxt = rider?.rating?.let { " · ★ ${"%.1f".format(Locale.US, it)}" } ?: ""

        val home = latLngOf(pickup.latitude ?: pickup.address?.latitude, pickup.longitude ?: pickup.address?.longitude)
        val riderPos = pickup.rider_id?.takeIf { live }
            ?.let { runCatching { RumaRepository.loadRiderPosition(it) }.getOrNull() }
        val riderAt = latLngOf(riderPos?.current_latitude, riderPos?.current_longitude)

        // Road distance/time when the router answers; the straight line otherwise.
        val approaching = live && pickup.status != "collecting"
        val street = if (approaching && home != null && riderAt != null) {
            streetRoute(pickup.id, riderAt, home, rider?.vehicle_type)
        } else null
        val km = street?.distanceKm ?: if (home != null && riderAt != null) RoutePlanner.distanceKm(riderAt, home) else null
        val eta = street?.minutes ?: km?.let { RoutePlanner.etaMinutes(it) } ?: pickup.eta_minutes
        val ageMin = riderPos?.last_location_at?.let { RumaTime.millis(it) }
            ?.let { (System.currentTimeMillis() - it) / 60_000 }
        // 15 min without a heartbeat (it beats every 15 s) is the rider's app closed:
        // "~1 min" off a 3-hour-old position was a promise nobody could keep.
        val signalLost = approaching && ageMin != null && ageMin >= 15

        tracking = TrackingUi(
            headerTitle = trackingHeader(pickup.status),
            riderLine = if (pickup.rider_id == null) "Asignando al Eco-Rider más cercano"
            else "$riderName · ${vehicleLabel(rider?.vehicle_type)}$ratingTxt",
            etaText = when {
                // A closed pickup showing its time window read as "still to come".
                pickup.status == "completed" -> "Completado"
                pickup.status == "cancelled" || pickup.status == "failed" -> "Cancelado"
                !live -> pickup.window_label.orEmpty()
                pickup.status == "collecting" -> "En tu puerta"
                // A stale fix has no honest ETA; a word reads better than the hero's "—".
                signalLost -> if (pickup.status == "arriving") "Llegando" else "En camino"
                eta != null && eta > 0 -> "~$eta min"
                else -> pickup.window_label ?: "En camino"
            },
            itemsText = itemsSummary(pickup.pickup_items),
            statusMessage = if (signalLost) {
                "$riderName no está compartiendo su ubicación ahora. Lo verás moverse apenas abra su app."
            } else trackingMessage(pickup.status, riderName),
            pickupId = pickup.id,
            canCancel = pickup.status in CANCELLABLE,
            riderPhone = pickup.rider?.phone?.takeIf { live && it.isNotBlank() },
            homeLat = home?.lat,
            homeLng = home?.lng,
            riderLat = riderAt?.lat,
            riderLng = riderAt?.lng,
            distanceText = km?.takeIf { approaching }?.let { "A ${RoutePlanner.distanceLabel(it)}" },
            path = street?.path.orEmpty(),
            staleText = ageMin?.takeIf { approaching && it >= STALE_MINUTES }
                ?.let { "Ubicación de hace " + if (it < 60) "$it min" else "${it / 60} h" },
            status = pickup.status,
            code = pickup.code.orEmpty(),
            windowText = windowText(pickup.window_label, pickup.scheduled_date),
            addressLine = pickup.address?.address_line.orEmpty(),
            riderName = pickup.rider?.display_name,
            riderMeta = listOfNotNull(
                rider?.vehicle_type?.let { vehicleLabel(it) },
                rider?.rating?.let { "★ ${"%.1f".format(Locale.US, it)}" },
            ).joinToString(" · "),
            items = pickup.pickup_items.map {
                TrackingItemUi(it.materials?.name ?: "Material", it.materials?.code.orEmpty(), it.reported_quantity, it.confirmed_quantity)
            },
            flowSteps = pickup.flowSteps(),
            flowCurrent = flowCurrent(pickup.status),
            pointsAwarded = pickup.generator_points_awarded ?: 0,
            pointsFinal = pickup.generator_points_final,
            estPoints = pickup.pickup_items.sumOf { it.points },
            dateText = RumaTime.dayMonth(pickup.completed_at ?: pickup.cancelled_at ?: pickup.created_at.orEmpty()),
            cancelReason = pickup.cancel_reason,
            timeline = pickup.timeline(),
        )
    }

    /** "Mis recojos": every pickup, newest first. Null until loaded. */
    var pickupList by mutableStateOf<List<OtherPickupUi>?>(null)
        private set

    fun loadPickupList() = load {
        pickupList = RumaRepository.loadPickups(30)
                .filter { it.id != null }.map {
                    OtherPickupUi(
                        id = it.id!!,
                        title = "Recojo · ${itemsSummary(it.pickup_items)}",
                        sub = if (it.status in ACTIVE) windowText(it.window_label, it.scheduled_date)
                        else RumaTime.dayMonth(it.completed_at ?: it.cancelled_at ?: it.created_at.orEmpty()),
                        status = when (it.status) { "completed" -> "Completado"; "cancelled" -> "Cancelado"; "requested" -> "Buscando Eco-Rider"; "failed" -> "No se pudo"; else -> "En curso" },
                        live = it.status in ACTIVE,
                    )
                }
    }

    /** Re-asks the router only when the rider moved [REROUTE_KM]; the poll runs every 10 s. */
    private suspend fun streetRoute(
        pickupId: String?,
        from: RoutePlanner.LatLng,
        to: RoutePlanner.LatLng,
        vehicle: String?,
    ): StreetRouter.Route? {
        val last = roadFrom
        if (pickupId == roadPickupId && last != null && RoutePlanner.distanceKm(last, from) < REROUTE_KM) return road
        return StreetRouter.route(listOf(from, to), vehicle)
            ?.also { road = it; roadFrom = from; roadPickupId = pickupId }
    }

    /** Calls off the pickup being tracked; the rider (if any) is notified by the DB. */
    fun cancelActivePickup() {
        val id = tracking?.pickupId
        if (id == null) {
            // Antes esto era `?: return`: el boton no hacia nada y no decia nada.
            message = "No hay un recojo que cancelar."
            return
        }
        runAction(success = "Recojo cancelado.") {
            RumaRepository.cancelPickup(id)
            history = null
            // Fijar el recojo cancelado. Sin esto la siguiente lectura devuelve el
            // activo mas reciente, que con dos recojos en vuelo es OTRO: la pantalla
            // volvia a pintar "Buscando un Eco-Rider" y parecia que no se habia
            // cancelado nada.
            trackedId = id
            fetchTracking()
        }
    }

    /** Saves the primary address (creating it on first use) and refreshes the form. */
    fun saveAddress(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) { message = "Escribe tu dirección."; return }
        runAction(success = "Dirección actualizada.") {
            RumaRepository.savePrimaryAddress(pickupForm?.addressId, trimmed)
            fetchPickupForm()
        }
    }

    /** Always fetched fresh: progress moves server-side whenever a pickup settles. */
    fun loadChallenge() = load {
        val all = RumaRepository.loadChallenges().map { it.toUi() }
        challenge = all.firstOrNull { !it.completed } ?: all.firstOrNull()
    }

    /**
     * Nearest first, measured from where the phone is — else from the family's home
     * ("cerca" used to list Lima before Cartavio).
     */
    fun loadCenters(here: RoutePlanner.LatLng? = null) = load {
        if (centers != null && here == null) return@load
        val home = here ?: RumaRepository.loadPrimaryAddress()?.let { latLngOf(it.latitude, it.longitude) }
        centers = RumaRepository.loadCenters()
            .map { c -> c.toUi(km = home?.let { h -> latLngOf(c.latitude, c.longitude)?.let { RoutePlanner.distanceKm(h, it) } }) }
            .sortedBy { it.km ?: Double.MAX_VALUE }
        // What the market pays per kilo next to what that kilo leaves in your wallet:
        // the two numbers the family actually compares before separating a bag.
        val materials = runCatching { RumaRepository.loadMaterials() }.getOrDefault(emptyList())
        val quotes = runCatching { RumaRepository.loadPrices() }.getOrDefault(prices).also { prices = it }
        priceRows = materials.mapNotNull { m ->
            val quote = quotes.firstOrNull { it.code == m.code }
            val pts = m.points_per_kg.takeIf { it > 0 } ?: return@mapNotNull null
            PriceRowUi(
                code = m.code,
                name = m.name,
                pricePerKg = quote?.price_per_kg?.takeIf { it > 0 }
                    ?.let { "S/%.2f/kg".format(Locale.US, it) } ?: "Sin precio",
                pointsPerKg = "${groupInt(pts.toInt())} pts/kg",
                yours = "≈ S/%.2f para ti".format(Locale.US, pts / POINTS_PER_SOL),
                changePct = quote?.pct_change?.takeIf { kotlin.math.abs(it) >= 0.1 }
                    ?.let { "${if (it > 0) "+" else ""}${"%.0f".format(Locale.US, it)}%" },
                rising = (quote?.pct_change ?: 0.0) >= 0,
            )
        }
    }

    /** Always re-read: the rider moves pickups along while the family is elsewhere in the app. */
    fun loadHistory() = load {
        val detections = RumaRepository.loadDetections()
        val redemptions = RumaRepository.loadRedemptions()
        val pickups = runCatching { RumaRepository.loadPickups() }.getOrDefault(emptyList())
        history = buildHistory(detections, redemptions, pickups)
    }

    /** Always re-read: the DB triggers post new notices at any time; the cache hid them. */
    fun loadNotifications() = load {
        val list = RumaRepository.loadNotifications()
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
    fun redeem(option: RedeemOptionUi, onDone: () -> Unit = {}) {
        if (!option.affordable) { message = option.blocked ?: "Saldo insuficiente para este canje."; return }
        runAction(success = "¡Canje realizado con éxito!", onSuccess = onDone) {
            RumaRepository.createRedemption(
                optionId = option.optionId.ifBlank { null },
                kind = option.kind,
                pointsSpent = option.cost,
                monetaryValue = option.monetaryValue,
            )
            RumaRepository.loadGeneratorProfile()?.let { state = it.toUiState() }
            fetchWallet()
        }
    }

    /**
     * Creates the pickup, then dispatches it to the nearest available Eco-Rider —
     * the taxi-app move: the family asks once and someone is already on the way,
     * instead of the request sitting on a board. [onDone] navigates to tracking.
     */
    /**
     * Where the phone is while the pickup form is open — the rider comes here. The
     * typed address often had no coordinates, so dispatch could not measure anything.
     */
    var pickupHere by mutableStateOf<RoutePlanner.LatLng?>(null)
        private set
    /** Street name for [pickupHere] (reverse geocoded), or null when there is no geocoder. */
    var pickupHereLabel by mutableStateOf<String?>(null)
        private set

    fun setPickupLocation(at: RoutePlanner.LatLng?, label: String?) {
        pickupHere = at
        pickupHereLabel = label
    }

    fun createPickup(selections: List<Pair<String, Int>>, windowLabel: String, onDone: () -> Unit) {
        if (selections.none { it.second > 0 }) { message = "Agrega al menos un material al recojo."; return }
        val form = pickupForm
        val fromScans = lastDetectionId != null || savedPile.isNotEmpty()
        val here = pickupHere
        runAction(success = "¡Recojo solicitado!", onSuccess = onDone) {
            val pickupId = if (fromScans) {
                // Link every saved scan (the whole pile) so the rider's count is compared with all of them.
                val id = RumaRepository.createPickupFromDetection(form?.addressId, scheduledDateFor(windowLabel), windowLabel, here)
                lastDetectionId = null
                // The RPC copies the AI's counts; re-state what the user actually has.
                if (id != null) runCatching { RumaRepository.updateReportedQuantities(id, selections) }
                id
            } else {
                RumaRepository.createPickup(
                    addressId = form?.addressId,
                    scheduledDate = scheduledDateFor(windowLabel),
                    windowLabel = windowLabel,
                    items = selections,
                    at = here,
                )
            }
            // Dispatch must never sink the request: if it fails, the pickup still
            // exists as 'requested' and any rider can take it from the board.
            val assigned = pickupId?.let { runCatching { RumaRepository.assignNearestRider(it) }.getOrNull() }
            dispatch = DispatchUi(
                riderName = assigned?.rider_name ?: "Un Eco-Rider",
                etaMinutes = assigned?.eta_minutes,
                searching = assigned == null,
                later = windowLabel.takeUnless { it.startsWith("Hoy", ignoreCase = true) },
            )
            tracking = null  // invalidate caches so Tracking/History reload the new pickup
            history = null
            detectedQty = emptyMap()
            savedPile = emptyList()
            pickupForm = null
        }
    }

    /** Marks every notification read; updates the list + home bell. */
    fun markNotificationsRead() = runAction(success = "Notificaciones marcadas como leídas") {
        if (notifications?.any { it.unread } != true) return@runAction
        RumaRepository.markAllNotificationsRead()
        notifications = notifications?.map { it.copy(unread = false, highlighted = false) }
        hasUnread = false
    }

    /** Marks a single notification read (silent, optimistic). */
    fun markNotificationRead(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            try {
                ensureSignedIn()
                RumaRepository.markNotificationRead(id)
                notifications = notifications?.map { if (it.id == id) it.copy(unread = false, highlighted = false) else it }
                hasUnread = notifications?.any { it.unread } ?: false
            } catch (_: Exception) { /* best-effort */ }
        }
    }

    /** Called by the camera once a scan is verified + persisted server-side. */
    fun onDetectionSaved(result: DetectResultDto, photo: ScanPhoto? = null) {
        lastScanPhoto = photo
        lastDetection = result.toUi(state.streakDays, prices)
        lastDetectionId = result.detection_id   // "pedir recojo" will link this detection
        detectedQty = result.items.associate { it.code to it.quantity }
        lastAiQty = detectedQty
        detectedBrands = result.items.associate { it.code to brandRows(it.brands, it.quantity) }
        pickupForm = null   // the new detection seeds the pickup form…
        history = null      // …and appears in history
        loadHome()          // Count only the server-confirmed saved detection.
    }

    /** Corrects the count for one material on the result screen (never below zero). */
    fun adjustDetectedQty(code: String, delta: Int) {
        val current = detectedQty[code] ?: return
        val next = (current + delta).coerceIn(0, 999)
        if (next == current) return
        detectedQty = detectedQty + (code to next)
        pickupForm = null   // the form must pick the corrected count up
    }

    /** Corrects one brand's count on the result; the material's declared total follows. */
    fun adjustBrandQty(code: String, brand: String, delta: Int) {
        val rows = detectedBrands[code] ?: return
        val current = rows[brand] ?: return
        val next = (current + delta).coerceIn(0, 999)
        if (next == current) return
        val corrected = rows + (brand to next)
        detectedBrands = detectedBrands + (code to corrected)
        detectedQty = detectedQty + (code to corrected.values.sum())
        pickupForm = null   // the form must pick the corrected count up
    }

    /** Dismisses the "un Eco-Rider va en camino" dialog. */
    fun consumeDispatch() { dispatch = null }

    /** Clears the session + cached data so the next launch lands on Login. */
    fun signOut() {
        RumaRepository.signOut()
    }
}

/** Pickup states that are still happening. */
private val ACTIVE = RumaRepository.ACTIVE_STATUSES.split(",").toSet()
/** A rider is attached and moving (or at the door): the map is live. */
private val LIVE = setOf("assigned", "en_route", "arriving", "collecting")
private val CANCELLABLE = setOf("requested", "assigned", "en_route")

/** 40 m: less than that and the old street path still starts at the rider. */
private const val REROUTE_KM = 0.04
/** A heartbeat every 15 s means 3+ min of silence is the app closed, not traffic. */
private const val STALE_MINUTES = 3L

private val NoPickupTracking = TrackingUi(
    headerTitle = "Tu recojo",
    riderLine = "",
    etaText = "",
    itemsText = "",
    statusMessage = "Aún no tienes recojos. Escanea tus residuos y pide uno desde la cámara.",
)

private fun latLngOf(lat: Double?, lng: Double?): RoutePlanner.LatLng? =
    if (lat != null && lng != null) RoutePlanner.LatLng(lat, lng) else null

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
        pendingPoints = points_pending,
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
    val hint = (if (remaining == 0) "Ya puedes canjear S/%.2f" else "Faltan ${groupInt(remaining)} pts para tu próximo canje de S/%.2f").format(Locale.US, cashValue)

    return WalletUi(
        balancePoints = balance,
        balanceCash = "= S/%.2f".format(Locale.US, balance / 1000.0),
        progress = progress,
        nextRedeemHint = hint,
        options = options.mapIndexed { i, o ->
            val mv = if (o.kind == "cash" && o.points_per_unit > 0) o.min_points / o.points_per_unit * o.unit_value else 0.0
            // Arbitrios are a share of the yearly fee: shown in %, never in soles, with the year's cap.
            val pct = o.metadata?.takeIf { it.unit == "percent" && o.points_per_unit > 0 }
            val minPct = pct?.let { o.min_points / o.points_per_unit * it.percent_per_unit } ?: 0.0
            val usedPct = pct?.let { m -> redemptions.filter { it.redemption_options?.code == o.code && it.status !in setOf("cancelled", "failed") && isThisLimaYear(it.created_at) }
                .sumOf { it.quantity } * m.percent_per_unit } ?: 0.0
            val capped = pct != null && pct.max_percent_year > 0 && usedPct + minPct > pct.max_percent_year
            RedeemOptionUi(
                kind = o.kind, code = o.code, partner = o.partner.orEmpty(),
                title = o.name,
                rule = if (pct == null) o.description ?: "" else
                    "${trimNum(minPct)} % de tu arbitrio anual · ${groupInt(o.min_points)} pts\n" +
                        "Hasta ${trimNum(pct.max_percent_year)} % al año · este año llevas ${trimNum(usedPct)} %",
                suggested = i == 0,
                optionId = o.id, cost = o.min_points, monetaryValue = mv, affordable = balance >= o.min_points && !capped,
                blocked = if (capped) "Ya usaste el ${trimNum(usedPct)} % de este año" else null,
            )
        },
        history = redemptions.map {
            val name = it.redemption_options?.name ?: redeemKindLabel(it.kind)
            val title = redeemAmount(it)?.let { a -> "$name · $a" } ?: name
            RedeemHistoryUi(title = title, date = RumaTime.dayMonth(it.created_at), amount = "−${groupInt(it.points_spent)} pts")
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

/**
 * One timeline: scans and pickups interleaved by day, newest first, then the
 * redemptions. Pickups used to be missing entirely — after asking for one, the
 * family had nowhere to find it again.
 */
private fun buildHistory(
    detections: List<DetectionDto>,
    redemptions: List<RedemptionDto>,
    pickups: List<PickupDto>,
): HistoryUi {
    val timeline = detections.map { it.detected_at to it.toHistoryEntry() } +
        // A pickup belongs to the day it was collected, not the day it was asked
        // for: one finished today was filed under "4 SEP".
        pickups.map { (it.completed_at ?: it.created_at).orEmpty() to it.toHistoryEntry() }
    val sections = timeline
        .sortedByDescending { it.first }
        .groupBy { RumaTime.dayLabel(it.first).ifBlank { "ANTERIORES" } }
        .map { (label, rows) -> HistorySectionUi(label, rows.map { it.second }) }
        .toMutableList()
    if (redemptions.isNotEmpty()) {
        sections += HistorySectionUi(
            "CANJES",
            redemptions.map {
                val name = it.redemption_options?.name ?: redeemKindLabel(it.kind)
                val title = redeemAmount(it)?.let { a -> "Canje $name $a" } ?: "Canje $name"
                HistoryEntryUi(
                    isRedeem = true, code = "redeem", title = title,
                    meta = RumaTime.dayMonth(it.created_at), statusText = null, statusKind = null,
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
    val statusText = when (status) {
        "collected" -> "Recogido"
        "scheduled" -> "Recojo pedido"
        "pending" -> "Listo para pedir recojo"
        else -> null
    }
    val firstCode = detection_items.firstOrNull()?.materials?.code ?: "PET"
    return HistoryEntryUi(
        isRedeem = false, code = firstCode, title = title, meta = meta,
        statusText = statusText, statusKind = null, note = null, amount = null,
    )
}

private fun PickupDto.toHistoryEntry(): HistoryEntryUi {
    val (statusText, kind) = when (status) {
        "requested" -> "Buscando Eco-Rider" to "assigned"
        "assigned" -> "Eco-Rider asignado" to "assigned"
        "en_route", "arriving" -> "En camino" to "assigned"
        "collecting" -> "Recolectando en tu puerta" to "assigned"
        "completed" -> "Recojo completado" to "done"
        "cancelled" -> "Cancelado" to "cancelled"
        else -> "No se pudo recoger" to "cancelled"
    }
    val points = generator_points_awarded?.takeIf { it > 0 }
    return HistoryEntryUi(
        isRedeem = false,
        code = pickup_items.firstOrNull()?.materials?.code ?: "PET",
        title = "Recojo · ${itemsSummary(pickup_items)}",
        // In flight: when and who. Closed: who; the window has passed.
        meta = (if (status in ACTIVE) listOfNotNull(windowText(window_label, scheduled_date), rider?.display_name)
        else listOfNotNull(rider?.display_name)).joinToString(" · ").ifBlank { code.orEmpty() },
        statusText = statusText,
        statusKind = kind,
        // A cancelled pickup earns nothing, so it shows no points at all.
        note = when {
            generator_points_final != null -> "+${groupInt(generator_points_final)} pts acreditados"
            status == "completed" && points != null -> "+$points pts por confirmar en el acopio"
            else -> null
        },
        amount = null,
        pickupId = id,
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
        shareText = "♻️ Acabo de reciclar $itemsLabel con Ruma y gané +$total_points Eco-Puntos. ¡Únete y recicla conmigo!",
    )
}

/** The row for the units whose label the AI couldn't read. */
internal const val UNNAMED_BRAND = "Sin marca legible"

/**
 * Units per brand, biggest first, with a row owning up to the units with no legible label:
 * the AI leaves a brand out rather than guess, and two brands under a count of 4 read as a
 * miscount on the phone. "Agua Fiel" and "Aguafiel" are one bottle read twice: one row.
 * Empty when no label was read at all; the material's own stepper covers that.
 */
internal fun brandRows(brands: Map<String, Int>, quantity: Int): Map<String, Int> {
    val named = brands.entries.groupBy { brandKey(it.key) ?: it.key }.values
        .map { same -> same.first().key to same.maxOf { it.value } }
        .sortedByDescending { it.second }
    if (named.isEmpty()) return emptyMap()
    val unnamed = quantity - named.sumOf { it.second }
    return (named + listOfNotNull(if (unnamed > 0) UNNAMED_BRAND to unnamed else null)).toMap()
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
        brandRows = brandRows(brands, quantity),
    )
}

private fun pe.ruma.app.data.ChallengeDto.toUi(): ChallengeUi {
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

private fun CollectionCenterDto.toUi(km: Double? = null): CenterUi = CenterUi(
    name = name,
    address = address_line ?: "Dirección no disponible",
    open = is_open,
    materials = center_materials.mapNotNull { it.materials?.name }.distinct().joinToString(", ").ifBlank { "Varios materiales" },
    lat = latitude,
    lng = longitude,
    phone = phone,
    km = km,
)

private fun NotificationDto.toUi(highlighted: Boolean) = NotifUi(
    kind = kind,
    title = title,
    body = body ?: "",
    time = RumaTime.relative(created_at),
    unread = !is_read,
    highlighted = highlighted,
    id = id,
    aboutPickup = reference_type == "pickup",
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

/** What a redemption was worth: arbitrios in % of the yearly fee, money in soles, nothing otherwise. */
private fun redeemAmount(r: RedemptionDto): String? = when {
    r.kind == "municipal_discount" || r.redemption_options?.code == "MUNI_ARBITRIOS" -> "${trimNum(r.quantity)} %"
    r.monetary_value > 0 -> "S/%.2f".format(Locale.US, r.monetary_value)
    else -> null
}

/** Arbitrios are capped per calendar year in Lima, whatever the phone's zone. */
private fun isThisLimaYear(ts: String): Boolean {
    val at = RumaTime.millis(ts) ?: return false
    val lima = TimeZone.getTimeZone("America/Lima")
    val year = { m: Long -> Calendar.getInstance(lima).apply { timeInMillis = m }.get(Calendar.YEAR) }
    return year(at) == year(System.currentTimeMillis())
}

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

private fun itemsSummaryFromDetection(items: List<pe.ruma.app.data.DetectionItemDto>): String =
    items.takeIf { it.isNotEmpty() }
        ?.joinToString(" + ") { "${it.quantity} ${shortMaterial(it.materials?.code)}" }
        ?: "Residuos"

/** yyyy-MM-dd for the chosen window label: "Hoy" -> today, anything else -> tomorrow. */
private fun scheduledDateFor(windowLabel: String): String {
    val cal = Calendar.getInstance()
    if (!windowLabel.startsWith("Hoy", ignoreCase = true)) cal.add(Calendar.DAY_OF_MONTH, 1)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

private val FLOW_LABELS = listOf("Pedido", "Asignado", "En camino", "Llegando", "Recogido")
private val FLOW_STATUSES = listOf("requested", "assigned", "en_route", "arriving")

private fun PickupDto.flowStamps() = listOf(requested_at ?: created_at, assigned_at, started_at, arrived_at, completed_at)
private fun clockOf(ts: String?): String? = ts?.let { RumaTime.millis(it) }?.let { RumaTime.clockAt(it) }

/** Index of the current step: all five done when completed; collecting still counts as "Llegando". */
private fun flowCurrent(status: String): Int = when (status) {
    "completed" -> FLOW_LABELS.size
    "collecting" -> 3
    else -> FLOW_STATUSES.indexOf(status)
}

private fun PickupDto.flowSteps(): List<Pair<String, String?>> {
    val current = flowCurrent(status)
    val stamps = flowStamps()
    return FLOW_LABELS.mapIndexed { i, l -> l to clockOf(stamps[i]).takeIf { i < current } }
}

/** Every step that happened, with its time; a cancellation closes the list. */
private fun PickupDto.timeline(): List<Pair<String, String>> {
    val stamps = flowStamps()
    val steps = FLOW_LABELS.mapIndexedNotNull { i, l -> clockOf(stamps[i])?.let { (if (i == 3) "Llegó" else l) to it } }
    val end = clockOf(cancelled_at)?.takeIf { status != "completed" }
        ?.let { (if (status == "cancelled") "Cancelado" else "No se pudo recoger") to it }
    return steps + listOfNotNull(end)
}

/** The stored label says "Hoy …" from the day it was asked (old rows "Manana"); the day comes from scheduled_date. */
internal fun windowText(label: String?, scheduledDate: String?): String {
    val time = label.orEmpty().replace(Regex("^(Hoy|Ma[ñn]ana)\\s*", RegexOption.IGNORE_CASE), "")
    val days = scheduledDate?.let { RumaTime.daysFromToday(it) } ?: return label.orEmpty()
    val day = when (days) { 0 -> "Hoy"; 1 -> "Mañana"; else -> RumaTime.dayMonth(scheduledDate) }
    return "$day $time".trim()
}

internal fun vehicleLabel(v: String?): String = when (v) {
    "moto" -> "Moto"
    "bici", "bicicleta" -> "Bici"
    "triciclo" -> "Triciclo"
    "furgoneta" -> "Furgoneta"
    "auto" -> "Auto"
    "a_pie" -> "A pie"
    else -> "Vehículo"
}

private fun homeActiveTitle(status: String, rider: String?): String = when (status) {
    "requested" -> "Buscando un Eco-Rider"
    "assigned" -> "${rider ?: "Un Eco-Rider"} tomó tu recojo"
    "collecting" -> "${rider ?: "Tu Eco-Rider"} está en tu puerta"
    else -> "${rider ?: "Tu Eco-Rider"} va en camino"
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
    "requested" -> "Buscando un Eco-Rider disponible cerca de ti. Te avisaremos apenas uno lo tome."
    "assigned" -> "$rider tomó tu recojo y saldrá pronto. ¡Ten listos tus residuos!"
    "en_route" -> "$rider va en camino. Míralo acercarse en el mapa."
    "arriving" -> "$rider está a pocos metros de tu casa."
    "collecting" -> "$rider está recogiendo y contando tus residuos."
    "completed" -> "$rider recogió tus residuos. Tus puntos se confirman al pesarse en el acopio."
    "cancelled" -> "Este recojo fue cancelado."
    else -> "Seguimiento de tu recojo."
}

/** API-safe date formatting (minSdk 24, no java.time). */
object RumaTime {
    private val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val monthsEs = arrayOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    private val weekdaysEs = arrayOf("DOMINGO", "LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO")

    private val dateOnly = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Timestamps arrive as "2026-10-05T12:00:00Z", but a leaderboard period is a
    // plain date — parsing only the long form left the label empty ("Del  al").
    private fun parse(ts: String): Date? = try {
        if (ts.length <= 10) dateOnly.parse(ts) else parser.parse(ts.take(19))
    } catch (e: Exception) { null }

    /** Epoch millis for an ISO stamp, or null when it cannot be parsed (date filters). */
    fun millis(ts: String): Long? = parse(ts)?.time

    /** 0 today, 1 tomorrow, negative in the past; null when [ts] cannot be parsed. */
    fun daysFromToday(ts: String): Int? = parse(ts)?.let { -daysAgo(it) }

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
            // A weekday is only unambiguous within the week: a pickup from last
            // Friday was filed under "VIERNES" as if it were this week's.
            in 2..6 -> weekdaysEs[Calendar.getInstance().apply { time = d }.get(Calendar.DAY_OF_WEEK) - 1]
            else -> dayMonth(ts).uppercase(Locale("es", "PE"))
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

    /**
     * Wall-clock time [minutes] from now ("4:35 p. m.") — an ETA a rider can act on:
     * "28 min" needs mental arithmetic while riding, an arrival hour does not.
     */
    fun clockPlus(minutes: Int): String {
        val c = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
        return clockAt(c.timeInMillis)
    }

    /** The clock face for an instant ("3:00 p. m."). */
    fun clockAt(millis: Long): String =
        SimpleDateFormat("h:mm a", Locale("es", "PE")).format(Date(millis))

    private fun daysAgo(d: Date): Int {
        val day = Calendar.getInstance().apply { time = d; clearTime() }
        val today = Calendar.getInstance().apply { clearTime() }
        return ((today.timeInMillis - day.timeInMillis) / 86_400_000L).toInt()
    }

    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
}
