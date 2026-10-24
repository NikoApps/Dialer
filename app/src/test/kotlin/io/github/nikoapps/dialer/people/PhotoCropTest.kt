package io.github.nikoapps.dialer.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCropTest {
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
