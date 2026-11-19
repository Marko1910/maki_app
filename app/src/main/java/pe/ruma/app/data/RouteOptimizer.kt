package pe.ruma.app.data

import kotlin.math.max

/**
 * The rider's best visit order on real street times (OSRM `table`, see [StreetRouter.bestOrder]), weighing
 * more than the drive: a family's window ("Hoy 3:00 - 6:00 pm") and a Ruma Plus pickup ("tu recojo primero").
 * OSRM's own `trip` only minimises driving, so it could leave the 3-6 pm family last and arrive at 7.
 * Twin of `pwa/src/lib/routeOptimizer.ts`; keep both in step (same cases in RouteOptimizerTest and
 * `pwa/scripts/check-route.ts`).
 *
 * Cost of an order, in minutes: driving + [LATE_WEIGHT] × minutes past each deadline + [PRIORITY_WEIGHT] ×
 * minutes a priority family waits. Exact (every order) up to [EXACT_MAX] stops — a rider's day — and
 * nearest-first + 2-opt + relocate above that.
 */
object RouteOptimizer {
    /** A late minute weighs as three minutes of driving: a broken promise costs more than fuel. */
    const val LATE_WEIGHT = 3.0
    /** Each minute a Ruma Plus family waits weighs as a minute of driving: first unless it is a big detour. */
    const val PRIORITY_WEIGHT = 1.0
    /** Minutes at each door (count, weigh by eye, load). */
    const val SERVICE_MIN = 5.0
    /** 8! = 40 320 orders: instant on a phone. */
    const val EXACT_MAX = 8

    /** [dueMin]: minutes from now until its window closes (negative = already late); null = no deadline. */
    data class Stop(val dueMin: Double? = null, val priority: Int = 0)

    /**
     * [minutes]: travel minutes, index 0 the rider's position and 1..n the stops (row = from, column = to).
     * Returns the stops' visit order as 0-based indexes into [stops].
     */
    fun bestOrder(minutes: Array<DoubleArray>, stops: List<Stop>): List<Int> {
        val n = stops.size
        if (n <= 1) return List(n) { it }
        require(minutes.size == n + 1 && minutes.all { it.size == n + 1 }) { "matrix must be (n+1)×(n+1)" }
        return if (n <= EXACT_MAX) exact(minutes, stops) else improve(minutes, stops, nearestFirst(minutes, n))
    }

    /** Minutes of the whole plan: what [bestOrder] minimises. */
    fun cost(minutes: Array<DoubleArray>, stops: List<Stop>, order: List<Int>): Double {
        var t = 0.0
        var at = 0
        var total = 0.0
        for (s in order) {
            val drive = minutes[at][s + 1]
            t += drive
            total += drive
            stops[s].dueMin?.let { total += LATE_WEIGHT * max(0.0, t - it) }
            if (stops[s].priority > 0) total += PRIORITY_WEIGHT * t
            t += SERVICE_MIN
            at = s + 1
        }
        return total
    }

    private fun exact(m: Array<DoubleArray>, stops: List<Stop>): List<Int> {
        val perm = IntArray(stops.size) { it }
        var best = perm.toList()
        var bestCost = cost(m, stops, best)
        // Heap's algorithm: every order once, no allocation per permutation.
        val c = IntArray(perm.size)
        var i = 0
        while (i < perm.size) {
            if (c[i] < i) {
                val j = if (i % 2 == 0) 0 else c[i]
                perm[j] = perm[i].also { perm[i] = perm[j] }
                val order = perm.asList()
                val k = cost(m, stops, order)
                if (k < bestCost - 1e-9) { bestCost = k; best = perm.toList() }
                c[i]++
                i = 0
            } else {
                c[i] = 0
                i++
            }
        }
        return best
    }

    private fun nearestFirst(m: Array<DoubleArray>, n: Int): List<Int> {
        val left = (0 until n).toMutableSet()
        val out = mutableListOf<Int>()
        var at = 0
        while (left.isNotEmpty()) {
            val next = left.minBy { m[at][it + 1] }
            out += next
            left -= next
            at = next + 1
        }
        return out
    }

    // ponytail: first-improvement local search, O(n³) per pass; fine for the tens of stops a tricycle carries.
    private fun improve(m: Array<DoubleArray>, stops: List<Stop>, start: List<Int>): List<Int> {
        var best = start
        var bestCost = cost(m, stops, best)
        var changed = true
        var passes = 0
        while (changed && passes++ < 50) {
            changed = false
            for (i in 0 until best.size - 1) for (j in i + 1 until best.size) {
                // 2-opt: reverse a stretch; relocate: move one stop elsewhere.
                val candidates = listOf(
                    best.subList(0, i) + best.subList(i, j + 1).reversed() + best.subList(j + 1, best.size),
                    best.toMutableList().apply { add(j, removeAt(i)) },
                    best.toMutableList().apply { add(i, removeAt(j)) },
                )
                for (cand in candidates) {
                    val k = cost(m, stops, cand)
                    if (k < bestCost - 1e-9) { best = cand; bestCost = k; changed = true }
                }
            }
        }
        return best
    }

    /**
     * Minutes from now until a window closes: "Hoy 3:00 - 6:00 pm" on [scheduledDate] ends at 18:00 there.
     * [nowDate] "yyyy-MM-dd" and [nowMinute] (minutes since midnight) are the phone's local clock.
     * Null when the label has no readable end time.
     */
    fun dueMinutes(windowLabel: String?, scheduledDate: String?, nowDate: String, nowMinute: Int): Double? {
        val end = windowEndMinute(windowLabel) ?: return null
        val days = if (scheduledDate.isNullOrBlank()) 0 else daysBetween(nowDate, scheduledDate) ?: return null
        return (days * 1440 + end - nowMinute).toDouble()
    }

    /** "3:00 - 6:00 pm" → 1080; "9:00 - 12:00 m" → 720 (m = mediodía). */
    fun windowEndMinute(label: String?): Int? {
        val m = Regex("""(\d{1,2}):(\d{2})\s*(am|pm|m)?\s*$""", RegexOption.IGNORE_CASE).find(label?.trim() ?: return null) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val hour = when (m.groupValues[3].lowercase()) {
            "pm" -> if (h == 12) 12 else h + 12
            "am" -> if (h == 12) 0 else h
            else -> h
        }
        return hour * 60 + min
    }

    private fun daysBetween(from: String, to: String): Int? {
        fun epochDay(s: String): Long? {
            val p = s.take(10).split("-").mapNotNull { it.toIntOrNull() }
            if (p.size != 3) return null
            // Days from civil (Howard Hinnant): no java.time below API 26.
            val (y0, mo, d) = p
            val y = if (mo <= 2) y0 - 1 else y0
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val doy = (153 * (mo + (if (mo > 2) -3 else 9)) + 2) / 5 + d - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146097L + doe - 719468
        }
        val a = epochDay(from) ?: return null
        val b = epochDay(to) ?: return null
        return (b - a).toInt()
    }
}
