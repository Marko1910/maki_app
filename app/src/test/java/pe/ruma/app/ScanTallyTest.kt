package pe.ruma.app

import pe.ruma.app.data.DetectedItemDto
import pe.ruma.app.ui.screens.generator.UNNAMED_BRAND
import pe.ruma.app.ui.screens.generator.brandKey
import pe.ruma.app.ui.screens.generator.brandRows
import pe.ruma.app.ui.screens.generator.pileTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The counter on the camera has to say the number the scan books, so [pileTally] mirrors
 * `foldPhotos` in the `commit` of detect-material. The cases are the same as its test
 * "a second photo adds only what is new"; a change to one side must reach the other.
 */
class ScanTallyTest {

    private fun pet(qty: Int, brand: String?, quality: Double = 1.0, points: Int = 100 * qty) =
        DetectedItemDto(code = "PET", name = "Botella PET", quantity = qty, quality = quality, brand = brand, points = points)

    private val three = listOf(pet(1, "Concordia"), pet(1, "Loa"), pet(1, "Aguafiel"))

    private fun count(vararg photos: List<DetectedItemDto>) = pileTally(photos.toList()).single().quantity

    @Test
    fun `brands in one photo add up`() {
        val tally = pileTally(listOf(listOf(pet(3, "Coca Cola"), pet(2, "Inca Kola")))).single()
        assertEquals(5, tally.quantity)
        assertEquals(mapOf("Coca Cola" to 3, "Inca Kola" to 2), tally.brands)
    }

    @Test
    fun `only the new bottle in the second photo adds up`() {
        assertEquals(4, count(three, listOf(pet(1, "Gatorade"))))
    }

    @Test
    fun `the same pile framed again with a new bottle adds only that one`() {
        // Phone test 2026-11-01: this one used to come out as 3 + 4 = 7.
        assertEquals(4, count(three, three + pet(1, "Gatorade")))
        assertEquals("the same photo twice adds nothing", 3, count(three, three))
        assertEquals("part of the pile plus a new one", 4, count(three, listOf(pet(1, "Loa"), pet(1, "Aguafiel"), pet(1, "Gatorade"))))
        assertEquals("a brand that grew", 3, count(listOf(pet(1, "Loa")), listOf(pet(3, "Loa"))))
    }

    @Test
    fun `a label that became legible is not a new bottle`() {
        val hidden = listOf(pet(1, "Concordia"), pet(1, "Gatorade"), pet(2, null))
        val legible = listOf(pet(1, "Concordia"), pet(1, "Gatorade"), pet(1, "Loa"), pet(1, null))
        assertEquals(4, count(hidden, legible))
        // That unreadable one is still there and a Gatorade joined it.
        assertEquals(3, count(listOf(pet(1, "Loa"), pet(1, null)), listOf(pet(1, "Loa"), pet(1, "Gatorade"), pet(1, null))))
    }

    @Test
    fun `a label legible before and covered now is not a new bottle`() {
        assertEquals(2, count(listOf(pet(1, "Loa"), pet(1, "Aguafiel")), listOf(pet(1, "Loa"), pet(1, null))))
    }

    @Test
    fun `photos with nothing readable are separate waste`() {
        assertEquals(5, count(listOf(pet(3, null)), listOf(pet(2, null))))
    }

    @Test
    fun `spellings of one label match across photos`() {
        assertEquals(1, count(listOf(pet(1, "Agua Fiel")), listOf(pet(1, "AGUAFIEL"))))
        assertEquals(brandKey("Coca-Cola"), brandKey("coca cola"))
    }

    @Test
    fun `materials stay apart and points follow what each photo added`() {
        val can = DetectedItemDto(code = "ALU", name = "Lata", quantity = 2, quality = 1.0, points = 60)
        val tally = pileTally(listOf(three, three + pet(1, "Gatorade") + can))
        assertEquals(4, tally.first { it.code == "PET" }.quantity)
        assertEquals(2, tally.first { it.code == "ALU" }.quantity)
        // 300 for the first photo, then the second's 400 scaled to the one bottle it added.
        assertEquals(400, tally.first { it.code == "PET" }.points)
    }

    @Test
    fun `an empty photo in progress changes nothing`() {
        assertEquals(3, count(three, emptyList()))
        assertTrue(pileTally(listOf(emptyList())).isEmpty())
    }

    @Test
    fun `the brand rows account for every unit`() {
        // Phone test: four bottles, two labels hidden — two brands under a 4 looked wrong.
        assertEquals(
            mapOf("Aguafiel" to 1, "Gatorade" to 1, UNNAMED_BRAND to 2),
            brandRows(mapOf("Aguafiel" to 1, "Gatorade" to 1), 4),
        )
        // Two spellings of one label are one row.
        assertEquals(mapOf("Agua Fiel" to 1, "Loa" to 1), brandRows(linkedMapOf("Agua Fiel" to 1, "Aguafiel" to 1, "Loa" to 1), 2))
        // No label read at all: no rows, the material's own stepper covers it.
        assertEquals(emptyMap<String, Int>(), brandRows(emptyMap(), 3))
    }
}
