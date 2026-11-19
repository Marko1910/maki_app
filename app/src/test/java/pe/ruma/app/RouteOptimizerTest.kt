package pe.ruma.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.ruma.app.data.RouteOptimizer
import pe.ruma.app.data.RouteOptimizer.Stop

/** Same cases as pwa/scripts/check-route.ts. */
class RouteOptimizerTest {
    // Rider at 0; stops A(1) B(2) C(3) on a line, 10 min apart: 0 —10— A —10— B —10— C.
    private val line = arrayOf(
        doubleArrayOf(0.0, 10.0, 20.0, 30.0),
        doubleArrayOf(10.0, 0.0, 10.0, 20.0),
        doubleArrayOf(20.0, 10.0, 0.0, 10.0),
        doubleArrayOf(30.0, 20.0, 10.0, 0.0),
    )

    @Test fun withoutConstraintsDrivesTheShortestWay() =
        assertEquals(listOf(0, 1, 2), RouteOptimizer.bestOrder(line, List(3) { Stop() }))

    @Test fun aClosingWindowGoesFirst() {
        // C's window closes in 15 min: along the line it arrives at 40, 25 late; going there first, 15 late.
        val order = RouteOptimizer.bestOrder(line, listOf(Stop(), Stop(), Stop(dueMin = 15.0)))
        assertEquals(2, order.first())
    }

    @Test fun findsTheCheapestCompromise() =
        // C closes in 35 min. A→B→C arrives 5 late (cost 45); C first is on time but drives 50.
        // A→C→B reaches C at 35 exactly and drives 40: the best of both.
        assertEquals(listOf(0, 2, 1), RouteOptimizer.bestOrder(line, listOf(Stop(), Stop(), Stop(dueMin = 35.0))))

    // A and B both 10 min from the rider and from each other.
    private val triangle = arrayOf(
        doubleArrayOf(0.0, 10.0, 10.0),
        doubleArrayOf(10.0, 0.0, 10.0),
        doubleArrayOf(10.0, 10.0, 0.0),
    )

    @Test fun rumaPlusFirstWhenItCostsNoDetour() {
        assertEquals(listOf(0, 1), RouteOptimizer.bestOrder(triangle, listOf(Stop(), Stop())))
        assertEquals(listOf(1, 0), RouteOptimizer.bestOrder(triangle, listOf(Stop(), Stop(priority = 1))))
    }

    @Test fun largeRoutesUseLocalSearchAndVisitEveryStop() {
        val n = 12
        val pts = List(n) { it * 1.7 % 7.0 to it * 3.1 % 5.0 }
        val m = Array(n + 1) { i -> DoubleArray(n + 1) { j ->
            val a = if (i == 0) 0.0 to 0.0 else pts[i - 1]
            val b = if (j == 0) 0.0 to 0.0 else pts[j - 1]
            Math.hypot(a.first - b.first, a.second - b.second) * 3
        } }
        val stops = List(n) { Stop() }
        val order = RouteOptimizer.bestOrder(m, stops)
        assertEquals((0 until n).toList(), order.sorted())
        val naive = RouteOptimizer.cost(m, stops, (0 until n).toList())
        assertTrue(RouteOptimizer.cost(m, stops, order) <= naive)
    }

    @Test fun readsWindowLabels() {
        assertEquals(18 * 60, RouteOptimizer.windowEndMinute("Hoy 3:00 - 6:00 pm"))
        assertEquals(12 * 60, RouteOptimizer.windowEndMinute("Mañana 9:00 - 12:00 m"))
        assertEquals(null, RouteOptimizer.windowEndMinute("Cuando puedas"))
        // Asked yesterday for "Mañana 9-12": due today at noon; at 10:00 that is 120 min away.
        assertEquals(120.0, RouteOptimizer.dueMinutes("Mañana 9:00 - 12:00 m", "2026-11-18", "2026-11-18", 600)!!, 0.0)
        assertEquals(1440.0 + 120.0, RouteOptimizer.dueMinutes("Mañana 9:00 - 12:00 m", "2026-12-01", "2026-11-30", 600)!!, 0.0)
    }
}
