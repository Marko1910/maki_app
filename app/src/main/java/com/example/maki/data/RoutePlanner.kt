package com.example.maki.data

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orders the rider's stops into a short round of the neighbourhood.
 *
 * Visiting N stops in the best possible order is the Travelling Salesman Problem,
 * which is NP-hard, so this uses the standard two-stage heuristic: build a tour
 * with **nearest neighbour**, then improve it with **2-opt** local search.
 * Nearest neighbour alone lands roughly 10–40% above the optimal tour because its
 * last hops are whatever is left over; 2-opt repeatedly reverses a segment when
 * doing so shortens the tour, which removes the crossings that cause most of that
 * gap. For the 5–20 stops a rider carries it is effectively optimal and runs in
 * microseconds — no network call, no API key, no dependency.
 *
 * Distances are straight-line (haversine). That is the right call for *ordering*
 * stops a few blocks apart, and wrong for *drawing* the path: streets are one-way,
 * blocked and curved. Turn-by-turn geometry needs a road-network router
 * (OpenRouteService / OSRM); see `planStreetRoute` in the README notes for that
 * upgrade path — it should live behind an Edge Function so the API key never
 * ships in the APK.
 */
object RoutePlanner {

    /** Latitude/longitude pair in degrees. */
    data class LatLng(val lat: Double, val lng: Double)

    private const val EARTH_RADIUS_KM = 6371.0
    private const val MAX_TWO_OPT_PASSES = 50

    /**
     * Returns [stops] reordered for a short round starting at [start] (the rider's
     * current position when known). Items without coordinates keep their relative
     * order and are appended at the end — a stop we cannot place should not be lost.
     */
    fun <T> optimize(start: LatLng?, stops: List<T>, position: (T) -> LatLng?): List<T> {
        if (stops.size < 2) return stops
        val located = stops.mapNotNull { s -> position(s)?.let { s to it } }
        val unlocated = stops.filter { position(it) == null }
        if (located.size < 2) return stops

        val order = twoOpt(nearestNeighbour(start, located.map { it.second }), located.map { it.second })
        return order.map { located[it].first } + unlocated
    }

    /**
     * Minutes to cover [km] through neighbourhood streets, at the same ~15 km/h the
     * server's dispatch assumes — keeping the family's "llega en ~5 min" and the
     * rider's own banner from disagreeing. Never returns 0: "0 min" reads as a bug.
     */
    fun etaMinutes(km: Double): Int = maxOf(1, kotlin.math.ceil(km / KM_PER_MINUTE).toInt())

    /** "458 m" under a kilometre, "1.2 km" beyond — how a person says a distance. */
    fun distanceLabel(km: Double): String =
        if (km < 1) "${(km * 1000).toInt()} m" else "%.1f km".format(java.util.Locale.US, km)

    /** 15 km/h door to door, the pace of a loaded bike through SJL. */
    private const val KM_PER_MINUTE = 0.25

    /** Great-circle distance in kilometres. */
    fun distanceKm(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
    }

    /** Total length of a tour visiting [points] in [order], starting at [start]. */
    fun tourLengthKm(start: LatLng?, points: List<LatLng>, order: List<Int>): Double {
        if (order.isEmpty()) return 0.0
        var total = start?.let { distanceKm(it, points[order.first()]) } ?: 0.0
        for (i in 0 until order.lastIndex) {
            total += distanceKm(points[order[i]], points[order[i + 1]])
        }
        return total
    }

    /** Greedy tour: from the current point, always hop to the closest unvisited stop. */
    private fun nearestNeighbour(start: LatLng?, points: List<LatLng>): List<Int> {
        val remaining = points.indices.toMutableList()
        val order = ArrayList<Int>(points.size)
        var current = start
        while (remaining.isNotEmpty()) {
            val nextIdx = if (current == null) {
                0   // no fix on the rider: start from the first stop as given
            } else {
                remaining.indices.minBy { i -> distanceKm(current!!, points[remaining[i]]) }
            }
            val chosen = remaining.removeAt(nextIdx)
            order.add(chosen)
            current = points[chosen]
        }
        return order
    }

    /**
     * 2-opt: while any segment reversal shortens the tour, apply it. Each pass is
     * O(n²); with a rider's handful of stops that is nothing, and the pass cap keeps
     * a pathological input from spinning.
     */
    private fun twoOpt(initial: List<Int>, points: List<LatLng>): List<Int> {
        if (initial.size < 4) return initial
        var order = initial
        var pass = 0
        var improved = true
        while (improved && pass++ < MAX_TWO_OPT_PASSES) {
            improved = false
            for (i in 0 until order.size - 2) {
                for (j in i + 2 until order.size) {
                    val a = points[order[i]]
                    val b = points[order[i + 1]]
                    val c = points[order[j]]
                    val d = points.getOrNull(order.getOrNull(j + 1) ?: -1)
                    // Current: a→b … c→d. Reversed: a→c … b→d. Compare only the
                    // two edges that change; the reversed middle keeps its length.
                    val before = distanceKm(a, b) + (d?.let { distanceKm(c, it) } ?: 0.0)
                    val after = distanceKm(a, c) + (d?.let { distanceKm(b, it) } ?: 0.0)
                    if (after < before - EPSILON) {
                        order = order.toMutableList().apply { reverse(i + 1, j) }
                        improved = true
                    }
                }
            }
        }
        return order
    }

    /** Reverses the inclusive [from]..[to] slice in place. */
    private fun MutableList<Int>.reverse(from: Int, to: Int) {
        var lo = from
        var hi = to
        while (lo < hi) {
            val tmp = this[lo]; this[lo] = this[hi]; this[hi] = tmp
            lo++; hi--
        }
    }

    /** Below this (≈11 cm) a "gain" is floating-point noise, not a shorter road. */
    private const val EPSILON = 1e-6

    /** True when two tours have the same total length, within [toleranceKm]. */
    fun sameLength(a: Double, b: Double, toleranceKm: Double = 1e-9): Boolean = abs(a - b) < toleranceKm
}
