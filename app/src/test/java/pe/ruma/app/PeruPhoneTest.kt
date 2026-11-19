package pe.ruma.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.ruma.app.data.PeruPhone

/** Same cases as pwa/scripts/check-phone.ts. */
class PeruPhoneTest {
    @Test fun acceptsWhatPeopleType() {
        assertEquals("+51987654321", PeruPhone.normalize("987 654 321"))
        assertEquals("+51987654321", PeruPhone.normalize("+51 987-654-321"))
        assertEquals("+51987654321", PeruPhone.normalize("0051987654321"))
    }

    @Test fun rejectsLandlinesAndShortNumbers() {
        assertNull(PeruPhone.normalize("044 123456"))
        assertNull(PeruPhone.normalize("98765432"))
        assertNull(PeruPhone.normalize(""))
    }
}
