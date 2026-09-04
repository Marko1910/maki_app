package com.example.maki

import com.example.maki.data.RoutePlanner
import com.example.maki.data.RoutePlanner.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rider's stop ordering is the one piece of non-trivial logic in the route
 * screen, so it gets a check: a deliberately crossed tour must come out shorter,
 * and no stop may be lost along the way.
 */
class RoutePlannerTest {

    // Four corners of a block in SJL, listed in an order that crosses itself.
    private val a = LatLng(-11.9901, -76.9920)   // NW
    private val b = LatLng(-11.9901, -76.9900)   // NE
    private val c = LatLng(-11.9921, -76.9900)   // SE
    private val d = LatLng(-11.9921, -76.9920)   // SW

    @Test
    fun `untangles a crossing tour`() {
        val crossed = listOf(a, c, b, d)          // NW → SE → NE → SW: two crossings
        val optimized = RoutePlanner.optimize(start = a, stops = crossed) { it }

        val before = RoutePlanner.tourLengthKm(a, crossed, crossed.indices.toList())
        val after = RoutePlanner.tourLengthKm(a, optimized, optimized.indices.toList())

        assertTrue("2-opt should shorten a crossing tour ($after km vs $before km)", after < before)
        assertEquals("no stop may be dropped", crossed.size, optimized.size)
        assertEquals("every stop must survive", crossed.toSet(), optimized.toSet())
    }

    @Test
    fun `keeps stops without coordinates instead of dropping them`() {
        data class Stop(val name: String, val at: LatLng?)
        val stops = listOf(Stop("con", a), Stop("sin", null), Stop("con2", c))

        val optimized = RoutePlanner.optimize(start = a, stops = stops) { it.at }

        assertEquals(3, optimized.size)
        assertTrue(optimized.any { it.name == "sin" })
    }

    @Test
    fun `haversine matches a known distance`() {
        // One degree of latitude is ~111.2 km anywhere on the globe.
        val km = RoutePlanner.distanceKm(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        assertEquals(111.2, km, 0.5)
    }

    @Test
    fun `single stop is returned untouched`() {
        val one = listOf(a)
        assertEquals(one, RoutePlanner.optimize(start = null, stops = one) { it })
    }

    @Test
    fun `eta and distance read the way a person says them`() {
        // Same pace the dispatch RPC uses (~15 km/h): 458 m is a 2-minute hop.
        assertEquals(2, RoutePlanner.etaMinutes(0.458))
        assertEquals(4, RoutePlanner.etaMinutes(1.0))
        // Arriving is never "0 min" — that reads as a broken screen, not a number.
        assertEquals(1, RoutePlanner.etaMinutes(0.0))

        assertEquals("458 m", RoutePlanner.distanceLabel(0.458))
        assertEquals("1.2 km", RoutePlanner.distanceLabel(1.23))
    }
}
