package io.github.nikoapps.dialer.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import io.github.nikoapps.commons.settings.SpeechSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsEncodingTest {

    @Test
    fun `nothing stored gives the spec defaults`() {
        val s = mutablePreferencesOf().toSettings()
        assertEquals(DialerSettings(), s)
        assertEquals(4, s.peopleOnScreen)
        assertEquals(WaitBeforeCalling.Wait3s, s.waitBeforeCalling)
        assertEquals(ThemeChoice.Phone, s.theme)
        assertNull(s.voiceLanguage)
        assertEquals(SpeechSpeed.Normal, s.speechSpeed)
        assertNull(s.helperPinHash)
        assertEquals(false, s.onlySavedContacts)
    }

    @Test
    fun `every setting reads back as written`() {
        val written = DialerSettings(
            peopleOnScreen = 6,
            waitBeforeCalling = WaitBeforeCalling.AskFirst,
            theme = ThemeChoice.Dark,
            voiceLanguage = "en",
            speechRate = SpeechSpeed.Slow.rate,
            onlySavedContacts = true,
            setupDone = true,
            helperPinHash = DialerHelperPin.hash("1234"),
        )
        val prefs = mutablePreferencesOf().apply { write(written) }
        assertEquals(written, prefs.toSettings())
    }

    @Test
    fun `going back to the phone's language and removing the PIN clear their keys`() {
        val prefs = mutablePreferencesOf().apply {
            write(DialerSettings(voiceLanguage = "en", helperPinHash = "abc"))
            write(DialerSettings())
        }
        assertNull(prefs[stringPreferencesKey("voice_language")])
        assertNull(prefs[stringPreferencesKey("helper_pin_hash")])
        assertEquals(DialerSettings(), prefs.toSettings())
    }

    @Test
    fun `unknown or broken values fall back to defaults`() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("people_on_screen") to 5,
            stringPreferencesKey("wait_before_calling") to "Wait10s",
            stringPreferencesKey("theme") to "Sepia",
            floatPreferencesKey("speech_rate") to 0f,
        )
        assertEquals(DialerSettings(), prefs.toSettings())
    }

    @Test
    fun `a stored speech rate maps to the closest of the three speeds`() {
        assertEquals(SpeechSpeed.Fast, DialerSettings(speechRate = 1.2f).speechSpeed)
        assertEquals(SpeechSpeed.Slow, DialerSettings(speechRate = 0.7f).speechSpeed)
    }

    @Test
    fun `the PIN salt never changes`() {
        // Saved PINs stop matching if this does. Expected value is SHA-256 of "niko-dialer-helper-pin:1234".
        assertEquals(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest("niko-dialer-helper-pin:1234".toByteArray())
                .joinToString("") { "%02x".format(it) },
            DialerHelperPin.hash("1234"),
        )
        assert(DialerHelperPin.matches("1234", DialerHelperPin.hash("1234")))
        assert(!DialerHelperPin.matches("4321", DialerHelperPin.hash("1234")))
    }
}
