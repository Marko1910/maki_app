package com.example.maki

import com.example.maki.data.StreetRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trip response is where the rider's stop order comes from, and its indexing
 * is easy to get wrong (input 0 is the rider, not a stop; lng comes before lat).
 */
class StreetRouterTest {

    @Test fun `empty or invalid road geometry is unavailable`() {
        assertNull(StreetRouter.parse("""{"code":"Ok","routes":[{"distance":10,"duration":10,"geometry":{"coordinates":[]}}]}"""))
        assertNull(StreetRouter.parse("""{"code":"Ok","routes":[{"distance":10,"duration":10,"geometry":{"coordinates":[[-77,-12],[999,999],[-77.1,-12.1]]}}]}"""))
    }

    @Test fun `steps preserve maneuver and offset for integrated guidance`() {
        val body = """{"code":"Ok","routes":[{"distance":300,"duration":60,
          "geometry":{"coordinates":[[-77,-12],[-77.001,-12.001]]},"legs":[{"steps":[
          {"distance":100,"name":"Calle A","maneuver":{"type":"depart"}},
          {"distance":200,"name":"Calle B","maneuver":{"type":"turn","modifier":"right"}}
          ]}]}]}"""
        val road = StreetRouter.parse(body)!!
        assertEquals(0.1, road.steps[1].atKm, 0.00001)
        assertEquals("Gira a la derecha por Calle B", road.steps[1].instruction)
    }

    @Test
    fun `trip order is re-based onto the stops and follows waypoint_index`() {
        // Input: rider, stop0, stop1. OSRM visits stop1 first (index 1), then stop0 (index 2).
        val body = """
            {"code":"Ok",
             "trips":[{"distance":4400.9,"duration":624.9,
                       "geometry":{"coordinates":[[-76.995,-11.995],[-76.99,-11.99]]}}],
             "waypoints":[{"waypoint_index":0},{"waypoint_index":2},{"waypoint_index":1}]}
        """.trimIndent()

        val r = StreetRouter.parse(body)!!

        assertEquals(listOf(1, 0), r.visitOrder)
        assertEquals(4.4009, r.distanceKm, 1e-6)
        assertEquals(-11.995, r.path.first().lat, 1e-9)   // GeoJSON is [lng, lat]
        assertEquals(-76.995, r.path.first().lng, 1e-9)
        assertTrue("traffic-adjusted, never faster than free-flow", r.minutes >= 11)
    }

    @Test
    fun `plain route has no visit order`() {
        val body = """{"code":"Ok","routes":[{"distance":990.6,"duration":145.2,"geometry":{"coordinates":[[-76.995,-11.995],[-76.99,-11.99]]}}],
                       "waypoints":[{"waypoint_index":0},{"waypoint_index":0}]}"""
        val r = StreetRouter.parse(body)!!
        assertTrue(r.visitOrder.isEmpty())
        assertEquals(0.9906, r.distanceKm, 1e-6)
    }

    @Test
    fun `no route means fall back, not crash`() {
        assertNull(StreetRouter.parse("""{"code":"NoRoute","message":"Impossible route"}"""))
    }

    /** Each stop's own ETA comes from the hops, so they must survive parsing in travel order. */
    @Test
    fun `hops carry each leg's own time and distance`() {
        val body = """
            {"code":"Ok",
             "trips":[{"distance":4400.0,"duration":600.0,
                       "geometry":{"coordinates":[[-76.995,-11.995],[-76.99,-11.99]]},
                       "legs":[{"distance":1200.0,"duration":180.0},
                               {"distance":3200.0,"duration":420.0}]}],
             "waypoints":[{"waypoint_index":0},{"waypoint_index":1},{"waypoint_index":2}]}
        """.trimIndent()

        val r = StreetRouter.parse(body)!!

        assertEquals(2, r.hopMinutes.size)
        assertEquals(listOf(1.2, 3.2), r.hopKm.map { (it * 10).toInt() / 10.0 })
        assertTrue("hop times are traffic-adjusted too", r.hopMinutes[0] >= 3 && r.hopMinutes[1] >= 7)
        // Each hop rounds up on its own, so the parts are never short of the whole.
        assertTrue(r.hopMinutes.sum() >= r.minutes)
    }
}
