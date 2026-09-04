package com.example.maki.ui.screens.rider

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.maki.data.MakiApi
import com.example.maki.data.MakiRepository
import com.example.maki.data.NotificationDto
import com.example.maki.data.PickupDto
import com.example.maki.data.RiderEarningDto
import com.example.maki.data.RiderProfileDto
import com.example.maki.data.RoutePlanner
import com.example.maki.data.RouteDto
import com.example.maki.data.UserAchievementDto
import com.example.maki.ui.screens.generator.MakiTime
import com.example.maki.ui.screens.generator.groupInt
import com.example.maki.ui.screens.generator.itemsSummary
import com.example.maki.ui.screens.generator.shortMaterial
import com.example.maki.ui.screens.generator.surnameInitial
import com.example.maki.ui.theme.MakiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Live state for the Eco-Rider flow. Every screen reads from the database:
 * the dashboard's next stop, the optimised route (routes + route_stops, falling
 * back to the rider's own assigned pickups when an operator planned none), the
 * settled history, earnings, stats/badges, the rider leaderboard and notifications.
 *
 * Each section is fetched once and cached in the ViewModel; [refresh] and the
 * write actions invalidate what they change, so navigating between tabs does not
 * re-query the backend.
 */
class RiderViewModel : ViewModel() {

    var dashboard by mutableStateOf(RiderDashboardUiState())
        private set
    var confirm by mutableStateOf(RiderConfirmUiState())
        private set

    // ---- Section state: null = still loading, empty = loaded and genuinely empty ----
    var route by mutableStateOf<RiderRouteUiState?>(null)
        private set
    var history by mutableStateOf<RiderHistoryUiState?>(null)
        private set
    var earnings by mutableStateOf<RiderEarningsUiState?>(null)
        private set
    var stats by mutableStateOf<RiderStatsUiState?>(null)
        private set
    var ranking by mutableStateOf<RiderRankingUiState?>(null)
        private set
    var notifications by mutableStateOf<List<RiderNotifUi>?>(null)
        private set

    /** Per-section load failure, keyed by section, so each screen can offer a retry. */
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** One-shot snackbar message; cleared by [consumeMessage]. */
    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** The pickup the flow acts on: the rider's in-flight one, else the first available. */
    private var activePickup by mutableStateOf<PickupDto?>(null)

    /** Turn-by-turn state for the stop in progress; null when nothing is accepted. */
    val navigation: RiderNavigationUiState?
        get() = activePickup?.takeIf { it.rider_id != null }?.toNavigationUi()

    fun consumeMessage() { message = null }
    fun notify(text: String) { message = text }

    private suspend fun ensureSignedIn() {
        if (!MakiApi.isSignedIn) MakiRepository.signInAsTestRider()
    }

