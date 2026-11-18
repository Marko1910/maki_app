package pe.ruma.app.ui.screens.rider

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
import pe.ruma.app.data.CollectionCenterDto
import pe.ruma.app.data.DeliveryDto
import pe.ruma.app.data.RumaApi
import pe.ruma.app.data.RumaRepository
import pe.ruma.app.data.NotificationDto
import pe.ruma.app.data.PickupDto
import pe.ruma.app.data.RiderEarningDto
import pe.ruma.app.data.RiderProfileDto
import pe.ruma.app.data.RiderShiftWorker
import pe.ruma.app.data.RoutePlanner
import pe.ruma.app.data.RouteDto
import pe.ruma.app.data.StreetRouter
import pe.ruma.app.data.NavigationRoutePolicy
import pe.ruma.app.data.RouteProgress
import android.os.SystemClock
import kotlinx.coroutines.Job
import pe.ruma.app.data.UserAchievementDto
import pe.ruma.app.ui.screens.generator.RumaTime
import pe.ruma.app.ui.screens.generator.groupInt
import pe.ruma.app.ui.screens.generator.itemsSummary
import pe.ruma.app.ui.screens.generator.shortMaterial
import pe.ruma.app.ui.screens.generator.surnameInitial
import pe.ruma.app.ui.screens.generator.vehicleLabel
import pe.ruma.app.ui.screens.generator.windowText
import pe.ruma.app.ui.theme.RumaColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Live state for the Eco-Rider flow. Every screen reads from the database:
 * the dashboard's next stop, the route (routes + route_stops, falling back to the
 * rider's own assigned pickups ordered by the street router), the settled
 * history, earnings, stats/badges, the rider leaderboard and notifications.
 *
 * Each section is fetched once and cached in the ViewModel; [refresh] and the
 * write actions invalidate what they change, so navigating between tabs does not
 * re-query the backend.
 */
class RiderViewModel : ViewModel() {

    var dashboard by mutableStateOf(
        RiderDashboardUiState(riderName = RumaRepository.cachedDisplayName().orEmpty()),
    )
        private set
    /** What "¡Recojo Completado!" shows: the stop just closed, not a sample. */
    var completed by mutableStateOf(RiderCompletedUiState())
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
    /** Open requests anyone can take, nearest first. null = still loading. */
    var board by mutableStateOf<RiderBoardUiState?>(null)
        private set

    /** The rider's own page. Null until read: the sample is for @Preview, not for a demo. */
    var profile by mutableStateOf<RiderProfileUiState?>(null)
        private set

    /** The load the rider is still carrying, where to drop it, and how past loads ended. */
    var delivery by mutableStateOf<RiderDeliveryUiState?>(null)
        private set

    /** Per-section load failure, keyed by section, so each screen can offer a retry. */
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** One-shot snackbar message; cleared by [consumeMessage]. */
    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    // ---- The stops ----

    /** The rider's own stops in visiting order (a stop already under way first). */
    private var assigned by mutableStateOf<List<PickupDto>>(emptyList())
    /** Open requests on the board, oldest first. */
    private var available by mutableStateOf<List<PickupDto>>(emptyList())
    /** The stop tapped on the route; null = simply the next one in order. */
    private var selectedId by mutableStateOf<String?>(null)
    /** Counts corrected at the door, per material id. */
    private var confirmQty by mutableStateOf<Map<String, Int>>(emptyMap())
    private var confirmCond by mutableStateOf<Map<String, String>>(emptyMap())

    /** Street path for the leg being navigated, and where/for which stop it was asked. */
    private var navRoad by mutableStateOf<StreetRouter.Route?>(null)
    private var navPickupId: String? = null
    private val navPolicy = NavigationRoutePolicy()
    private var navJob: Job? = null
    private var navKey: String? = null
    private var navHere by mutableStateOf<RoutePlanner.LatLng?>(null)
    private var navLoading by mutableStateOf(false)
    private var navError by mutableStateOf<String?>(null)
    private var offRouteFixes = 0
    private var routeGeneration = 0L

    /** Last fresh rider position (heartbeat / navigation). */
    private var origin: RoutePlanner.LatLng? = null
    /** An older or rougher device fix: indoors at night a fresh one may never come. */
    private var staleFix: RoutePlanner.LatLng? = null
    /** The last position this rider published to `rider_profiles` (any device). */
    private var heartbeatFix: RoutePlanner.LatLng? = null
    /**
     * Where every order starts: the best position we have, fresh or not. With none
     * the order is only approximate and the route says so; it used to start silently
     * at whichever stop came first.
     */
    private val start: RoutePlanner.LatLng? get() = origin ?: staleFix ?: heartbeatFix
    /** Mirrors `rider_profiles.is_available`: the shift switch on the dashboard. */
    private var onShift: Boolean = true
    private var vehicle: String? = null
    private var plannedRoute: RouteDto? = null
    /** Stop ids seen on the last refresh, to announce a newly dispatched one. */
    private var knownStopIds: Set<String>? = null

    // Delivery screen: the load, the acopio list and what travels on this trip.
    private var undelivered by mutableStateOf<List<PickupDto>>(emptyList())
    private var centers by mutableStateOf<List<CollectionCenterDto>>(emptyList())
    private var deliveries by mutableStateOf<List<DeliveryDto>>(emptyList())
    private var pickedStops by mutableStateOf<Set<String>>(emptySet())
    private var pickedCenter by mutableStateOf<String?>(null)

    /**
     * The pickup the flow acts on: the stop the rider picked, else the next in
     * order, else the oldest open request (the dashboard offers to claim it).
     * It used to be "the most recently created", which sent the rider across
     * the neighbourhood past a closer stop.
     */
    private val activePickup: PickupDto?
        // A picked stop is binding: if it leaves the list (released, taken, completed
        // elsewhere) the answer is "none", never the next one along. Falling through
        // would aim Llegué / Confirmar / Liberar at a stop the rider never chose.
        get() = if (selectedId != null) assigned.firstOrNull { it.id == selectedId }
        else assigned.firstOrNull() ?: available.firstOrNull()

    /** Turn-by-turn state for the stop in progress; null when nothing is accepted. */
    val navigation: RiderNavigationUiState?
        get() {
            val p = activePickup?.takeIf { it.rider_id != null } ?: return null
            val index = assigned.indexOfFirst { it.id == p.id }
            val road = navRoad.takeIf { navPickupId == p.id }
            val progress = navHere?.let { here -> road?.let { RouteProgress.locate(here, it.path) } }
            val onPath = progress != null && progress.offRouteKm <= 0.06
            val travelled = if (onPath && progress!!.totalKm > 0) progress.travelledKm / progress.totalKm * (road?.distanceKm ?: 0.0) else 0.0
            val next = road?.steps?.firstOrNull { it.atKm >= travelled - 0.01 && it.atKm > 0 } ?: road?.steps?.lastOrNull()
            return p.toNavigationUi(
                road = road,
                position = if (index >= 0 && assigned.size > 1) "Parada ${index + 1} de ${assigned.size}" else "",
            ).copy(
                roadLoading = navLoading,
                roadError = navError,
                maneuver = next?.takeIf { onPath && navError == null }?.instruction,
                maneuverMeters = next?.takeIf { onPath && navError == null }?.let { maxOf(0, ((it.atKm - travelled) * 1000).toInt()) },
                maneuverKey = next?.let { "${p.id}:${it.point}:${it.instruction}" },
                roadKm = road?.distanceKm?.let { maxOf(0.0, it - travelled) },
                roadMinutes = road?.let { maxOf(1, kotlin.math.ceil(it.minutes * (1 - travelled / it.distanceKm.coerceAtLeast(0.001))).toInt()) },
            )
        }

