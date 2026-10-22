package io.github.nikoapps.dialer.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCropTest {
    private val wide = PhotoSize(width = 4000, height = 3000)
    private val tall = PhotoSize(width = 600, height = 1000)

    private fun assertCrop(expected: CropSquare, actual: CropSquare) {
        assertEquals(expected.left, actual.left, 0.01f)
        assertEquals(expected.top, actual.top, 0.01f)
        assertEquals(expected.side, actual.side, 0.01f)
    }

    @Test
    fun `the default is the biggest square in the middle`() {
        assertCrop(CropSquare(left = 500f, top = 0f, side = 3000f), PhotoCrop.centred(wide))
        assertCrop(CropSquare(left = 0f, top = 200f, side = 600f), PhotoCrop.centred(tall))
        assertCrop(CropSquare(left = 0f, top = 0f, side = 100f), PhotoCrop.centred(PhotoSize(100, 100)))
    }

    @Test
    fun `fit whole photo puts all of it in the middle of the square`() {
        assertCrop(CropSquare(left = 0f, top = -500f, side = 4000f), PhotoCrop.fitWhole(wide))
        assertCrop(CropSquare(left = -200f, top = 0f, side = 1000f), PhotoCrop.fitWhole(tall))
    }

    @Test
    fun `fit whole photo undoes any drag and zoom`() {
        var crop = PhotoCrop.zoom(PhotoCrop.centred(wide), wide, factor = 3f, focusX = 0.1f, focusY = 0.9f)
        crop = PhotoCrop.move(crop, wide, dx = 0.4f, dy = -0.3f)
        assertCrop(PhotoCrop.fitWhole(wide), PhotoCrop.clamp(PhotoCrop.fitWhole(wide), wide))
        assertNotEquals(PhotoCrop.fitWhole(wide), crop)
    }

    @Test
    fun `dragging stops at the photo's edge`() {
        val start = PhotoCrop.centred(wide)
        // The photo dragged far right: the square reaches the photo's left edge and stops.
        assertCrop(CropSquare(left = 0f, top = 0f, side = 3000f), PhotoCrop.move(start, wide, dx = 5f, dy = 0f))
        assertCrop(CropSquare(left = 1000f, top = 0f, side = 3000f), PhotoCrop.move(start, wide, dx = -5f, dy = 0f))
        // No room up or down at this zoom.
        assertCrop(start, PhotoCrop.move(start, wide, dx = 0f, dy = 3f))
    }

    @Test
    fun `a small move goes a share of the square`() {
        val zoomed = PhotoCrop.zoom(PhotoCrop.centred(wide), wide, factor = 2f)
        val moved = PhotoCrop.move(zoomed, wide, dx = PhotoCrop.MOVE_STEP, dy = 0f)
        assertEquals(zoomed.left - 150f, moved.left, 0.01f)
        assertEquals(zoomed.top, moved.top, 0.01f)
    }

    @Test
    fun `zooming in keeps the middle where it was`() {
        val zoomed = PhotoCrop.zoom(PhotoCrop.centred(wide), wide, factor = 2f)
        assertCrop(CropSquare(left = 1250f, top = 750f, side = 1500f), zoomed)
    }

    @Test
    fun `zooming at a point keeps that point still`() {
        val start = PhotoCrop.zoom(PhotoCrop.centred(wide), wide, factor = 2f)
        val zoomed = PhotoCrop.zoom(start, wide, factor = 1.5f, focusX = 0.25f, focusY = 0.75f)
        assertEquals(start.left + 0.25f * start.side, zoomed.left + 0.25f * zoomed.side, 0.01f)
        assertEquals(start.top + 0.75f * start.side, zoomed.top + 0.75f * zoomed.side, 0.01f)
    }

    @Test
    fun `zoom stops at both ends`() {
        var crop = PhotoCrop.centred(wide)
        repeat(50) { crop = PhotoCrop.zoom(crop, wide, PhotoCrop.ZOOM_STEP) }
        assertEquals(PhotoCrop.minSide(wide), crop.side, 0.01f)
        assertEquals(375f, crop.side, 0.01f)
        repeat(50) { crop = PhotoCrop.zoom(crop, wide, 1 / PhotoCrop.ZOOM_STEP) }
        assertCrop(PhotoCrop.fitWhole(wide), crop)
    }

    @Test
    fun `a tiny photo can't be zoomed into`() {
        val tiny = PhotoSize(40, 30)
        assertEquals(30f, PhotoCrop.minSide(tiny), 0.01f)
        assertCrop(PhotoCrop.centred(tiny), PhotoCrop.zoom(PhotoCrop.centred(tiny), tiny, 4f))
    }

    @Test
    fun `zoomed out past the photo it stays in the middle the long way`() {
        val out = PhotoCrop.zoom(PhotoCrop.centred(wide), wide, factor = 0.8f)
        assertEquals(3750f, out.side, 0.01f)
        assertTrue(out.top < 0f)
        val moved = PhotoCrop.move(out, wide, dx = 0.2f, dy = 0.2f)
        assertEquals(out.top, moved.top, 0.01f)
        assertNotEquals(out.left, moved.left)
    }

    @Test
    fun `a bad zoom leaves the square as it was`() {
        val start = PhotoCrop.centred(wide)
        assertCrop(start, PhotoCrop.zoom(start, wide, 0f))
        assertCrop(start, PhotoCrop.zoom(start, wide, Float.NaN))
    }

    @Test
    fun `big photos are shrunk by powers of two until the long side fits`() {
        assertEquals(1, sampleSizeFor(1000, 800, 1600))
        assertEquals(1, sampleSizeFor(1600, 1200, 1600))
        assertEquals(2, sampleSizeFor(1601, 1200, 1600))
        assertEquals(2, sampleSizeFor(3200, 2400, 1600))
        assertEquals(4, sampleSizeFor(3201, 2400, 1600))
        assertEquals(8, sampleSizeFor(4000, 8000, 1600))
        assertEquals(1, sampleSizeFor(4000, 3000, 0))
    }

    @Test
    fun `the shrunk long side is never over the working size`() {
        for (long in listOf(1601, 2000, 3199, 4000, 4032, 6000, 9248, 12000)) {
            val sample = sampleSizeFor(long, long / 2, 1600)
            assertTrue("$long", (long + sample - 1) / sample <= 1600)
            assertTrue("$long", long / sample >= 800)
        }
    }

    @Test
    fun `exif orientations turn the photo upright`() {
        assertEquals(Upright(0, false), uprightFor(1))
        assertEquals(Upright(0, false), uprightFor(0))
        assertEquals(Upright(0, true), uprightFor(2))
        assertEquals(Upright(180, false), uprightFor(3))
        assertEquals(Upright(180, true), uprightFor(4))
        assertEquals(Upright(90, true), uprightFor(5))
        assertEquals(Upright(90, false), uprightFor(6))
        assertEquals(Upright(270, true), uprightFor(7))
        assertEquals(Upright(270, false), uprightFor(8))
        assertTrue(uprightFor(6).swapsSides)
        assertFalse(uprightFor(3).swapsSides)
    }

    @Test
    fun `photo files are named after the person`() {
        assertEquals("6f1c2a9e-1b2c-4d5e-8f90-123456789abc.jpg", photoFileName("6f1c2a9e-1b2c-4d5e-8f90-123456789abc"))
    }

    @Test
    fun `odd ids can't reach outside the photos folder`() {
        val name = photoFileName("../../shared_prefs/x")
        assertTrue(name.matches(Regex("id_[0-9a-f]{32}\\.jpg")))
        assertEquals(name, photoFileName("../../shared_prefs/x"))
        assertNotEquals(name, photoFileName("../../shared_prefs/y"))
        assertTrue(photoFileName("").startsWith("id_"))
    }
}