    /**
     * Loads one section, recording a per-section error instead of throwing so a
     * failing tab never blanks the rest of the app.
     */
    private fun load(key: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                ensureSignedIn()
                block()
                if (errors.containsKey(key)) errors = errors - key
            } catch (e: Exception) {
                errors = errors + (key to (e.message ?: "No se pudo cargar la información."))
            }
        }
    }

    fun refresh() {
        load(KEY_DASHBOARD) {
            val mine = MakiRepository.loadMyRiderPickup()
            val available = runCatching { MakiRepository.loadAvailablePickups() }.getOrDefault(emptyList())
            activePickup = mine ?: available.firstOrNull()
            val earningRows = runCatching { MakiRepository.loadRiderEarnings() }.getOrDefault(emptyList())
            val name = runCatching { MakiRepository.loadMyDisplayName() }.getOrNull()
            val profile = runCatching { MakiRepository.loadMyRiderProfile() }.getOrNull()
            dashboard = buildDashboard(dashboard, name, activePickup, mine != null, available.size, earningRows, profile)
            activePickup?.let { confirm = it.toConfirmUi() }
        }
    }

    // ---- Sections (fetched once, then served from memory) ----

    /** Raw stops behind [route]; kept so a new GPS fix reorders without refetching. */
    private var plannedRoute: RouteDto? = null
    private var assignedStops: List<PickupDto> = emptyList()
    private var origin: RoutePlanner.LatLng? = null

    fun loadRoute(origin: RoutePlanner.LatLng? = this.origin, force: Boolean = false) = load(KEY_ROUTE) {
        this.origin = origin
        if (route != null && !force) return@load
        plannedRoute = runCatching { MakiRepository.loadTodayRoute(today()) }.getOrNull()
        assignedStops = runCatching { MakiRepository.loadMyAssignedPickups() }.getOrDefault(emptyList())
        route = buildRoute(plannedRoute, assignedStops, origin)
    }

    /**
     * Re-orders the stops already in memory against a new starting point (the GPS
     * fix usually lands after the first paint). No network call.
     */
    fun reorderRoute(from: RoutePlanner.LatLng?) {
        origin = from
        if (route != null) route = buildRoute(plannedRoute, assignedStops, from)
    }

    fun loadHistory(force: Boolean = false) = load(KEY_HISTORY) {
        if (history != null && !force) return@load
        history = buildHistory(MakiRepository.loadRiderHistory())
    }

    fun loadEarnings(force: Boolean = false) = load(KEY_EARNINGS) {
        if (earnings != null && !force) return@load
        val rows = MakiRepository.loadRiderEarnings(limit = 200)
        earnings = buildEarnings(rows, MakiRepository.loadMyRiderProfile())
    }

    fun loadStats(force: Boolean = false) = load(KEY_STATS) {
        if (stats != null && !force) return@load
        val profile = MakiRepository.loadMyRiderProfile()
        val rows = MakiRepository.loadRiderEarnings(limit = 200)
        val badges = runCatching { MakiRepository.loadMyAchievements() }.getOrDefault(emptyList())
        stats = buildStats(profile, rows, badges)
    }

    fun loadRanking(force: Boolean = false) = load(KEY_RANKING) {
        if (ranking != null && !force) return@load
        val period = MakiRepository.loadOpenPeriod(role = "eco_rider")
        if (period == null) { ranking = RiderRankingUiState(zone = "Ranking de riders"); return@load }
        val entries = MakiRepository.loadLeaderboardEntries(period.id)
        val rewards = runCatching { MakiRepository.loadRankingRewards(period.scope, period.period_type) }
            .getOrDefault(emptyList())
        ranking = buildRanking(period, entries, rewards, MakiApi.session?.userId)
    }

    fun loadNotifications(force: Boolean = false) = load(KEY_NOTIFICATIONS) {
        if (notifications != null && !force) return@load
        notifications = MakiRepository.loadNotifications().map { it.toRiderUi() }
    }

    /** Marks one notification read (optimistic; a failure just leaves the dot on). */
    fun markNotificationRead(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            runCatching {
                ensureSignedIn()
                MakiRepository.markNotificationRead(id)
                notifications = notifications?.map { if (it.id == id) it.copy(unread = false) else it }
            }
        }
    }

    fun markAllNotificationsRead() {
        if (notifications?.any { it.unread } != true) return
        viewModelScope.launch {
            try {
                ensureSignedIn()
                MakiRepository.markAllNotificationsRead()
                notifications = notifications?.map { it.copy(unread = false) }
                message = "Notificaciones marcadas como leídas"
            } catch (e: Exception) {
                message = e.message ?: "No se pudieron marcar como leídas."
            }
        }
    }

    // ---- Write actions ----

    /** Claims the visible next stop if it isn't ours yet, then continues to navigation. */
    fun acceptNextStop(onAccepted: () -> Unit) {
        val p = activePickup ?: run { message = "No hay recojos pendientes por ahora."; return }
        val id = p.id ?: return
        if (p.rider_id != null) { onAccepted(); return }   // already mine, just navigate
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                ensureSignedIn()
                if (MakiRepository.acceptPickup(id)) {
                    message = "¡Recojo asignado! En camino."
                    route = null    // the route gained a stop
                    refresh()
                    onAccepted()
                } else {
                    message = "Otro rider tomó este recojo."
                    refresh()
                }
            } catch (e: Exception) {
                message = e.message ?: "No se pudo tomar el recojo."
            } finally {
                busy = false
            }
        }
    }

    /** Marks arrival at the stop (best-effort; the flow continues even offline). */
    fun arrive() {
        val id = activePickup?.takeIf { it.rider_id != null }?.id ?: return
        viewModelScope.launch {
            runCatching { ensureSignedIn(); MakiRepository.setPickupStatus(id, "collecting") }
        }
    }

    /** Completes the pickup → settle credits points/earnings/retos server-side. */
    fun confirmCollection(onDone: () -> Unit) {
        val p = activePickup ?: run { message = "No hay un recojo activo."; return }
        val id = p.id ?: return
        if (p.rider_id == null) { message = "Primero acepta el recojo desde el inicio."; return }
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                ensureSignedIn()
                MakiRepository.completePickup(id)
                message = "¡Recolección confirmada! Ganancia acreditada."
                activePickup = null
                // Everything the settle touched is now stale.
                route = null; history = null; earnings = null; stats = null
                refresh()
                onDone()
            } catch (e: Exception) {
                message = e.message ?: "No se pudo confirmar la recolección."
            } finally {
                busy = false
            }
        }
    }

    companion object {
        const val KEY_DASHBOARD = "dashboard"
        const val KEY_ROUTE = "route"
        const val KEY_HISTORY = "history"
        const val KEY_EARNINGS = "earnings"
        const val KEY_STATS = "stats"
        const val KEY_RANKING = "ranking"
        const val KEY_NOTIFICATIONS = "notifications"
    }
}

