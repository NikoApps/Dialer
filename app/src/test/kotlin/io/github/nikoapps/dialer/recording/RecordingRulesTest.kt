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
    fun `the contact ringtone is set when it's ours and still on the phone`() {
        assertEquals(RingtoneState.Set, ringtoneState("content://ours", "content://ours", oursExists = true, contactRingtoneGone = false))
    }

    @Test
    fun `a first ringtone is set quietly`() {
        assertEquals(RingtoneState.NeedsSetting, ringtoneState(null, "", oursExists = false, contactRingtoneGone = false))
        assertEquals(RingtoneState.NeedsSetting, ringtoneState("", "", oursExists = false, contactRingtoneGone = false))
    }

    @Test
    fun `after a move or repair, when Dialer's own ringtone is gone, it is set again quietly`() {
        // The contact still points at the old phone's item, or lost its ringtone with the move.
        assertEquals(RingtoneState.NeedsSetting, ringtoneState("content://ours", "content://ours", oursExists = false, contactRingtoneGone = false))
        assertEquals(RingtoneState.NeedsSetting, ringtoneState(null, "content://ours", oursExists = false, contactRingtoneGone = false))
    }

    @Test
    fun `choosing the phone's default ringtone is left alone`() {
        assertEquals(RingtoneState.ChosenElsewhere, ringtoneState(null, "content://ours", oursExists = true, contactRingtoneGone = false))
        assertEquals(RingtoneState.ChosenElsewhere, ringtoneState("", "content://ours", oursExists = true, contactRingtoneGone = false))
    }

    @Test
    fun `a ringtone chosen in the contacts app isn't ours`() {
        val chosen = "content://media/internal/audio/media/12"
        assertEquals(RingtoneState.ChosenElsewhere, ringtoneState(chosen, "content://ours", oursExists = true, contactRingtoneGone = false))
        assertEquals(RingtoneState.ChosenElsewhere, ringtoneState(chosen, "", oursExists = false, contactRingtoneGone = false))
    }

    @Test
    fun `a contact ringtone that's no longer on the phone is set again quietly`() {
        assertEquals(RingtoneState.NeedsSetting, ringtoneState("content://old", "content://ours", oursExists = true, contactRingtoneGone = true))
        assertEquals(RingtoneState.NeedsSetting, ringtoneState("content://old", "content://ours", oursExists = false, contactRingtoneGone = true))
    }

    @Test
    fun `removing only clears a ringtone that is ours`() {
        assertTrue(ringtoneIsOurs("content://new", setOf("content://new")))
        assertFalse(ringtoneIsOurs("content://other", setOf("content://new")))
        assertFalse(ringtoneIsOurs(null, setOf("content://new")))
        assertFalse(ringtoneIsOurs("", setOf("")))
    }
}
