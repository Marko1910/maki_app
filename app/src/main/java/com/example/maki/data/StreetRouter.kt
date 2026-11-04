package com.example.maki.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * trip really is by road. Every call is best-effort: null means "draw the
 * straight line", never an error the rider has to deal with.
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
    )

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
        val coords = points.joinToString(";") { "%.6f,%.6f".format(Locale.US, it.lng, it.lat) }
        val url = "$BASE/${profile(vehicle)}/$service/v1/driving/$coords?overview=full&geometries=geojson$extra"
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                parse(resp.body?.string().orEmpty())
            }
        }.getOrNull()
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
        val order = if (dto.trips.isEmpty()) emptyList() else
            dto.waypoints.indices.drop(1)
                .sortedBy { dto.waypoints[it].waypoint_index }
                .map { it - 1 }
        return Route(
            path = leg.geometry?.coordinates.orEmpty()
                .filter { it.size >= 2 }
                .map { RoutePlanner.LatLng(lat = it[1], lng = it[0]) },
            distanceKm = leg.distance / 1000.0,
            minutes = travelMinutes(leg.duration),
            visitOrder = order,
            hopMinutes = leg.legs.map { travelMinutes(it.duration) },
            hopKm = leg.legs.map { it.distance / 1000.0 },
        )
    }

    /** OSRM seconds → the minutes a rider actually spends, traffic included. */
    private fun travelMinutes(seconds: Double): Int = maxOf(1, ceil(seconds * TRAFFIC_FACTOR / 60.0).toInt())

    /** Which road network the rider's vehicle actually uses. */
    private fun profile(vehicle: String?): String = when (vehicle) {
        "bicicleta", "bici", "triciclo" -> "routed-bike"
        "a_pie" -> "routed-foot"
        else -> "routed-car"   // moto, furgoneta: the streets a car can take
    }
}