    val confirm: RiderConfirmUiState
        get() = activePickup?.takeIf { it.rider_id != null }?.toConfirmUi(confirmQty, confirmCond) ?: RiderConfirmUiState()

    fun consumeMessage() { message = null }
    fun notify(text: String) { message = text }

    private suspend fun ensureSignedIn() {
        // Dev shortcut only (see GeneratorViewModel.ensureSignedIn).
        if (!RumaApi.isSignedIn) { if (pe.ruma.app.BuildConfig.DEBUG) RumaRepository.signInAsTestRider() else error("Tu sesión terminó. Cierra sesión y vuelve a entrar.") }
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

    /**
     * Stops closed today. Earnings rows only appear when the acopio weighs (mixed pay), so
     * counting them read "0 completados" right after a completed stop (2026-11-17).
     */
    private suspend fun completedToday(): Int = runCatching {
        RumaRepository.loadRiderHistory(limit = 40)
            .count { it.status == "completed" && it.completed_at?.let(::localDay) == today() }
    }.getOrDefault(0)

    /** Dashboard data; also polled while the dashboard is open, so dispatches show up. */
    fun refresh() {
        load(KEY_DASHBOARD) {
            val profile = runCatching { RumaRepository.loadMyRiderProfile() }.getOrNull()
            vehicle = profile?.vehicle_type ?: vehicle
            profile?.position()?.let { heartbeatFix = it }
            val mine = RumaRepository.loadMyAssignedPickups()
            announceChanges(mine)
            assigned = keepOrder(mine)
            available = runCatching { RumaRepository.loadAvailablePickups() }.getOrDefault(emptyList())
            val earningRows = runCatching { RumaRepository.loadRiderEarnings() }.getOrDefault(emptyList())
            val name = runCatching { RumaRepository.loadMyDisplayName() }.getOrNull()
            val unread = runCatching { RumaRepository.loadNotifications().count { !it.is_read } }
                .getOrDefault(dashboard.unreadCount)
            onShift = profile?.is_available ?: onShift
            board = buildBoard(available, start)
            dashboard = buildDashboard(
                dashboard, name, activePickup, assigned.size, available.size, earningRows, profile, unread, start,
                onShift, completedToday(),
            )
        }
    }

    /** The open board, re-read on demand (the screen pulls, the dashboard polls). */
    fun loadBoard(force: Boolean = false) = load(KEY_BOARD) {
        if (board != null && !force) return@load
        available = RumaRepository.loadAvailablePickups()
        board = buildBoard(available, start)
    }

    /**
     * Takes a stop straight off the board. Unlike [acceptNextStop] the rider chose
     * *this* one, so a lost race only drops it from the list — it never navigates.
     */
    fun claimStop(pickupId: String, onClaimed: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                ensureSignedIn()
                if (RumaRepository.acceptPickup(pickupId)) {
                    message = "¡Recojo tomado! Ya está en tu ruta."
                    available = available.filter { it.id != pickupId }
                    board = buildBoard(available, start)
                    selectedId = pickupId
                    route = null
                    refresh()
                    onClaimed()
                } else {
                    message = "Otro Eco-Rider tomó este recojo."
                    loadBoard(force = true)
                }
            } catch (e: Exception) {
                message = e.message ?: "No se pudo tomar el recojo."
            } finally {
                busy = false
            }
        }
    }

    /**
     * "Salgo a las 3:00 pm": the shift stays off until then and turns itself on
     * ([pe.ruma.app.data.RiderShiftWorker]). Passing null drops the plan.
     */
    fun planShift(atMillis: Long?, context: android.content.Context) {
        if (atMillis == null) {
            RiderShiftWorker.cancel(context)
            dashboard = dashboard.copy(shiftPlan = "")
            message = "Quitamos tu salida programada."
        } else {
            RiderShiftWorker.schedule(context, atMillis)
            dashboard = dashboard.copy(shiftPlan = shiftPlanLabel(atMillis))
            message = "Listo: tu turno empieza solo a las ${RumaTime.clockAt(atMillis)}."
        }
        // Best-effort: the reminder already works offline, the database copy is for
        // whoever plans the day on the other side.
        viewModelScope.launch {
            runCatching { ensureSignedIn(); RumaRepository.setRiderShiftStart(atMillis) }
        }
    }

    /** Shift switch: off means the dispatcher stops sending stops to this rider. */
    fun setOnShift(value: Boolean) {
        if (onShift == value) return
        onShift = value
        dashboard = dashboard.copy(onShift = value, zone = shiftLabel(vehicle, value))
        viewModelScope.launch {
            try {
                ensureSignedIn()
                RumaRepository.setRiderAvailability(value)
                message = if (value) "Estás en turno: el despacho puede asignarte recojos."
                          else "Fuera de turno: no recibirás nuevas asignaciones."
            } catch (e: Exception) {
                onShift = !value   // the server said no: show what is actually stored
                dashboard = dashboard.copy(onShift = !value, zone = shiftLabel(vehicle, !value))
                message = e.message ?: "No se pudo cambiar tu disponibilidad."
            }
        }
    }

    /** Snackbar when the dispatcher hands this rider a stop, or a family cancels one. */
    private fun announceChanges(mine: List<PickupDto>) {
        val ids = mine.mapNotNull { it.id }.toSet()
        val known = knownStopIds
        knownStopIds = ids
        if (known == null) return   // first load: nothing is "new"
        val fresh = mine.filter { it.id !in known }
        when {
            fresh.isNotEmpty() -> message = "Nuevo recojo asignado: ${fresh.first().generator?.display_name ?: "un vecino"}"
            (known - ids).isNotEmpty() -> message = "Un recojo salió de tu ruta (lo cancelaron)."
        }
        if (fresh.isNotEmpty() || (known - ids).isNotEmpty()) route = null
    }

    /**
     * Fresh rows, same order: the street-trip order from the route screen must not
     * flip back to the crow-flies order on every dashboard poll. A changed set of
     * stops is re-ordered from scratch.
     */
    private fun keepOrder(mine: List<PickupDto>): List<PickupDto> {
        val byId = mine.associateBy { it.id }
        return if (byId.keys == assigned.map { it.id }.toSet()) assigned.mapNotNull { byId[it.id] } else inVisitOrder(mine)
    }

    /** Nearest neighbour + 2-opt from where the rider stands; the stop at the door stays first. */
    private fun inVisitOrder(stops: List<PickupDto>): List<PickupDto> {
        val here = start
        return RoutePlanner.optimize(here, stops) { it.latLng()?.toLL() }
            .sortedByDescending { it.underWay(here) }
    }

    /**
     * Heartbeat fix. Cheap on purpose (every 15 s): no network, no reorder, except
     * the first position after a route was ordered blind, which redoes it.
     */
    fun onLocation(fix: RoutePlanner.LatLng) {
        val blind = start == null
        origin = fix
        if (blind) reorderRoute(fix)
    }

    /** No fresh fix: an older or rougher one still beats ordering from nowhere. */
    fun onApproxLocation(fix: RoutePlanner.LatLng?) {
        if (fix == null) return
        val blind = start == null
        staleFix = fix
        if (blind) reorderRoute(null)
    }

    // ---- Sections (fetched once, then served from memory) ----

    fun loadRoute(from: RoutePlanner.LatLng? = null, force: Boolean = false) = load(KEY_ROUTE) {
        if (from != null) origin = from
        if (route != null && !force) return@load
        if (start == null) {
            heartbeatFix = runCatching { RumaRepository.loadMyRiderProfile() }.getOrNull()?.position() ?: heartbeatFix
        }
        plannedRoute = runCatching { RumaRepository.loadTodayRoute(today()) }.getOrNull()
        assigned = inVisitOrder(RumaRepository.loadMyAssignedPickups())
        knownStopIds = assigned.mapNotNull { it.id }.toSet()
        route = buildRoute(plannedRoute, assigned, start, street = null)   // instant, crow-flies
        streetTrip()                                                       // then the street-true order
    }

    /** A position landed after the first paint: redo the order from where the rider is. */
    fun reorderRoute(from: RoutePlanner.LatLng?) {
        if (from != null) origin = from
        if (start == null || route == null) return
        load(KEY_ROUTE) {
            assigned = inVisitOrder(assigned)
            route = buildRoute(plannedRoute, assigned, start, street = null)
            streetTrip()
        }
    }

    /**
     * Asks the street router for the fastest order through the rider's stops (by
     * road travel time, not distance as the crow flies) and the path to draw. An
     * operator's planned route keeps its order — they decided it — and only gets
     * its streets drawn. Best-effort: on failure the local order stays.
     */
    private suspend fun streetTrip() {
        val generation = ++routeGeneration
        val here = start
        val planned = plannedRoute?.takeIf { it.route_stops.isNotEmpty() }
        if (planned != null) {
            val pts = listOfNotNull(here) + route?.markers.orEmpty().map { RoutePlanner.LatLng(it.lat, it.lng) }
            val street = StreetRouter.route(pts, vehicle) ?: return
            if (generation != routeGeneration) return
            route = buildRoute(planned, assigned, here, street)
            return
        }
        // Only the stop whose door the rider is at stays pinned. Every in-flight stop
        // used to be: three left "en_route" by earlier tests started a Cartavio rider's
        // trip in Trujillo, with the stop next to him last ("parada 5 de 5, 174 min").
        val head = assigned.firstOrNull { it.underWay(here) }
        val rest = assigned.filter { it !== head }
        val located = rest.filter { it.latLng() != null }
        if (located.isEmpty()) {
            val points = listOfNotNull(here, head?.latLng()?.toLL())
            val street = StreetRouter.route(points, vehicle) ?: return
            if (generation == routeGeneration) route = buildRoute(null, assigned, here, street)
            return
        }
        // No position at all: the trip would start at whichever stop is first. Keep the
        // approximate order, which the screen labels as such, over a confident wrong one.
        val from = head?.latLng()?.toLL() ?: here ?: return
        val stops = assigned.mapNotNull { it.id }
        val trip = StreetRouter.trip(from, located.map { it.latLng()!!.toLL() }, vehicle) ?: return
        if (trip.visitOrder.sorted() != located.indices.toList()) return
        // Stops appeared or left while the router thought: its order and its drawn path
        // describe a route that no longer exists. Drop it — the next fix re-asks.
        if (generation != routeGeneration || assigned.mapNotNull { it.id } != stops || assigned.firstOrNull { it.underWay(here) }?.id != head?.id) return
        // The router can answer after the list moved on (a poll landed, a stop turned
        // en_route). Apply only the order it computed to the rows we hold now; pasting
        // back the copies we captured would undo those fresher statuses.
        // ponytail: indexOf over a rider's handful of stops; a map if routes get long.
        val order = (listOfNotNull(head) + trip.visitOrder.map { located[it] }).mapNotNull { it.id }
        val ordered = assigned.sortedBy { p -> order.indexOf(p.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        // Trip starts at the pinned stop; fetch the complete geometry from the rider.
        val street = if (head != null) StreetRouter.route(listOfNotNull(here) + ordered.mapNotNull { it.latLng()?.toLL() }, vehicle) else trip
        if (generation != routeGeneration || assigned.mapNotNull { it.id } != stops) return
        assigned = assigned.sortedBy { p -> order.indexOf(p.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        route = buildRoute(null, assigned, here, street)
    }

    fun loadHistory(force: Boolean = false) = load(KEY_HISTORY) {
        if (history != null && !force) return@load
        history = buildHistory(RumaRepository.loadRiderHistory())
    }

    fun loadEarnings(force: Boolean = false) = load(KEY_EARNINGS) {
        if (earnings != null && !force) return@load
        val rows = RumaRepository.loadRiderEarnings(limit = 200)
        earnings = buildEarnings(rows, RumaRepository.loadMyRiderProfile(), completedToday())
    }

    fun loadStats(force: Boolean = false) = load(KEY_STATS) {
        if (stats != null && !force) return@load
        val profile = RumaRepository.loadMyRiderProfile()
        val rows = RumaRepository.loadRiderEarnings(limit = 200)
        val badges = runCatching { RumaRepository.loadMyAchievements() }.getOrDefault(emptyList())
        stats = buildStats(profile, rows, badges)
    }

    fun loadRanking(force: Boolean = false) = load(KEY_RANKING) {
        if (ranking != null && !force) return@load
        val period = RumaRepository.loadOpenPeriod(role = "eco_rider")
        if (period == null) { ranking = RiderRankingUiState(zone = "Ranking de riders"); return@load }
        val entries = RumaRepository.loadLeaderboardEntries(period.id)
        val rewards = runCatching { RumaRepository.loadRankingRewards(period.scope, period.period_type) }
            .getOrDefault(emptyList())
        ranking = buildRanking(period, entries, rewards, RumaApi.session?.userId)
    }

    fun loadNotifications(force: Boolean = false) = load(KEY_NOTIFICATIONS) {
        if (notifications != null && !force) return@load
        notifications = RumaRepository.loadNotifications().map { it.toRiderUi() }
    }

    /** Marks one notification read (optimistic; a failure just leaves the dot on). */
    fun markNotificationRead(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            runCatching {
                ensureSignedIn()
                RumaRepository.markNotificationRead(id)
                notifications = notifications?.map { if (it.id == id) it.copy(unread = false) else it }
            }
        }
    }

    fun markAllNotificationsRead() {
        if (notifications?.any { it.unread } != true) return
        viewModelScope.launch {
            try {
                ensureSignedIn()
                RumaRepository.markAllNotificationsRead()
                notifications = notifications?.map { it.copy(unread = false) }
                dashboard = dashboard.copy(unreadCount = 0)
                message = "Notificaciones marcadas como leídas"
            } catch (e: Exception) {
                message = e.message ?: "No se pudieron marcar como leídas."
            }
        }
    }

    // ---- The stop in progress ----

    /** The rider tapped "Navegar" on a specific stop of the route. */
    fun selectStop(pickupId: String?) {
        selectedId = pickupId
        resetNavigation()
    }

    private fun resetNavigation() {
        navJob?.cancel()
        navPolicy.reset()
        navRoad = null
        navPickupId = null
        navKey = null
        navLoading = false
        navError = null
        offRouteFixes = 0
    }

    fun retryNavigation() { navHere?.let { onNavPosition(it, force = true) } }

    /** Leaving for the stop: the family's map switches to "va en camino". */
    fun startNavigation() {
        val p = activePickup ?: return
        // Pin the stop before anything else. Entered from "Iniciar ruta" there is no
        // selection, so activePickup falls back to assigned.first() — and a street
        // route still in flight can reorder that list, moving the rider's destination
        // to a stop the PATCH below never touched.
        selectedId = p.id
        if (p.rider_id != null && p.status == "assigned") advance(p, "en_route")
    }

    /**
     * Each position on the navigation screen: flips the stop to "arriving" near the
     * door (the family gets told) and re-asks the street path once the rider has
     * moved far enough for the old one to be wrong.
     */
    fun onNavPosition(here: RoutePlanner.LatLng, force: Boolean = false) {
        origin = here
        navHere = here
        val p = activePickup?.takeIf { it.rider_id != null } ?: return
        val dest = p.latLng()?.toLL() ?: return
        if (p.status == "en_route" && RoutePlanner.distanceKm(here, dest) <= ARRIVING_KM) advance(p, "arriving")
        val key = "${p.id}:${dest.lat}:${dest.lng}:$vehicle"
        if (navKey != key) { resetNavigation(); navKey = key }
        navPickupId = p.id
        val away = navRoad?.let { RouteProgress.locate(here, it.path)?.offRouteKm ?: 0.0 } ?: 0.0
        offRouteFixes = if (away > 0.06) offRouteFixes + 1 else 0
        val ticket = navPolicy.begin(key, SystemClock.elapsedRealtime(), offRouteFixes >= 2, force) ?: return
        navLoading = true
        navJob = viewModelScope.launch {
            val result = StreetRouter.route(listOf(here, dest), vehicle)
            if (!navPolicy.complete(ticket, here, SystemClock.elapsedRealtime(), result != null)) return@launch
            navLoading = false
            navError = if (result == null) "No se pudo actualizar la ruta. Reintentaremos automáticamente." else null
            if (result != null) navRoad = result
        }
    }

    /** At the door: counting starts from what the family declared. */
    fun arrive() {
        val p = activePickup?.takeIf { it.rider_id != null } ?: return
        confirmQty = p.pickup_items.mapNotNull { item ->
            item.materials?.id?.let { it to (item.confirmed_quantity ?: item.reported_quantity) }
        }.toMap()
        confirmCond = p.pickup_items.mapNotNull { item ->
            item.materials?.id?.let { it to (item.condition ?: "limpia") }
        }.toMap()
        if (p.status != "collecting") advance(p, "collecting")
    }

    /** +/- on the confirm screen (never below zero). */
    fun adjustConfirmed(materialId: String, delta: Int) {
        val current = confirmQty[materialId] ?: return
        confirmQty = confirmQty + (materialId to (current + delta).coerceIn(0, 999))
    }

    /** Status step (en_route/arriving/collecting): optimistic, trg_pickup_guard has the last word. */
    private fun advance(p: PickupDto, status: String) {
        val id = p.id ?: return
        assigned = assigned.map { if (it.id == id) it.copy(status = status) else it }
        viewModelScope.launch {
            runCatching { ensureSignedIn(); RumaRepository.setPickupStatus(id, status) }
        }
    }

    // ---- Write actions ----

    /** Claims the visible next stop if it isn't ours yet, then continues to navigation. */
    fun acceptNextStop(onAccepted: () -> Unit) {
        // "The next one" is what the dashboard button offers, so here the fall-through
        // activePickup refuses is the right answer: a stale selection must not hide an
        // open request. The stop-in-progress actions below keep the strict reading.
        val p = activePickup ?: assigned.firstOrNull() ?: available.firstOrNull()
            ?: run { message = "No hay recojos pendientes por ahora."; return }
        val id = p.id ?: return
        if (p.rider_id != null) { onAccepted(); return }   // already mine, just navigate
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                ensureSignedIn()
                if (RumaRepository.acceptPickup(id)) {
                    message = "¡Recojo asignado! En camino."
                    // Optimistic: navigation opens on this stop without waiting for the reload.
                    assigned = inVisitOrder(assigned + p.copy(rider_id = RumaApi.session?.userId, status = "assigned"))
                    available = available.filter { it.id != id }
                    knownStopIds = knownStopIds.orEmpty() + id
                    selectedId = id
                    route = null
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

    /** Writes the counts, completes the pickup → settle books points/earnings server-side. */
    /** limpia | aplastada | sucia, per material; the database applies the factor. */
    fun setCondition(materialId: String, condition: String) {
        confirmCond = confirmCond + (materialId to condition)
    }

    fun confirmCollection(onDone: () -> Unit) {
        val p = activePickup?.takeIf { it.rider_id != null } ?: run { message = "Primero acepta el recojo desde el inicio."; return }
        val id = p.id ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                ensureSignedIn()
                val counts = p.pickup_items.mapNotNull { item ->
                    item.materials?.id?.let { mid -> mid to (confirmQty[mid] ?: item.confirmed_quantity ?: item.reported_quantity) }
                }
                RumaRepository.confirmQuantities(id, counts, confirmCond)
                RumaRepository.completePickup(id)

                val remaining = assigned.count { it.id != id }
                completed = RiderCompletedUiState(
                    stopName = p.generator?.display_name ?: "el vecino",
                    materialsSummary = "${counts.sumOf { it.second }} unidades" +
                        (p.total_weight_kg?.takeIf { it > 0 }?.let { " · %.1f kg".format(Locale.US, it) } ?: ""),
                    // The settle prices them a moment later; don't show a zero as a result.
                    pointsAwarded = (p.generator_points_awarded?.takeIf { it > 0 }
                        ?: p.pickup_items.sumOf { it.points }.takeIf { it > 0 })
                        ?.let { "$it pts" } ?: "Se acreditan en el acopio",
                    earnings = estimate(p.rider_earnings),
                    remaining = when (remaining) {
                        0 -> "No te quedan paradas por ahora"
                        1 -> "Te queda 1 parada en tu ruta"
                        else -> "Te quedan $remaining paradas en tu ruta"
                    },
                    hasNext = remaining > 0,
                )
                message = "¡Recolección confirmada! Ganancia acreditada."
                assigned = assigned.filter { it.id != id }
                knownStopIds = knownStopIds?.minus(id)
                selectedId = null
                confirmQty = emptyMap()
                confirmCond = emptyMap()
                navRoad = null
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

    /** The rider can't make it: the stop goes back to the open board and the family is told. */
    fun releaseActive(onDone: () -> Unit) {
        val p = activePickup?.takeIf { it.rider_id != null } ?: return
        val id = p.id ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                ensureSignedIn()
                RumaRepository.releasePickup(id)
                message = "Liberaste el recojo: volvió al tablero para otro Eco-Rider."
                assigned = assigned.filter { it.id != id }
                knownStopIds = knownStopIds?.minus(id)
                selectedId = null
                navRoad = null
                route = null
                refresh()
                onDone()
            } catch (e: Exception) {
                message = e.message ?: "No se pudo liberar el recojo."
            } finally {
                busy = false
            }
        }
    }

    /**
     * Three reads for one decision: what is still in the vehicle, where it can go and
     * how the past loads ended. A fresh list arrives fully ticked, because the usual
     * trip is the whole load; unticking is for the rider who splits it in two.
     */
    fun loadDelivery(force: Boolean = false) = load(KEY_DELIVERY) {
        if (delivery != null && !force) return@load
        undelivered = RumaRepository.loadUndeliveredPickups()
        deliveries = runCatching { RumaRepository.loadMyDeliveries() }.getOrDefault(emptyList())
        if (centers.isEmpty()) centers = runCatching { RumaRepository.loadCenters() }.getOrDefault(emptyList())
        val ids = undelivered.mapNotNull { it.id }.toSet()
        pickedStops = if (force || pickedStops.isEmpty()) ids else pickedStops intersect ids
        pickedCenter = pickedCenter?.takeIf { chosen -> centers.any { it.id == chosen } }
            ?: centers.firstOrNull { it.is_open }?.id ?: centers.firstOrNull()?.id
        delivery = buildDelivery(undelivered, centers, deliveries, pickedStops, pickedCenter)
    }

    /**
     * The profile screen used to render its own sample constant, so a demo showed
     * 340 recojos next to a stats screen reading 356 off the database.
     */
    fun loadProfile(force: Boolean = false) = load(KEY_PROFILE) {
        if (profile != null && !force) return@load
        val row = RumaRepository.loadMyRiderProfile()
        val name = runCatching { RumaRepository.loadMyDisplayName() }.getOrNull()
        profile = buildRiderProfile(row, name, dashboard.zone)
    }

    fun toggleDeliveryStop(pickupId: String) {
        pickedStops = if (pickupId in pickedStops) pickedStops - pickupId else pickedStops + pickupId
        delivery = buildDelivery(undelivered, centers, deliveries, pickedStops, pickedCenter)
    }

    fun pickDeliveryCenter(centerId: String) {
        pickedCenter = centerId
        delivery = buildDelivery(undelivered, centers, deliveries, pickedStops, pickedCenter)
    }

    /**
     * Opens the delivery. The RPC re-checks ownership and completion while holding the
     * stops locked, so one that slipped into another load is refused here instead of
     * riding along: the rider reads that message and the list comes back without it.
     */
    fun sendDelivery(onSent: () -> Unit) {
        val center = pickedCenter ?: return
        val stops = pickedStops.toList()
        if (busy || stops.isEmpty()) return
        busy = true
        viewModelScope.launch {
            try {
                ensureSignedIn()
                RumaRepository.createDelivery(center, stops)
                message = "Entrega abierta. Lleva la carga al acopio y pide el pesaje."
                onSent()
            } catch (e: Exception) {
                message = e.message ?: "No se pudo abrir la entrega."
            } finally {
                busy = false
                loadDelivery(force = true)
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
        const val KEY_BOARD = "board"
        const val KEY_DELIVERY = "delivery"
        const val KEY_PROFILE = "profile"
    }
}

/** The rider is on the move or at the door for this stop. */
private val IN_FLIGHT = setOf("en_route", "arriving", "collecting")
/** The rider is at this door. "en_route" is only intent, and it stays behind when they back out. */
private val AT_DOOR = setOf("arriving", "collecting")
/** A stop "at the door" farther than this from the rider is a stale flag, not where they are. */
private const val UNDER_WAY_KM = 1.0
/** 300 m: farther than that, the registered address is not where the pickup was asked. */
private const val GPS_ADDRESS_KM = 0.3
/** 150 m: close enough that "está llegando" is true on SJL's short blocks. */
private const val ARRIVING_KM = 0.15
/** Parking, knocking, counting: minutes per stop on top of the ride. */
private const val SERVICE_MIN_PER_STOP = 4

// ---- Mapping helpers ----

/** A missing total shows a dash: an invented number is worse than an absent one. */
private fun buildRiderProfile(
    row: RiderProfileDto?,
    name: String?,
    zone: String,
): RiderProfileUiState = RiderProfileUiState(
    name = name ?: "Eco-Rider",
    roleZone = zone.ifBlank { "Eco-Rider" },
    vehicle = vehicleLabel(row?.vehicle_type),
    rating = row?.rating?.let { "%.1f".format(Locale.US, it) } ?: "—",
    riderId = row?.rider_code ?: "—",
    collections = row?.total_collections?.let { groupInt(it) } ?: "—",
    recycled = row?.total_recycled_kg?.let { "%s kg".format(groupInt(it.toInt())) } ?: "—",
    co2 = row?.co2_avoided_kg?.let { "%s kg".format(groupInt(it.toInt())) } ?: "—",
)

private fun buildDelivery(
    stops: List<PickupDto>,
    centers: List<CollectionCenterDto>,
    past: List<DeliveryDto>,
    picked: Set<String>,
    pickedCenter: String?,
): RiderDeliveryUiState {
    val chosen = stops.filter { (it.id ?: "") in picked }
    val fees = chosen.sumOf { it.rider_earnings ?: 0.0 }
    return RiderDeliveryUiState(
        stops = stops.map { p ->
            val id = p.id ?: ""
            DeliveryStopUi(
                id = id,
                code = p.code ?: "",
                name = p.generator?.display_name ?: "Vecino",
                address = p.stopAddress(),
                materials = itemsSummary(p.pickup_items),
                earnings = "≈ " + soles(p.rider_earnings ?: 0.0),
                selected = id in picked,
            )
        },
        centers = centers.map { c ->
            DeliveryCenterUi(
                id = c.id,
                name = c.name,
                address = c.address_line ?: "Sin direccion registrada",
                isOpen = c.is_open,
                selected = c.id == pickedCenter,
            )
        },
        recent = past.map { d ->
            DeliveryRecordUi(
                id = d.id,
                status = d.status,
                code = d.code ?: "-",
                statusLabel = deliveryStatusLabel(d.status),
                centerName = d.center?.name ?: "Acopio",
                detail = deliveryDetail(d),
                // Nada esta cobrado hasta la liquidacion; un cero antes de eso engana.
                amount = d.rider_amount?.takeIf { d.status == "liquidada" }?.let { soles(it) } ?: "-",
            )
        },
        selectedLabel = when (chosen.size) {
            0 -> "Nada seleccionado"
            1 -> "1 recojo"
            else -> "${chosen.size} recojos"
        },
        selectedEarnings = if (chosen.isEmpty()) "Marca los recojos que vas a entregar"
        else "≈ ${soles(fees)} · se paga al pesar",
        canSend = chosen.isNotEmpty() && pickedCenter != null,
        hint = when {
            chosen.isEmpty() -> "Marca al menos un recojo."
            pickedCenter == null -> "Elige el acopio al que vas."
            else -> "S/ 0,35 por kg pesado + S/ 6 por ruta de 12 recojos. El acopio pesa la carga: ahi se paga todo."
        },
    )
}

private fun deliveryStatusLabel(status: String): String = when (status) {
    "en_camino" -> "En camino al acopio"
    "recibida" -> "Recibida · esperando pesaje"
    "pesada" -> "Pesada · esperando liquidacion"
    "liquidada" -> "Liquidada · te pagaron"
    "rechazada" -> "Rechazada por el acopio"
    else -> status
}

private fun deliveryDetail(d: DeliveryDto): String = listOfNotNull(
    d.delivery_pickups.size.takeIf { it > 0 }?.let { if (it == 1) "1 recojo" else "$it recojos" },
    d.total_kg?.takeIf { it > 0 }?.let { "%.1f kg".format(Locale.US, it) },
    (d.settled_at ?: d.departed_at)?.let { RumaTime.dayMonth(it) },
).joinToString(" · ").ifBlank { "Sin detalle" }

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun soles(v: Double): String = "S/%.2f".format(Locale.US, v)

/** rider_earnings is only an estimate until the acopio weighs the load. */
private fun estimate(v: Double?): String = "≈ ${soles(v ?: 0.0)} · se paga al pesar"

private fun Pair<Double, Double>.toLL() = RoutePlanner.LatLng(first, second)

/**
 * The open board, nearest first — the order that matters to someone who has to
 * ride there. The list used to be invisible: only the oldest request was offered,
 * on the dashboard, even when a closer one was waiting two blocks away.
 */
private fun buildBoard(available: List<PickupDto>, origin: RoutePlanner.LatLng?): RiderBoardUiState {
    val rows = available.map { p ->
        val at = p.latLng()?.toLL()
        val km = at?.let { stop -> origin?.let { RoutePlanner.distanceKm(it, stop) } }
        BoardStopUi(
            pickupId = p.id.orEmpty(),
            family = p.generator?.display_name ?: "Vecino",
            address = p.stopAddress(),
            items = itemsSummary(p.pickup_items).ifBlank { "Residuos por confirmar" },
            window = windowText(p.window_label, p.scheduled_date).ifBlank { "Sin ventana horaria" },
            distance = km?.let { RoutePlanner.distanceLabel(it) } ?: "Sin ubicación",
            eta = km?.let { "~${RoutePlanner.etaMinutes(it)} min" } ?: "",
            km = km,
            waiting = p.created_at?.let { RumaTime.relative(it) }.orEmpty(),
            lat = at?.lat,
            lng = at?.lng,
        )
    }.sortedWith(compareBy(nullsLast()) { it.km })
    return RiderBoardUiState(stops = rows, here = origin)
}

private fun shiftLabel(vehicle: String?, onShift: Boolean): String =
    "${vehicleLabel(vehicle)} · ${if (onShift) "En turno" else "Fuera de turno"}"

/** "Sales hoy 3:00 p. m." — the promise the rider made to themselves. */
private fun shiftPlanLabel(atMillis: Long?): String {
    val at = atMillis?.takeIf { it > System.currentTimeMillis() } ?: return ""
    val sameDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) ==
        Calendar.getInstance().apply { timeInMillis = at }.get(Calendar.DAY_OF_YEAR)
    return "Sales ${if (sameDay) "hoy" else "mañana"} ${RumaTime.clockAt(at)}"
}

private fun buildDashboard(
    prev: RiderDashboardUiState,
    name: String?,
    next: PickupDto?,
    /** The rider's own stops still to do (board requests are not their work yet). */
    pending: Int,
    onBoard: Int,
    earnings: List<RiderEarningDto>,
    profile: RiderProfileDto?,
    unread: Int,
    origin: RoutePlanner.LatLng?,
    onShift: Boolean,
    doneToday: Int,
): RiderDashboardUiState {
    val nextIsMine = next?.rider_id != null
    val todayRows = earnings.filter { localDay(it.created_at) == today() }
    val monthTotal = earnings.filter { localDay(it.created_at).startsWith(currentMonthPrefix()) }.sumOf { it.amount }
    val goal = profile?.monthly_goal?.takeIf { it > 0 }
    val km = next?.latLng()?.toLL()?.let { stop -> origin?.let { RoutePlanner.distanceKm(it, stop) } }
    return prev.copy(
        monthEarnings = soles(monthTotal),
        goalProgress = goal?.let { (monthTotal / it).coerceIn(0.0, 1.0).toFloat() } ?: 0f,
        goalPctText = goal?.let { "${((monthTotal / it) * 100).toInt().coerceAtMost(100)}% de tu meta" } ?: "",
        goalText = goal?.let { "Meta: ${soles(it)}" } ?: "Sin meta definida",
        riderName = name ?: prev.riderName,
        zone = shiftLabel(profile?.vehicle_type, onShift),
        onShift = onShift,
        shiftPlan = shiftPlanLabel(pe.ruma.app.data.RumaPrefs.shiftStartAt),
        onBoard = "$onBoard",
        boardHint = when {
            onBoard == 0 -> "Sin solicitudes abiertas ahora mismo"
            onBoard == 1 -> "1 recojo esperando un Eco-Rider"
            else -> "$onBoard recojos esperando un Eco-Rider"
        },
        unreadCount = unread,
        pickupsTotal = "${doneToday + pending}",
        completed = "$doneToday/${doneToday + pending}",
        earnedToday = soles(todayRows.sumOf { it.amount }),
        hasNextStop = next != null,
        nextStopIsMine = nextIsMine,
        nextStopName = next?.generator?.display_name ?: "Sin recojos pendientes",
        nextStopAddress = next?.stopAddress() ?: "Te avisaremos cuando haya solicitudes",
        nextStopEta = km?.let { "Llegas ${RumaTime.clockPlus(RoutePlanner.etaMinutes(it))}" }.orEmpty(),
        nextStopDistance = when {
            next == null -> ""
            // A pickup booked for tomorrow read "Disponible ahora" at night and the rider went (2026-11-17).
            next.scheduled_date?.let { it > today() } == true ->
                (next.window_label ?: "Otro día") + if (nextIsMine) "" else " · tócala para tomarla"
            !nextIsMine -> "Disponible · tócala para tomarla"
            km != null -> "A ${RoutePlanner.distanceLabel(km)}"
            else -> "Asignado"
        },
        nextStopChips = next?.pickup_items.orEmpty().mapNotNull { item ->
            val qty = item.confirmed_quantity ?: item.reported_quantity
            item.materials?.code?.let { "$qty ${shortMaterial(it)}" }
        },
        rankingTitle = prev.rankingTitle.ifBlank { "Ranking de riders" },
        rankingSubtitle = prev.rankingSubtitle.ifBlank { "Mira tu posición de esta semana" },
    )
}

private fun PickupDto.toConfirmUi(qty: Map<String, Int>, cond: Map<String, String>): RiderConfirmUiState {
    val mats = pickup_items.map {
        val mid = it.materials?.id.orEmpty()
        ConfirmMaterialUi(
            name = it.materials?.name ?: "Material",
            code = it.materials?.code.orEmpty(),
            detail = "La familia declaró ${it.reported_quantity}",
            points = "+${it.points} pts",
            materialId = mid,
            declared = it.reported_quantity,
            confirmed = qty[mid] ?: it.confirmed_quantity ?: it.reported_quantity,
            condition = cond[mid] ?: it.condition ?: "limpia",
        )
    }
    val totalPts = (generator_points_awarded ?: 0).takeIf { it > 0 } ?: pickup_items.sumOf { it.points }
    return RiderConfirmUiState(
        stopName = generator?.display_name ?: "Recolector",
        address = stopAddress(),
        streak = windowText(window_label, scheduled_date).ifBlank { "Recojo programado" },
        materials = mats.ifEmpty {
            listOf(ConfirmMaterialUi("Residuos declarados", "Verifica el material en el punto", ""))
        },
        totalWeight = total_weight_kg?.takeIf { it > 0 }?.let { "%.1f kg".format(Locale.US, it) } ?: "—",
        // Points are priced server-side on settle: "0 pts" before that is not a zero,
        // it is a number that does not exist yet.
        totalPoints = if (totalPts > 0) "$totalPts pts" else "Se calculan al confirmar",
        earnings = estimate(rider_earnings),
    )
}

private fun PickupDto.toNavigationUi(road: StreetRouter.Route?, position: String): RiderNavigationUiState = RiderNavigationUiState(
    stopName = generator?.display_name ?: "Recolector",
    address = stopAddress(),
    distance = distance_m?.let { RoutePlanner.distanceLabel(it / 1000.0) } ?: "—",
    eta = eta_minutes?.let { "$it min" } ?: "—",
    instruction = address?.reference?.takeIf { it.isNotBlank() } ?: "Dirígete al punto de recojo",
    position = position,
    statusLabel = when (status) {
        "arriving" -> "Estás llegando"
        "collecting" -> "Contando en la puerta"
        else -> "En camino"
    },
    atDoor = status == "collecting",
    materials = pickup_items.mapNotNull { item ->
        val qty = item.confirmed_quantity ?: item.reported_quantity
        item.materials?.code?.let { "$qty ${shortMaterial(it)}" }
    },
    lat = latLng()?.first,
    lng = latLng()?.second,
    path = road?.path.orEmpty(),
    roadKm = road?.distanceKm,
    roadMinutes = road?.minutes,
    phone = generator?.phone?.takeIf { it.isNotBlank() },
)

/**
 * Where the stop actually is: the pickup's own coordinates when the app recorded
 * them, otherwise the generator's saved address. Without this fallback most stops
 * have no pin at all.
 */
private fun PickupDto.latLng(): Pair<Double, Double>? {
    val lat = latitude ?: address?.latitude
    val lng = longitude ?: address?.longitude
    return if (lat != null && lng != null) lat to lng else null
}

/** The stop being worked right now: pinned first, everything else is ordered from the rider. */
private fun PickupDto.underWay(here: RoutePlanner.LatLng?): Boolean {
    if (status !in AT_DOOR) return false
    val at = latLng()?.toLL() ?: return true
    return here == null || RoutePlanner.distanceKm(here, at) <= UNDER_WAY_KM
}

/**
 * The address line the rider reads. A pickup asked with the phone's GPS can be far
 * from the recolector's registered address (Cartavio vs. Av. Larco, 30 km): then
 * the pin is the truth, and the street line would send the rider to the wrong town.
 */
internal fun PickupDto.stopAddress(): String {
    val line = address?.address_line?.takeIf { it.isNotBlank() } ?: "Dirección no registrada"
    val lat = latitude ?: return line
    val lng = longitude ?: return line
    val aLat = address?.latitude ?: return line
    val aLng = address?.longitude ?: return line
    val apart = RoutePlanner.distanceKm(RoutePlanner.LatLng(lat, lng), RoutePlanner.LatLng(aLat, aLng))
    return if (apart > GPS_ADDRESS_KM) "Ubicación GPS del recolector" else line
}

private fun RiderProfileDto.position(): RoutePlanner.LatLng? {
    val lat = current_latitude ?: return null
    val lng = current_longitude ?: return null
    return RoutePlanner.LatLng(lat, lng)
}

/**
 * The phone's calendar day for a database stamp. Stamps come in UTC, so a stop paid
 * at 7:44 pm in Lima reads "2026-11-12T00:44": matching the prefix filed it under
 * tomorrow, and "Ganado hoy" said S/0.00 right after a S/7.00 stop.
 */
internal fun localDay(ts: String): String =
    RumaTime.millis(ts)?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: ts.take(10)

// ---- Route ----

/**
 * Builds the stop list from the operator's planned route when there is one, else
 * from the rider's own assigned pickups (already in visiting order). With a
 * [street] answer the totals and the drawn path are the road's, not the crow's.
 */
private fun buildRoute(
    planned: RouteDto?,
    assigned: List<PickupDto>,
    origin: RoutePlanner.LatLng?,
    street: StreetRouter.Route?,
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
    val stops = fromPlan.ifEmpty {
        assigned.mapIndexed { i, p -> p.toStopUi(number = (i + 1).toString(), eta = null, done = false) }
    }.withStreetEtas(street)
    val pins = stops.mapNotNull { it.marker }.map { RoutePlanner.LatLng(it.lat, it.lng) }
    val tourKm = RoutePlanner.tourLengthKm(origin, pins, pins.indices.toList())
    val distanceM = street?.distanceKm?.times(1000.0)
        ?: planned?.total_distance_m
        ?: tourKm.takeIf { it > 0 }?.let { it * 1000.0 }
    val minutes = street?.let { it.minutes + stops.size * SERVICE_MIN_PER_STOP }
        ?: planned?.total_duration_min
        ?: tourKm.takeIf { it > 0 }?.let { km -> RoutePlanner.etaMinutes(km) + stops.size * SERVICE_MIN_PER_STOP }
        ?: stops.size.takeIf { it > 0 }?.let { it * 15 }
    val estimated = assigned.sumOf { it.rider_earnings ?: 0.0 }

    return RiderRouteUiState(
        optimizedNote = when {
            planned != null -> "Ruta planificada · ${planned.total_stops} paradas"
            stops.isEmpty() -> "Aún no tienes paradas asignadas"
            origin == null && stops.size >= 2 -> "Sin tu ubicación: orden aproximado"
            street != null && stops.size >= 2 -> "Ruta por calles · ${stops.size} paradas"
            stops.size >= 2 -> "Orden aproximado · ${stops.size} paradas"
            else -> "1 parada asignada"
        },
        stops = stops,
        totalDistance = distanceM?.let { "%.1f km total".format(Locale.US, it / 1000.0) } ?: "—",
        totalTime = minutes?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" } ?: "—",
        // Only with a street answer: an hour built on crow-flies would be a lie.
        finishLabel = street?.let { "Terminas ~${RumaTime.clockPlus(minutes ?: it.minutes)}" }.orEmpty(),
        estimatedEarnings = if (estimated > 0) soles(estimated) else "—",
        markers = stops.mapNotNull { it.marker },
        path = street?.path.orEmpty(),
        here = origin,
    )
}

/**
 * Puts a real arrival time on every stop: each hop the router returned, plus the
 * minutes spent at each door before it. The last `hopMinutes.size` stops are the
 * ones still ahead — a stop already under way has no hop of its own.
 */
private fun List<RouteStopUi>.withStreetEtas(street: StreetRouter.Route?): List<RouteStopUi> {
    val hops = street?.hopMinutes.orEmpty()
    if (hops.isEmpty() || hops.size > size) return this
    val offset = size - hops.size
    var running = 0
    return mapIndexed { i, stop ->
        if (i < offset || stop.done) return@mapIndexed stop
        running += hops[i - offset] + if (i > offset) SERVICE_MIN_PER_STOP else 0
        stop.copy(eta = "${running} min", arrival = "Llegas ${RumaTime.clockPlus(running)}")
    }
}

private fun PickupDto.toStopUi(number: String, eta: String?, done: Boolean): RouteStopUi = RouteStopUi(
    number = number,
    name = generator?.display_name ?: "Recolector",
    subtitle = stopAddress(),
    eta = eta,
    material = itemsSummary(pickup_items),
    streak = windowText(window_label, scheduled_date).takeIf { it.isNotBlank() },
    highlighted = status in IN_FLIGHT,
    done = done,
    marker = latLng()?.let { (lat, lng) ->
        RouteMarkerUi(lat, lng, "$number · ${generator?.display_name ?: "Parada"}")
    },
    pickupId = id,
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
            name = p.generator?.display_name ?: "Recolector",
            detail = listOfNotNull(
                itemsSummary(p.pickup_items).takeIf { p.pickup_items.isNotEmpty() },
                p.address?.let { p.stopAddress() },
            ).joinToString(" · ").ifBlank { "Recojo sin detalle" },
            earnings = p.rider_earnings?.takeIf { it > 0 && p.status == "completed" }?.let { "≈ ${soles(it)}" } ?: "—",
            time = RumaTime.relative(ts),
            status = status,
            statusLabel = label,
            note = p.notes ?: p.cancel_reason,
            timestamp = ts,
        )
    }
    return RiderHistoryUiState(
        sections = entries.groupBy { RumaTime.dayLabel(it.timestamp) }
            .map { (label, rows) -> RiderHistorySectionUi(label.ifBlank { "ANTERIORES" }, rows) },
    )
}

// ---- Earnings ----

private fun buildEarnings(rows: List<RiderEarningDto>, profile: RiderProfileDto?, doneToday: Int): RiderEarningsUiState {
    val month = currentMonthPrefix()
    val monthRows = rows.filter { localDay(it.created_at).startsWith(month) }
    val monthTotal = monthRows.sumOf { it.amount }
    val goal = profile?.monthly_goal?.takeIf { it > 0 }
    val todayRows = rows.filter { localDay(it.created_at) == today() }

    // Last 7 calendar days, oldest first: a week reads left to right, like the chart.
    val byDay = rows.groupBy { localDay(it.created_at) }
    val week = (6 downTo 0).map { back ->
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        Triple(back, cal, byDay[key].orEmpty())
    }
    val best = week.maxOfOrNull { (_, _, r) -> r.sumOf { it.amount } } ?: 0.0
    val days = week.map { (back, cal, dayRows) ->
        val amount = dayRows.sumOf { it.amount }
        EarningDayUi(
            day = when (back) { 0 -> "Hoy"; 1 -> "Ayer"; else -> weekdayShort(cal) },
            // A quiet day is quiet, not an error: no red tag, just a muted dash.
            recs = if (dayRows.isEmpty()) "—" else "${dayRows.size} recojo${if (dayRows.size == 1) "" else "s"}",
            amount = soles(amount),
            fraction = if (best <= 0) 0f else (amount / best).toFloat(),
            isToday = back == 0,
            inactive = dayRows.isEmpty(),
        )
    }
    val weekTotal = week.sumOf { (_, _, r) -> r.sumOf { it.amount } }

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
        todaySub = "$doneToday recojo${if (doneToday == 1) "" else "s"} completado${if (doneToday == 1) "" else "s"} · se pagan al pesar",
        days = days,
        weekTotal = soles(weekTotal),
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
        StatTileUi(Icons.Filled.Home, RumaColors.Rider, groupInt(profile?.total_collections ?: 0), "recolecciones"),
        StatTileUi(Icons.Filled.Recycling, RumaColors.Gen, "%.0f kg".format(Locale.US, profile?.total_recycled_kg ?: 0.0), "reciclado"),
        StatTileUi(Icons.Filled.Eco, RumaColors.Success, "%.0f kg".format(Locale.US, profile?.co2_avoided_kg ?: 0.0), "CO₂ evitado"),
        StatTileUi(Icons.Filled.Route, RumaColors.Rider, "${groupInt(earnings.size)}", "viajes pagados"),
        StatTileUi(
            Icons.Filled.Star, RumaColors.Money,
            profile?.rating?.let { "%.1f".format(Locale.US, it) } ?: "—",
            if ((profile?.rating_count ?: 0) > 0) "de ${profile?.rating_count} reseñas" else "sin reseñas",
        ),
        StatTileUi(Icons.Filled.AccountBalanceWallet, RumaColors.Money, soles(profile?.earnings_lifetime ?: 0.0), "ganado"),
    )

    // Pickups per day over the last 7 days, scaled against the busiest day.
    val byDay = earnings.groupBy { localDay(it.created_at) }
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
    period: pe.ruma.app.data.LeaderboardPeriodDto,
    entries: List<pe.ruma.app.data.LeaderboardEntryDto>,
    rewards: List<pe.ruma.app.data.RankingRewardDto>,
    me: String?,
): RiderRankingUiState {
    val podiumColors = listOf(Gold, Silver, Bronze)
    val podium = entries.take(3).mapIndexed { i, e ->
        val mine = e.user_id == me
        // How far the rider ahead is — measured against *them*, not against oneself.
        val ahead = entries.getOrNull(i - 1)?.points?.minus(e.points)?.takeIf { it > 0 }
        PodiumUi(
            rank = e.rank,
            name = e.display_name ?: "Rider",
            sub = if (e.streak_days > 0) "Racha ${e.streak_days} días" else "—",
            points = groupInt(e.points),
            // Nothing computes the weekly delta yet: "+0 pts" reads as a result.
            monthEarnings = when {
                e.delta > 0 -> "+${e.delta} pts esta semana"
                e.delta < 0 -> "${e.delta} pts esta semana"
                else -> ""
            },
            medalColor = if (mine) RumaColors.Rider else podiumColors.getOrElse(i) { RumaColors.Rider },
            crown = e.rank == 1,
            isYou = mine,
            gap = if (mine && ahead != null) "A $ahead pts del ${e.rank - 1}°" else null,
        )
    }
    return RiderRankingUiState(
        zone = period.block_label?.let { "Ranking · $it" } ?: "Ranking de riders",
        periodLabel = listOfNotNull(period.period_start, period.period_end)
            .takeIf { it.size == 2 }
            ?.let { "Del ${RumaTime.dayMonth(it[0])} al ${RumaTime.dayMonth(it[1])}" }
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
                medalColor = podiumColors.getOrElse(i) { RumaColors.Rider },
            )
        },
        prizeNote = rewards.firstOrNull()?.description ?: "",
    )
}

// ---- Notifications ----

private fun NotificationDto.toRiderUi(): RiderNotifUi {
    val (icon, tint, bg) = when (kind) {
        "rider_assigned", "pickup_update", "rider_arriving" ->
            Triple(Icons.AutoMirrored.Filled.DirectionsBike, RumaColors.Rider, RumaColors.RiderTint)
        "ranking" -> Triple(Icons.Filled.EmojiEvents, RumaColors.Money, RumaColors.MoneyTint)
        "points_earned", "redemption" ->
            Triple(Icons.Filled.AccountBalanceWallet, RumaColors.Success, RumaColors.SuccessTint)
        "challenge" -> Triple(Icons.Filled.Celebration, RumaColors.Streak, RumaColors.StreakTint)
        "promo" -> Triple(Icons.Filled.BarChart, RumaColors.Gen, RumaColors.GenTint)
        else -> Triple(Icons.Filled.Notifications, RumaColors.Text2, RumaColors.Border)
    }
    return RiderNotifUi(
        id = id,
        icon = icon,
        iconTint = tint,
        iconBg = bg,
        title = title,
        body = body.orEmpty(),
        time = RumaTime.relative(created_at),
        unread = !is_read,
        aboutPickup = reference_type == "pickup",
    )
}