// ---- Mapping helpers ----

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun soles(v: Double): String = "S/%.2f".format(Locale.US, v)

private fun buildDashboard(
    prev: RiderDashboardUiState,
    name: String?,
    next: PickupDto?,
    nextIsMine: Boolean,
    availableCount: Int,
    earnings: List<RiderEarningDto>,
    profile: RiderProfileDto?,
): RiderDashboardUiState {
    val todayRows = earnings.filter { it.created_at.startsWith(today()) }
    val doneToday = todayRows.size
    val pending = availableCount + (if (nextIsMine) 1 else 0)
    val monthTotal = earnings.filter { it.created_at.startsWith(currentMonthPrefix()) }.sumOf { it.amount }
    val goal = profile?.monthly_goal?.takeIf { it > 0 }
    return prev.copy(
        monthEarnings = soles(monthTotal),
        goalProgress = goal?.let { (monthTotal / it).coerceIn(0.0, 1.0).toFloat() } ?: 0f,
        goalPctText = goal?.let { "${((monthTotal / it) * 100).toInt().coerceAtMost(100)}% de tu meta" } ?: "",
        goalText = goal?.let { "Meta: ${soles(it)}" } ?: "Sin meta definida",
        riderName = name ?: prev.riderName,
        pickupsTotal = "${doneToday + pending}",
        completed = "$doneToday/${doneToday + pending}",
        earnedToday = soles(todayRows.sumOf { it.amount }),
        nextStopName = next?.generator?.display_name ?: "Sin recojos pendientes",
        nextStopAddress = next?.address?.address_line ?: "Te avisaremos cuando haya solicitudes",
        nextStopDistance = if (nextIsMine) "Asignado" else (next?.window_label ?: ""),
        nextStopChips = next?.pickup_items.orEmpty().mapNotNull { item ->
            val qty = item.confirmed_quantity ?: item.reported_quantity
            item.materials?.code?.let { "$qty ${shortMaterial(it)}" }
        },
    )
}

