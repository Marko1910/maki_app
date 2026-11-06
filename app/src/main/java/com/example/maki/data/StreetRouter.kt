package com.example.maki.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Street paths and the fastest visiting order, from OSRM on the FOSSGIS servers
 * (routing.openstreetmap.de): OpenStreetMap data, no API key, nothing to hide in
 * the APK.
 *
 * [RoutePlanner] orders stops by straight-line distance and stays the offline
 * fallback; this answers what it cannot — which streets to take, and how long the
 * trip is estimated to take by road. Null means unavailable: callers retain
 * a valid old route or show a retry state, never invent a straight-line road.
 */
object StreetRouter {

    data class Route(
        /** Road geometry in travel order. */
        val path: List<RoutePlanner.LatLng>,
        val distanceKm: Double,
        val minutes: Int,
        /** [trip] only: indices into its `stops`, in the fastest visiting order. */
        val visitOrder: List<Int> = emptyList(),
        /** Minutes of each hop between consecutive waypoints, in travel order. */
        val hopMinutes: List<Int> = emptyList(),
        /** Kilometres of each hop, same order as [hopMinutes]. */
        val hopKm: List<Double> = emptyList(),
        val steps: List<Step> = emptyList(),
    )

    data class Step(val atKm: Double, val instruction: String, val point: RoutePlanner.LatLng?)

    /** The User-Agent OSM's servers see; generic/tutorial agents are blocked. */
    const val USER_AGENT = "Ruma-Reciclaje/1.0 (Android)"

    private const val BASE = "https://routing.openstreetmap.de"

    /**
     * OSRM times are free-flow; SJL at 4 pm is not. Calibration knob — tune it
     * against real `started_at → arrived_at` pickups once there are enough.
     */
    private const val TRAFFIC_FACTOR = 1.3

    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val requestLock = Mutex()
    private var lastRequestNanos = 0L

    /** Fastest street path through [points], in the order given. */
    suspend fun route(points: List<RoutePlanner.LatLng>, vehicle: String?): Route? =
        if (points.size < 2) null else fetch("route", points, vehicle, "")

    /**
     * Fastest order to visit [stops] leaving from [start] — OSRM's trip solver on
     * road travel times, not crow-flies — plus the path through them.
     */
    suspend fun trip(start: RoutePlanner.LatLng, stops: List<RoutePlanner.LatLng>, vehicle: String?): Route? =
        if (stops.isEmpty()) null
        else fetch("trip", listOf(start) + stops, vehicle, "&source=first&roundtrip=false&destination=any")

    private suspend fun fetch(
        service: String,
        points: List<RoutePlanner.LatLng>,
        vehicle: String?,
        extra: String,
    ): Route? = withContext(Dispatchers.IO) {
        requestLock.withLock {
        val waitMs = 1_000 - (System.nanoTime() - lastRequestNanos) / 1_000_000
        if (waitMs > 0) delay(waitMs)
        lastRequestNanos = System.nanoTime()
        val coords = points.joinToString(";") { "%.6f,%.6f".format(Locale.US, it.lng, it.lat) }
        val url = "$BASE/${profile(vehicle)}/$service/v1/driving/$coords?overview=full&geometries=geojson&steps=true$extra"
        try {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                parse(resp.body?.string().orEmpty())
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
    }

    /**
     * Reads an OSRM `route` or `trip` response. For a trip, input 0 is the start
     * and each waypoint's `waypoint_index` is its place in the tour; the stops
     * come back re-based to 0 so the caller can index its own list.
     */
    internal fun parse(body: String): Route? {
        val dto = json.decodeFromString(OsrmResponseDto.serializer(), body)
        if (dto.code != "Ok") return null
        val leg = dto.routes.firstOrNull() ?: dto.trips.firstOrNull() ?: return null
        val coordinates = leg.geometry?.coordinates.orEmpty()
        if (coordinates.any { it.size < 2 || !it[0].isFinite() || !it[1].isFinite() || it[0] !in -180.0..180.0 || it[1] !in -90.0..90.0 }) return null
        val path = coordinates.map { RoutePlanner.LatLng(lat = it[1], lng = it[0]) }
        if (path.size < 2 || !leg.distance.isFinite() || !leg.duration.isFinite() || leg.distance < 0 || leg.duration < 0) return null
        if (dto.trips.isNotEmpty() && (dto.trips.size != 1 || dto.waypoints.isEmpty() ||
            dto.waypoints.first().waypoint_index != 0 || dto.waypoints.map { it.waypoint_index }.sorted() != dto.waypoints.indices.toList())) return null
        var offsetKm = 0.0
        val steps = leg.legs.flatMap { it.steps }.map { step ->
            Step(offsetKm, instruction(step), step.maneuver.location.takeIf { it.size >= 2 }?.let { RoutePlanner.LatLng(it[1], it[0]) })
                .also { offsetKm += step.distance / 1000.0 }
        }
        val order = if (dto.trips.isEmpty()) emptyList() else
            dto.waypoints.indices.drop(1)
                .sortedBy { dto.waypoints[it].waypoint_index }
                .map { it - 1 }
        return Route(
            path = path,
            distanceKm = leg.distance / 1000.0,
            minutes = travelMinutes(leg.duration),
            visitOrder = order,
            hopMinutes = leg.legs.map { travelMinutes(it.duration) },
            hopKm = leg.legs.map { it.distance / 1000.0 },
            steps = steps,
        )
    }

    internal fun instruction(step: OsrmStepDto): String {
        val action = when (step.maneuver.type) {
            "arrive" -> "Llegaste al destino"
            "depart" -> "Inicia el recorrido"
            "roundabout", "rotary" -> step.maneuver.exit?.let { "En la rotonda, toma la salida $it" } ?: "Entra en la rotonda"
            else -> when (step.maneuver.modifier) {
                "left", "sharp left" -> "Gira a la izquierda"
                "right", "sharp right" -> "Gira a la derecha"
                "slight left" -> "Mantente a la izquierda"
                "slight right" -> "Mantente a la derecha"
                "uturn" -> "Da la vuelta donde esté permitido"
                else -> "Continúa recto"
            }
        }
        return action + if (step.name.isNotBlank() && step.maneuver.type != "arrive") " por ${step.name}" else ""
    }

    /** Static estimate; this is not a live traffic feed. */
    private fun travelMinutes(seconds: Double): Int = maxOf(1, ceil(seconds * TRAFFIC_FACTOR / 60.0).toInt())

    /** Which road network the rider's vehicle actually uses. */
    private fun profile(vehicle: String?): String = when (vehicle) {
        "bicicleta", "bici", "triciclo" -> "routed-bike"
        "a_pie" -> "routed-foot"
        else -> "routed-car"   // moto, furgoneta: the streets a car can take
    }
}
