package com.example.maki.data

/** Pure request lifecycle, shared by navigation and its deterministic tests. */
class NavigationRoutePolicy {
    private var key: String? = null
    private var generation = 0L
    private var pending: Long? = null
    private var retryAt = 0L
    private var failures = 0
    private var refreshedAt = 0L
    private var lastSuccess: RoutePlanner.LatLng? = null

    fun reset() {
        generation++
        key = null
        pending = null
        retryAt = 0
        failures = 0
        lastSuccess = null
    }

    fun begin(destination: String, now: Long, offRoute: Boolean, force: Boolean = false): Long? {
        if (destination != key) { reset(); key = destination }
        if (pending != null || (!force && now < retryAt)) return null
        if (!force && failures == 0 && lastSuccess != null && !offRoute && now - refreshedAt < 60_000) return null
        if (!force && failures == 0 && lastSuccess != null && now - refreshedAt < 10_000) return null
        return (++generation).also { pending = it }
    }

    fun complete(ticket: Long, here: RoutePlanner.LatLng, now: Long, success: Boolean): Boolean {
        if (pending != ticket) return false
        pending = null
        if (success) {
            lastSuccess = here
            refreshedAt = now
            failures = 0
            retryAt = 0
        } else {
            failures++
            retryAt = now + minOf(30_000L, 3_000L * (1L shl minOf(failures - 1, 4)))
        }
        return true
    }
}

/** Local projection onto road segments. Distances are kilometres, not raw degrees. */
object RouteProgress {
    data class Progress(val offRouteKm: Double, val travelledKm: Double, val totalKm: Double)

    fun locate(here: RoutePlanner.LatLng, path: List<RoutePlanner.LatLng>): Progress? {
        if (path.size < 2) return null
        val xScale = 111.32 * kotlin.math.cos(Math.toRadians(here.lat))
        val yScale = 111.32
        var best = Double.POSITIVE_INFINITY
        var travelled = 0.0
        var total = 0.0
        path.zipWithNext().forEach { (a, b) ->
            val ax = (a.lng - here.lng) * xScale
            val ay = (a.lat - here.lat) * yScale
            val dx = (b.lng - a.lng) * xScale
            val dy = (b.lat - a.lat) * yScale
            val length = RoutePlanner.distanceKm(a, b)
            val t = if (dx * dx + dy * dy == 0.0) 0.0 else (-(ax * dx + ay * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
            val distance = kotlin.math.hypot(ax + t * dx, ay + t * dy)
            if (distance < best) { best = distance; travelled = total + t * length }
            total += length
        }
        return Progress(best, travelled, total)
    }
}