private fun PickupDto.toConfirmUi(): RiderConfirmUiState {
    val mats = pickup_items.map {
        ConfirmMaterialUi(
            name = it.materials?.name ?: "Material",
            detail = "${it.confirmed_quantity ?: it.reported_quantity} unidades",
            points = "+${it.points} pts",
        )
    }
    val totalPts = (generator_points_awarded ?: 0).takeIf { it > 0 } ?: pickup_items.sumOf { it.points }
    return RiderConfirmUiState(
        stopName = generator?.display_name ?: "Generador",
        address = address?.address_line ?: "Dirección no registrada",
        streak = window_label ?: "Recojo programado",
        materials = mats.ifEmpty {
            listOf(ConfirmMaterialUi("Residuos declarados", "Verifica el material en el punto", ""))
        },
        totalWeight = total_weight_kg?.let { "%.1f kg".format(Locale.US, it) } ?: "—",
        totalPoints = "$totalPts pts",
        earnings = soles(rider_earnings ?: 0.0),
    )
}

private fun PickupDto.toNavigationUi(): RiderNavigationUiState = RiderNavigationUiState(
    stopName = generator?.display_name ?: "Generador",
    address = address?.address_line ?: "Dirección no registrada",
    distance = distance_m?.let { "%.0f m".format(Locale.US, it) } ?: "—",
    eta = eta_minutes?.let { "$it min" } ?: "—",
    instruction = address?.reference?.takeIf { it.isNotBlank() } ?: "Dirígete al punto de recojo",
    materials = pickup_items.mapNotNull { item ->
        val qty = item.confirmed_quantity ?: item.reported_quantity
        item.materials?.code?.let { "$qty ${shortMaterial(it)}" }
    },
    lat = latLng()?.first,
    lng = latLng()?.second,
)

/**
 * Where the stop actually is: the pickup's own coordinates when the app recorded
 * them, otherwise the generator's saved address. Without this fallback most stops
 * have no pin at all — only 1 of 4 pickups in staging carries its own lat/lng.
 */
private fun PickupDto.latLng(): Pair<Double, Double>? {
    val lat = latitude ?: address?.latitude
    val lng = longitude ?: address?.longitude
    return if (lat != null && lng != null) lat to lng else null
}

// ---- Route ----

/**
 * Builds the stop list from the operator's planned route when there is one, else
 * from the rider's own assigned pickups. Both paths produce the same UI shape.
 */
private fun buildRoute(
    planned: RouteDto?,
    assigned: List<PickupDto>,
    origin: RoutePlanner.LatLng?,
): RiderRouteUiState {
    val fromPlan = planned?.route_stops.orEmpty()
        .sortedBy { it.sequence }
        .mapIndexedNotNull { i, stop ->
            val p = stop.pickups ?: return@mapIndexedNotNull null
            p.toStopUi(
                number = (i + 1).toString(),
                eta = stop.eta_minutes?.let { "$it min" },
                done = stop.status == "collected" || stop.status == "skipped",
            )
        }
    // With no operator-planned route, the stops arrive in creation order — which is
    // not a route. Order them into a short round (nearest neighbour + 2-opt) from
    // wherever the rider is standing.
    val stops = fromPlan.ifEmpty {
        RoutePlanner.optimize(origin, assigned) { p -> p.latLng()?.let { RoutePlanner.LatLng(it.first, it.second) } }
            .mapIndexed { i, p ->
                p.toStopUi(number = (i + 1).toString(), eta = p.eta_minutes?.let { "$it min" }, done = false)
            }
    }
    val tourKm = RoutePlanner.tourLengthKm(
        origin,
        stops.mapNotNull { it.marker }.map { RoutePlanner.LatLng(it.lat, it.lng) },
        stops.mapNotNull { it.marker }.indices.toList(),
    )
    val distanceM = planned?.total_distance_m
        ?: assigned.sumOf { it.distance_m ?: 0.0 }.takeIf { it > 0 }
        ?: tourKm.takeIf { it > 0 }?.let { it * 1000.0 }
    val minutes = planned?.total_duration_min
        ?: tourKm.takeIf { it > 0 }?.let { km -> (km / 12.0 * 60).toInt() + stops.size * 4 }
        ?: stops.size.takeIf { it > 0 }?.let { it * 15 }
    val estimated = assigned.sumOf { it.rider_earnings ?: 0.0 }

    return RiderRouteUiState(
        optimizedNote = when {
            planned != null -> "Ruta optimizada · ${planned.total_stops} paradas"
            stops.isEmpty() -> "Aún no tienes paradas asignadas"
            stops.size >= 3 -> "Ruta optimizada · ${stops.size} paradas en orden más corto"
            else -> "${stops.size} parada${if (stops.size == 1) "" else "s"} asignada${if (stops.size == 1) "" else "s"}"
        },
        stops = stops,
        totalDistance = distanceM?.let { "%.1f km total".format(Locale.US, it / 1000.0) } ?: "—",
        totalTime = minutes?.let { "${it / 60}h ${it % 60}m" } ?: "—",
        estimatedEarnings = if (estimated > 0) "${soles(estimated)} estimado por esta ruta" else "Ganancia por confirmar",
        markers = stops.mapNotNull { it.marker },
    )
}

