package io.github.nikoapps.dialer.people

import androidx.compose.ui.graphics.toArgb
import io.github.nikoapps.commons.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonColorsTest {

    @Test
    fun `the silhouette meets 7 to 1 on every person colour`() {
        val ink = SilhouetteColor.toArgb().toLong() and 0xFFFFFFFFL
        PersonPalette.forEach { colour ->
            val ratio = contrastRatio(ink, colour.toArgb().toLong() and 0xFFFFFFFFL)
            assertTrue("$colour gives $ratio", ratio >= 7.0)
        }
    }

    @Test
    fun `the same person always gets the same colour`() {
        val first = personColorIndex("6f1c-4e")
        repeat(10) { assertEquals(first, personColorIndex("6f1c-4e")) }
    }

    @Test
    fun `every id maps into the palette, including negative hash codes`() {
        listOf("", "a", "polygenelubricants", "zzzzzzzzzzzzzzzz", "आमा").forEach { id ->
            assertTrue(personColorIndex(id) in PersonPalette.indices)
        }
    }
}
