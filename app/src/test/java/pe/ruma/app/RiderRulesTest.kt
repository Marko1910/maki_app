package pe.ruma.app

import pe.ruma.app.data.AddressDto
import pe.ruma.app.data.PickupDto
import pe.ruma.app.ui.screens.rider.localDay
import pe.ruma.app.ui.screens.rider.stopAddress
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/** The two rules behind the Cartavio report: which address the rider reads, and which day a payment is. */
class RiderRulesTest {

    private val larco = AddressDto(address_line = "Av. Larco 1250, Victor Larco", latitude = -8.1403, longitude = -79.0344)

    @Test
    fun gpsPickupFarFromHomeShowsTheGpsLabel() {
        val p = PickupDto(latitude = -7.8855966, longitude = -79.2215387, address = larco)
        assertEquals("Ubicación GPS del recolector", p.stopAddress())
    }

    @Test
    fun gpsPickupAtHomeKeepsTheStreet() {
        // ~100 m from the saved point: the registered street is still right.
        val p = PickupDto(latitude = -8.1412, longitude = -79.0344, address = larco)
        assertEquals("Av. Larco 1250, Victor Larco", p.stopAddress())
    }

    @Test
    fun pickupWithoutItsOwnPinKeepsTheStreet() {
        assertEquals("Av. Larco 1250, Victor Larco", PickupDto(address = larco).stopAddress())
    }

    @Test
    fun aLimaEveningPaymentIsToday() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"))
            // 7:44 pm on the 11th in Lima is already the 12th in UTC.
            assertEquals("2026-11-11", localDay("2026-11-12T00:44:38.972369+00:00"))
        } finally {
            TimeZone.setDefault(saved)
        }
    }
}