private fun PickupDto.toStopUi(number: String, eta: String?, done: Boolean): RouteStopUi = RouteStopUi(
    number = number,
    name = generator?.display_name ?: "Generador",
    subtitle = address?.address_line ?: "Dirección no registrada",
    eta = eta,
    material = itemsSummary(pickup_items),
    streak = window_label,
    highlighted = status == "en_route" || status == "arriving" || status == "collecting",
    done = done,
    marker = latLng()?.let { (lat, lng) ->
        RouteMarkerUi(lat, lng, "$number · ${generator?.display_name ?: "Parada"}")
    },
)

// ---- History ----

private fun buildHistory(pickups: List<PickupDto>): RiderHistoryUiState {
    val entries = pickups.map { p ->
        val (status, label) = when {
            p.status == "cancelled" || p.status == "failed" -> HistoryStatus.CANCELLED to "Cancelado"
            p.match_status == "partial" -> HistoryStatus.PARTIAL to "Parcial"
            p.status == "no_match" || p.match_status == "none" -> HistoryStatus.PARTIAL to "Sin coincidencia"
            else -> HistoryStatus.COMPLETED to "Completado"
        }
        val ts = p.completed_at ?: p.created_at.orEmpty()
        RiderHistoryEntryUi(
            id = p.id ?: ts,
            name = p.generator?.display_name ?: "Generador",
            detail = listOfNotNull(
                itemsSummary(p.pickup_items).takeIf { p.pickup_items.isNotEmpty() },
                p.address?.address_line,
            ).joinToString(" · ").ifBlank { "Recojo sin detalle" },
            earnings = p.rider_earnings?.takeIf { it > 0 }?.let { "+${soles(it)}" } ?: "—",
            time = MakiTime.relative(ts),
            status = status,
            statusLabel = label,
            note = p.notes ?: p.cancel_reason,
            timestamp = ts,
        )
    }
    return RiderHistoryUiState(
        sections = entries.groupBy { MakiTime.dayLabel(it.timestamp) }
            .map { (label, rows) -> RiderHistorySectionUi(label.ifBlank { "ANTERIORES" }, rows) },
    )
}

// ---- Earnings ----

