package io.github.nikoapps.dialer.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingRulesTest {
    private val format = { name: String -> "$name is calling - Niko Dialer" }

    @Test
    fun `the ringtone title is the name and the app, never the number`() {
        assertEquals("Mum is calling - Niko Dialer", ringtoneTitle("Mum", "Someone", format))
        assertEquals("आमा is calling - Niko Dialer", ringtoneTitle("  आमा ", "Someone", format))
    }

    @Test
    fun `characters files can't hold are taken out of the title`() {
        assertEquals("Ram Sita is calling - Niko Dialer", ringtoneTitle("Ram/Sita", "Someone", format))
        assertEquals("A B is calling - Niko Dialer", ringtoneTitle("A\n\tB", "Someone", format))
        assertEquals("Someone is calling - Niko Dialer", ringtoneTitle(" ?*: ", "Someone", format))
    }

    @Test
    fun `a long name is cut so the file name stays short`() {
        val title = ringtoneTitle("x".repeat(500), "Someone", format)
        assertTrue(title.length <= 80)
        assertTrue(ringtoneFileName(title).length <= 84)
    }

    @Test
    fun `the ringtone file is named after its title`() {
        assertEquals("Mum is calling - Niko Dialer.wav", ringtoneFileName("Mum is calling - Niko Dialer"))
        assertEquals("Niko Dialer.wav", ringtoneFileName(" "))
    }

    @Test
    fun `the master recording is named after the person, safely`() {
        assertEquals("6f1c2a9e-1b2c-4d5e-8f90-123456789abc.wav", recordingFileName("6f1c2a9e-1b2c-4d5e-8f90-123456789abc"))
        assertTrue(recordingFileName("../../x").startsWith("id_"))
    }

    @Test
    fun `the contact ringtone is set when it's ours now`() {
        assertEquals(RingtoneState.Set, ringtoneState("content://new", "content://new", setOf("content://new")))
    }

    @Test
    fun `no ringtone, or an old one of ours, is set again quietly`() {
        assertEquals(RingtoneState.NeedsSetting, ringtoneState(null, "content://new", setOf("content://old", "content://new")))
        assertEquals(RingtoneState.NeedsSetting, ringtoneState("", "content://new", setOf("content://new")))
        // After moving to a new phone: the contact still points at the old phone's item.
        assertEquals(RingtoneState.NeedsSetting, ringtoneState("content://old", "content://new", setOf("content://old", "content://new")))
    }

    @Test
    fun `a ringtone chosen in the contacts app isn't ours`() {
        assertEquals(RingtoneState.ChosenElsewhere, ringtoneState("content://media/internal/audio/media/12", "content://new", setOf("content://new")))
    }

    @Test
    fun `removing only clears a ringtone that is ours`() {
        assertTrue(ringtoneIsOurs("content://new", setOf("content://new")))
        assertFalse(ringtoneIsOurs("content://other", setOf("content://new")))
        assertFalse(ringtoneIsOurs(null, setOf("content://new")))
        assertFalse(ringtoneIsOurs("", setOf("")))
    }
}
