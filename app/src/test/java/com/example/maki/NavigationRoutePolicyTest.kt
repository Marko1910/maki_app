package com.example.maki

import com.example.maki.data.NavigationRoutePolicy
import com.example.maki.data.RoutePlanner
import com.example.maki.data.RouteProgress
import org.junit.Assert.*
import org.junit.Test

class NavigationRoutePolicyTest {
    private val here = RoutePlanner.LatLng(-12.05, -77.04)

    @Test fun `stationary failures retry with backoff`() {
        val gate = NavigationRoutePolicy()
        val first = gate.begin("A", 0, false)!!
        assertNull(gate.begin("A", 1, false, true))
        assertTrue(gate.complete(first, here, 100, false))
        assertNull(gate.begin("A", 3099, false))
        assertNotNull(gate.begin("A", 3100, false))
    }

    @Test fun `destination change and reopening invalidate old responses`() {
        val gate = NavigationRoutePolicy()
        val old = gate.begin("A", 0, false)!!
        val current = gate.begin("B", 1, false)!!
        assertFalse(gate.complete(old, here, 2, true))
        assertTrue(gate.complete(current, here, 3, true))
        gate.reset()
        assertNotNull(gate.begin("B", 4, false))
    }

    @Test fun `failed refresh retries without waiting for successful-route ttl`() {
        val gate = NavigationRoutePolicy()
        gate.complete(gate.begin("A", 0, false)!!, here, 100, true)
        assertNull(gate.begin("A", 5000, true))
        val refresh = gate.begin("A", 10100, true)!!
        gate.complete(refresh, here, 10200, false)
        assertNotNull(gate.begin("A", 13200, false))
    }

    @Test fun `following streets does not recalculate every forty metres`() {
        val gate = NavigationRoutePolicy()
        gate.complete(gate.begin("A", 0, false)!!, here, 0, true)
        assertNull(gate.begin("A", 30000, false))
        assertNotNull(gate.begin("A", 60000, false))
    }

    @Test fun `progress projects to segment rather than nearest vertex`() {
        val a = RoutePlanner.LatLng(0.0, 0.0)
        val b = RoutePlanner.LatLng(0.0, 0.02)
        val progress = RouteProgress.locate(RoutePlanner.LatLng(0.0, 0.01), listOf(a, b))!!
        assertEquals(0.0, progress.offRouteKm, 0.00001)
        assertEquals(0.5, progress.travelledKm / progress.totalKm, 0.00001)
        val away = RouteProgress.locate(RoutePlanner.LatLng(0.001, 0.01), listOf(a, b))!!
        assertTrue(away.offRouteKm > 0.1)
        assertNull(RouteProgress.locate(a, listOf(a)))
    }
}