private fun buildEarnings(rows: List<RiderEarningDto>, profile: RiderProfileDto?): RiderEarningsUiState {
    val month = currentMonthPrefix()
    val monthRows = rows.filter { it.created_at.startsWith(month) }
    val monthTotal = monthRows.sumOf { it.amount }
    val goal = profile?.monthly_goal?.takeIf { it > 0 }
    val todayRows = rows.filter { it.created_at.startsWith(today()) }

    // Last 7 calendar days, most recent first — days without work still show as a row.
    val byDay = rows.groupBy { it.created_at.take(10) }
    val days = (0..6).map { back ->
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        val dayRows = byDay[key].orEmpty()
        val amount = dayRows.sumOf { it.amount }
        EarningDayUi(
            day = when (back) { 0 -> "Hoy"; 1 -> "Ayer"; else -> weekdayShort(cal) },
            recs = "${dayRows.size} rec",
            amount = soles(amount),
            tag = if (dayRows.isEmpty()) "sin actividad" else null,
            tagNegative = dayRows.isEmpty(),
            inactive = dayRows.isEmpty(),
        )
    }

    return RiderEarningsUiState(
        monthEarned = soles(monthTotal),
        goalText = goal?.let { "Meta ${soles(it)}" } ?: "Sin meta definida",
        goalRemaining = when {
            goal == null -> "Define una meta mensual en tu perfil"
            monthTotal >= goal -> "¡Meta del mes alcanzada!"
            else -> "Faltan ${soles(goal - monthTotal)} para tu meta"
        },
        progress = goal?.let { (monthTotal / it).coerceIn(0.0, 1.0).toFloat() } ?: 0f,
        today = "${soles(todayRows.sumOf { it.amount })} hoy",
        todaySub = "${todayRows.size} recojo${if (todayRows.size == 1) "" else "s"} completado${if (todayRows.size == 1) "" else "s"}",
        days = days,
        available = soles(profile?.earnings_balance ?: monthTotal),
        withdrawNote = "Mínimo S/50.00 · 1-2 días hábiles",
        canWithdraw = (profile?.earnings_balance ?: 0.0) >= 50.0,
    )
}

