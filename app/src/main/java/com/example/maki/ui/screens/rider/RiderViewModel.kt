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
import com.example.maki.data.CollectionCenterDto
import com.example.maki.data.DeliveryDto
import com.example.maki.data.MakiApi
import com.example.maki.data.MakiRepository
import com.example.maki.data.NotificationDto
import com.example.maki.data.PickupDto
import com.example.maki.data.RiderEarningDto
import com.example.maki.data.RiderProfileDto
import com.example.maki.data.RiderShiftWorker
import com.example.maki.data.RoutePlanner
import com.example.maki.data.RouteDto
import com.example.maki.data.StreetRouter
import com.example.maki.data.UserAchievementDto
import com.example.maki.ui.screens.generator.MakiTime
import com.example.maki.ui.screens.generator.groupInt
import com.example.maki.ui.screens.generator.itemsSummary
import com.example.maki.ui.screens.generator.shortMaterial
import com.example.maki.ui.screens.generator.surnameInitial
import com.example.maki.ui.screens.generator.vehicleLabel
import com.example.maki.ui.theme.MakiColors
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
        RiderDashboardUiState(riderName = MakiRepository.cachedDisplayName().orEmpty()),
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

    /** Street path for the leg being navigated, and where/for which stop it was asked. */
    private var navRoad by mutableStateOf<StreetRouter.Route?>(null)
    private var navFrom: RoutePlanner.LatLng? = null
    private var navPickupId: String? = null

    /** Last known rider position (heartbeat / navigation); every order starts here. */
    private var origin: RoutePlanner.LatLng? = null
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
            return p.toNavigationUi(
                road = navRoad.takeIf { navPickupId == p.id },
                position = if (index >= 0 && assigned.size > 1) "Parada ${index + 1} de ${assigned.size}" else "",
            )
        }

    val confirm: RiderConfirmUiState
        get() = activePickup?.takeIf { it.rider_id != null }?.toConfirmUi(confirmQty) ?: RiderConfirmUiState()

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

    /** Dashboard data; also polled while the dashboard is open, so dispatches show up. */
    fun refresh() {
        load(KEY_DASHBOARD) {
            val profile = runCatching { MakiRepository.loadMyRiderProfile() }.getOrNull()
            vehicle = profile?.vehicle_type ?: vehicle
            val mine = MakiRepository.loadMyAssignedPickups()
            announceChanges(mine)
            assigned = keepOrder(mine)
            available = runCatching { MakiRepository.loadAvailablePickups() }.getOrDefault(emptyList())
            val earningRows = runCatching { MakiRepository.loadRiderEarnings() }.getOrDefault(emptyList())
            val name = runCatching { MakiRepository.loadMyDisplayName() }.getOrNull()
            val unread = runCatching { MakiRepository.loadNotifications().count { !it.is_read } }
                .getOrDefault(dashboard.unreadCount)
            onShift = profile?.is_available ?: onShift
            board = buildBoard(available, origin)
            dashboard = buildDashboard(
                dashboard, name, activePickup, assigned.size, available.size, earningRows, profile, unread, origin,
                onShift,
            )
        }
    }

    /** The open board, re-read on demand (the screen pulls, the dashboard polls). */
    fun loadBoard(force: Boolean = false) = load(KEY_BOARD) {
        if (board != null && !force) return@load
        available = MakiRepository.loadAvailablePickups()
        board = buildBoard(available, origin)
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
                if (MakiRepository.acceptPickup(pickupId)) {
                    message = "¡Recojo tomado! Ya está en tu ruta."
                    available = available.filter { it.id != pickupId }
                    board = buildBoard(available, origin)
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
     * ([com.example.maki.data.RiderShiftWorker]). Passing null drops the plan.
     */
    fun planShift(atMillis: Long?, context: android.content.Context) {
        if (atMillis == null) {
            RiderShiftWorker.cancel(context)
            dashboard = dashboard.copy(shiftPlan = "")
            message = "Quitamos tu salida programada."
        } else {
            RiderShiftWorker.schedule(context, atMillis)
            dashboard = dashboard.copy(shiftPlan = shiftPlanLabel(atMillis))
            message = "Listo: tu turno empieza solo a las ${MakiTime.clockAt(atMillis)}."
        }
        // Best-effort: the reminder already works offline, the database copy is for
        // whoever plans the day on the other side.
        viewModelScope.launch {
            runCatching { ensureSignedIn(); MakiRepository.setRiderShiftStart(atMillis) }
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
                MakiRepository.setRiderAvailability(value)
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

    /** Nearest neighbour + 2-opt from where the rider stands; a stop under way stays first. */
    private fun inVisitOrder(stops: List<PickupDto>): List<PickupDto> =
        RoutePlanner.optimize(origin, stops) { it.latLng()?.toLL() }
            .sortedByDescending { it.status in IN_FLIGHT }

    /** Heartbeat fix. Cheap on purpose (every 15 s): no network, no reorder. */
    fun onLocation(fix: RoutePlanner.LatLng) { origin = fix }

    // ---- Sections (fetched once, then served from memory) ----

    fun loadRoute(from: RoutePlanner.LatLng? = null, force: Boolean = false) = load(KEY_ROUTE) {
        if (from != null) origin = from
        if (route != null && !force) return@load
        plannedRoute = runCatching { MakiRepository.loadTodayRoute(today()) }.getOrNull()
        assigned = inVisitOrder(MakiRepository.loadMyAssignedPickups())
        knownStopIds = assigned.mapNotNull { it.id }.toSet()
        route = buildRoute(plannedRoute, assigned, origin, street = null)   // instant, crow-flies
        streetTrip()                                                        // then the street-true order
    }

    /** A GPS fix landed after the first paint: redo the trip from where the rider is. */
    fun reorderRoute(from: RoutePlanner.LatLng?) {
        if (from == null) return
        origin = from
        if (route != null) load(KEY_ROUTE) { streetTrip() }
    }

    /**
     * Asks the street router for the fastest order through the rider's stops (by
     * road travel time, not distance as the crow flies) and the path to draw. An
     * operator's planned route keeps its order — they decided it — and only gets
     * its streets drawn. Best-effort: on failure the local order stays.
     */
    private suspend fun streetTrip() {
        val planned = plannedRoute?.takeIf { it.route_stops.isNotEmpty() }
        if (planned != null) {
            val pts = listOfNotNull(origin) + route?.markers.orEmpty().map { RoutePlanner.LatLng(it.lat, it.lng) }
            val street = StreetRouter.route(pts, vehicle) ?: return
            route = buildRoute(planned, assigned, origin, street)
            return
        }
        val head = assigned.firstOrNull { it.status in IN_FLIGHT }
        val rest = assigned.filter { it !== head }
        val located = rest.filter { it.latLng() != null }
        if (located.isEmpty()) return
        val from = head?.latLng()?.toLL() ?: origin ?: located.first().latLng()!!.toLL()
        val stops = assigned.mapNotNull { it.id }
        val trip = StreetRouter.trip(from, located.map { it.latLng()!!.toLL() }, vehicle) ?: return
        if (trip.visitOrder.size != located.size) return
        // Stops appeared or left while the router thought: its order and its drawn path
        // describe a route that no longer exists. Drop it — the next fix re-asks.
        if (assigned.mapNotNull { it.id } != stops) return
        // The router can answer after the list moved on (a poll landed, a stop turned
        // en_route). Apply only the order it computed to the rows we hold now; pasting
        // back the copies we captured would undo those fresher statuses.
        // ponytail: indexOf over a rider's handful of stops; a map if routes get long.
        val order = (listOfNotNull(head) + trip.visitOrder.map { located[it] }).mapNotNull { it.id }
        assigned = assigned.sortedBy { p -> order.indexOf(p.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        route = buildRoute(null, assigned, origin, trip)
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
        navRoad = null
    }

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
    fun onNavPosition(here: RoutePlanner.LatLng) {
        origin = here
        val p = activePickup?.takeIf { it.rider_id != null } ?: return
        val dest = p.latLng()?.toLL() ?: return
        if (p.status == "en_route" && RoutePlanner.distanceKm(here, dest) <= ARRIVING_KM) advance(p, "arriving")
        val last = navFrom
        if (navPickupId == p.id && last != null && RoutePlanner.distanceKm(last, here) < REROUTE_KM) return
        navFrom = here
        navPickupId = p.id
        viewModelScope.launch {
            StreetRouter.route(listOf(here, dest), vehicle)?.let { if (navPickupId == p.id) navRoad = it }
        }
    }

    /** At the door: counting starts from what the family declared. */
    fun arrive() {
        val p = activePickup?.takeIf { it.rider_id != null } ?: return
        confirmQty = p.pickup_items.mapNotNull { item ->
            item.materials?.id?.let { it to (item.confirmed_quantity ?: item.reported_quantity) }
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
            runCatching { ensureSignedIn(); MakiRepository.setPickupStatus(id, status) }
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
                if (MakiRepository.acceptPickup(id)) {
                    message = "¡Recojo asignado! En camino."
                    // Optimistic: navigation opens on this stop without waiting for the reload.
                    assigned = inVisitOrder(assigned + p.copy(rider_id = MakiApi.session?.userId, status = "assigned"))
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
                MakiRepository.confirmQuantities(id, counts)
                MakiRepository.completePickup(id)

                val remaining = assigned.count { it.id != id }
                completed = RiderCompletedUiState(
                    stopName = p.generator?.display_name ?: "el vecino",
                    materialsSummary = "${counts.sumOf { it.second }} unidades" +
                        (p.total_weight_kg?.takeIf { it > 0 }?.let { " · %.1f kg".format(Locale.US, it) } ?: ""),
                    // The settle prices them a moment later; don't show a zero as a result.
                    pointsAwarded = (p.generator_points_awarded?.takeIf { it > 0 }
                        ?: p.pickup_items.sumOf { it.points }.takeIf { it > 0 })
                        ?.let { "$it pts" } ?: "Se acreditan en el acopio",
                    earnings = soles(p.rider_earnings ?: 0.0),
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
                MakiRepository.releasePickup(id)
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
        undelivered = MakiRepository.loadUndeliveredPickups()
        deliveries = runCatching { MakiRepository.loadMyDeliveries() }.getOrDefault(emptyList())
        if (centers.isEmpty()) centers = runCatching { MakiRepository.loadCenters() }.getOrDefault(emptyList())
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
        val row = MakiRepository.loadMyRiderProfile()
        val name = runCatching { MakiRepository.loadMyDisplayName() }.getOrNull()
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
                MakiRepository.createDelivery(center, stops)
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
/** 150 m: close enough that "está llegando" is true on SJL's short blocks. */
private const val ARRIVING_KM = 0.15
/** 40 m: less than that and the old street path still starts at the rider. */
private const val REROUTE_KM = 0.04
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
                address = p.address?.address_line ?: "Direccion no registrada",
                materials = itemsSummary(p.pickup_items),
                earnings = soles(p.rider_earnings ?: 0.0),
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
        else "${soles(fees)} por cobrar cuando el acopio pese la carga",
        canSend = chosen.isNotEmpty() && pickedCenter != null,
        hint = when {
            chosen.isEmpty() -> "Marca al menos un recojo."
            pickedCenter == null -> "Elige el acopio al que vas."
            else -> "El acopio pesa la carga: ahi se pagan tus recojos y los puntos del vecino."
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
    (d.settled_at ?: d.departed_at)?.let { MakiTime.dayMonth(it) },
).joinToString(" · ").ifBlank { "Sin detalle" }

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun soles(v: Double): String = "S/%.2f".format(Locale.US, v)

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
            address = p.address?.address_line ?: "Dirección no registrada",
            items = itemsSummary(p.pickup_items).ifBlank { "Residuos por confirmar" },
            window = p.window_label ?: "Sin ventana horaria",
            distance = km?.let { RoutePlanner.distanceLabel(it) } ?: "Sin ubicación",
            eta = km?.let { "~${RoutePlanner.etaMinutes(it)} min" } ?: "",
            km = km,
            waiting = p.created_at?.let { MakiTime.relative(it) }.orEmpty(),
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
    return "Sales ${if (sameDay) "hoy" else "mañana"} ${MakiTime.clockAt(at)}"
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
): RiderDashboardUiState {
    val nextIsMine = next?.rider_id != null
    val todayRows = earnings.filter { it.created_at.startsWith(today()) }
    val doneToday = todayRows.size
    val monthTotal = earnings.filter { it.created_at.startsWith(currentMonthPrefix()) }.sumOf { it.amount }
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
        shiftPlan = shiftPlanLabel(com.example.maki.data.MakiPrefs.shiftStartAt),
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
        nextStopAddress = next?.address?.address_line ?: "Te avisaremos cuando haya solicitudes",
        nextStopEta = km?.let { "Llegas ${MakiTime.clockPlus(RoutePlanner.etaMinutes(it))}" }.orEmpty(),
        nextStopDistance = when {
            next == null -> ""
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

private fun PickupDto.toConfirmUi(qty: Map<String, Int>): RiderConfirmUiState {
    val mats = pickup_items.map {
        val mid = it.materials?.id.orEmpty()
        ConfirmMaterialUi(
            name = it.materials?.name ?: "Material",
            detail = "Declararon ${it.reported_quantity}",
            points = "+${it.points} pts",
            materialId = mid,
            declared = it.reported_quantity,
            confirmed = qty[mid] ?: it.confirmed_quantity ?: it.reported_quantity,
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
        totalWeight = total_weight_kg?.takeIf { it > 0 }?.let { "%.1f kg".format(Locale.US, it) } ?: "—",
        // Points are priced server-side on settle: "0 pts" before that is not a zero,
        // it is a number that does not exist yet.
        totalPoints = if (totalPts > 0) "$totalPts pts" else "Se calculan al confirmar",
        earnings = soles(rider_earnings ?: 0.0),
    )
}

private fun PickupDto.toNavigationUi(road: StreetRouter.Route?, position: String): RiderNavigationUiState = RiderNavigationUiState(
    stopName = generator?.display_name ?: "Generador",
    address = address?.address_line ?: "Dirección no registrada",
    distance = distance_m?.let { RoutePlanner.distanceLabel(it / 1000.0) } ?: "—",
    eta = eta_minutes?.let { "$it min" } ?: "—",
    instruction = address?.reference?.takeIf { it.isNotBlank() } ?: "Dirígete al punto de recojo",
    position = position,
    statusLabel = when (status) {
        "arriving" -> "Estás llegando"
        "collecting" -> "Contando en la puerta"
        else -> "En camino"
    },
    atDoor = status == "arriving" || status == "collecting",
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
            street != null && stops.size >= 2 -> "Ruta más rápida por calles · ${stops.size} paradas"
            stops.size >= 2 -> "Ruta ordenada · ${stops.size} paradas en orden más corto"
            else -> "1 parada asignada"
        },
        stops = stops,
        totalDistance = distanceM?.let { "%.1f km total".format(Locale.US, it / 1000.0) } ?: "—",
        totalTime = minutes?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" } ?: "—",
        // Only with a street answer: an hour built on crow-flies would be a lie.
        finishLabel = street?.let { "Terminas ~${MakiTime.clockPlus(minutes ?: it.minutes)}" }.orEmpty(),
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
        stop.copy(eta = "${running} min", arrival = "Llegas ${MakiTime.clockPlus(running)}")
    }
}

private fun PickupDto.toStopUi(number: String, eta: String?, done: Boolean): RouteStopUi = RouteStopUi(
    number = number,
    name = generator?.display_name ?: "Generador",
    subtitle = address?.address_line ?: "Dirección no registrada",
    eta = eta,
    material = itemsSummary(pickup_items),
    streak = window_label,
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
            name = p.generator?.display_name ?: "Generador",
            detail = listOfNotNull(
                itemsSummary(p.pickup_items).takeIf { p.pickup_items.isNotEmpty() },
                p.address?.address_line,
            ).joinToString(" · ").ifBlank { "Recojo sin detalle" },
            earnings = p.rider_earnings?.takeIf { it > 0 && p.status == "completed" }?.let { "+${soles(it)}" } ?: "—",
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

    // Last 7 calendar days, oldest first: a week reads left to right, like the chart.
    val byDay = rows.groupBy { it.created_at.take(10) }
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
        todaySub = "${todayRows.size} recojo${if (todayRows.size == 1) "" else "s"} completado${if (todayRows.size == 1) "" else "s"}",
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
            medalColor = if (mine) MakiColors.Rider else podiumColors.getOrElse(i) { MakiColors.Rider },
            crown = e.rank == 1,
            isYou = mine,
            gap = if (mine && ahead != null) "A $ahead pts del ${e.rank - 1}°" else null,
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
        aboutPickup = reference_type == "pickup",
    )
}