private fun currentMonthPrefix(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

private fun weekdayShort(cal: Calendar): String =
    arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")[cal.get(Calendar.DAY_OF_WEEK) - 1]

// ---- Stats ----

private fun buildStats(
    profile: RiderProfileDto?,
    earnings: List<RiderEarningDto>,
    badges: List<UserAchievementDto>,
): RiderStatsUiState {
    val tiles = listOf(
        StatTileUi(Icons.Filled.Home, MakiColors.Rider, groupInt(profile?.total_collections ?: 0), "recolecciones"),
        StatTileUi(Icons.Filled.Recycling, MakiColors.Gen, "%.0f kg".format(Locale.US, profile?.total_recycled_kg ?: 0.0), "reciclado"),
        StatTileUi(Icons.Filled.Eco, MakiColors.Success, "%.0f kg".format(Locale.US, profile?.co2_avoided_kg ?: 0.0), "CO₂ evitado"),
        StatTileUi(Icons.Filled.Route, MakiColors.Rider, "${groupInt(earnings.size)}", "viajes pagados"),
        StatTileUi(
            Icons.Filled.Star, MakiColors.Money,
            profile?.rating?.let { "%.1f".format(Locale.US, it) } ?: "—",
            if ((profile?.rating_count ?: 0) > 0) "de ${profile?.rating_count} reseñas" else "sin reseñas",
        ),
        StatTileUi(Icons.Filled.AccountBalanceWallet, MakiColors.Money, soles(profile?.earnings_lifetime ?: 0.0), "ganado"),
    )

    // Pickups per day over the last 7 days, scaled against the busiest day.
    val byDay = earnings.groupBy { it.created_at.take(10) }
    val week = (6 downTo 0).map { back ->
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        Triple(weekdayInitial(cal), byDay[key].orEmpty().size, back == 0)
    }
    val peak = week.maxOfOrNull { it.second } ?: 0
    val bars = week.map { (label, count, isToday) ->
        StatBarUi(
            label = label,
            heightDp = if (peak == 0) 4 else (8 + (count.toFloat() / peak * 122f)).toInt(),
            active = isToday,
            value = count.toString(),
        )
    }

    val badgeTiles = badges.map { b ->
        StatBadgeUi(
            icon = if (b.unlocked) badgeIcon(b.achievements?.tier) else Icons.Filled.Lock,
            label = b.achievements?.name ?: "Logro",
            locked = !b.unlocked,
        )
    }

    return RiderStatsUiState(
        tiles = tiles,
        bars = bars,
        badges = badgeTiles,
        weeklyTotal = "${week.sumOf { it.second }} recojos esta semana",
    )
}

private fun weekdayInitial(cal: Calendar): String =
    arrayOf("D", "L", "M", "X", "J", "V", "S")[cal.get(Calendar.DAY_OF_WEEK) - 1]

private fun badgeIcon(tier: String?) = when (tier) {
    "gold", "oro" -> Icons.Filled.WorkspacePremium
    "silver", "plata" -> Icons.Filled.MilitaryTech
    else -> Icons.Filled.EmojiEvents
}

// ---- Ranking ----

private fun buildRanking(
    period: com.example.maki.data.LeaderboardPeriodDto,
    entries: List<com.example.maki.data.LeaderboardEntryDto>,
    rewards: List<com.example.maki.data.RankingRewardDto>,
    me: String?,
): RiderRankingUiState {
    val podiumColors = listOf(Gold, Silver, Bronze)
    val second = entries.getOrNull(1)?.points ?: 0
    val podium = entries.take(3).mapIndexed { i, e ->
        val mine = e.user_id == me
        PodiumUi(
            rank = e.rank,
            name = e.display_name ?: "Rider",
            sub = if (e.streak_days > 0) "Racha ${e.streak_days} días" else "—",
            points = groupInt(e.points),
            monthEarnings = e.delta.let { if (it >= 0) "+$it pts esta semana" else "$it pts esta semana" },
            medalColor = if (mine) MakiColors.Rider else podiumColors.getOrElse(i) { MakiColors.Rider },
            crown = e.rank == 1,
            isYou = mine,
            gap = if (mine && e.rank > 1) "A ${second - e.points} pts del ${e.rank - 1}°" else null,
        )
    }
    return RiderRankingUiState(
        zone = period.block_label?.let { "Ranking · $it" } ?: "Ranking de riders",
        periodLabel = listOfNotNull(period.period_start, period.period_end)
            .takeIf { it.size == 2 }
            ?.let { "Del ${MakiTime.dayMonth(it[0])} al ${MakiTime.dayMonth(it[1])}" }
            ?: "",
        podium = podium,
        rows = entries.drop(3).map { e ->
            RankRowUi(
                rank = e.rank,
                initial = surnameInitial(e.display_name ?: "Rider"),
                name = e.display_name ?: "Rider",
                points = groupInt(e.points),
                isYou = e.user_id == me,
            )
        },
        prizes = rewards.take(3).mapIndexed { i, r ->
            PrizeUi(
                rank = r.rank,
                value = r.reward_points.takeIf { it > 0 }?.let { "$it pts" }
                    ?: r.description ?: "x${r.reward_multiplier}",
                medalColor = podiumColors.getOrElse(i) { MakiColors.Rider },
            )
        },
        prizeNote = rewards.firstOrNull()?.description ?: "",
    )
}

// ---- Notifications ----

private fun NotificationDto.toRiderUi(): RiderNotifUi {
    val (icon, tint, bg) = when (kind) {
        "rider_assigned", "pickup_update", "rider_arriving" ->
            Triple(Icons.AutoMirrored.Filled.DirectionsBike, MakiColors.Rider, MakiColors.RiderTint)
        "ranking" -> Triple(Icons.Filled.EmojiEvents, MakiColors.Money, MakiColors.MoneyTint)
        "points_earned", "redemption" ->
            Triple(Icons.Filled.AccountBalanceWallet, MakiColors.Success, MakiColors.SuccessTint)
        "challenge" -> Triple(Icons.Filled.Celebration, MakiColors.Streak, MakiColors.StreakTint)
        "promo" -> Triple(Icons.Filled.BarChart, MakiColors.Gen, MakiColors.GenTint)
        else -> Triple(Icons.Filled.Notifications, MakiColors.Text2, MakiColors.Border)
    }
    return RiderNotifUi(
        id = id,
        icon = icon,
        iconTint = tint,
        iconBg = bg,
        title = title,
        body = body.orEmpty(),
        time = MakiTime.relative(created_at),
        unread = !is_read,
    )
}
