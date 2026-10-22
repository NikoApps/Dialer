package io.github.nikoapps.dialer.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class VoiceLanguagesTest {

    @Test
    fun `same as phone comes first, then each language with a sentence`() {
        assertEquals(listOf(null, "en", "ne-NP", "hi-IN", "bn", "ur", "ar", "es", "pt-BR", "fr", "id"), VOICE_LANGUAGES)
    }

    @Test
    fun `same as phone follows the phone, country included`() {
        assertEquals(Locale.forLanguageTag("en-IN"), voiceLocaleFor(null, Locale.forLanguageTag("en-IN")))
        assertEquals(Locale.forLanguageTag("ne-NP"), voiceLocaleFor(null, Locale.forLanguageTag("ne-NP")))
    }

    @Test
    fun `a phone language without a sentence says nothing`() {
        assertNull(voiceLocaleFor(null, Locale.forLanguageTag("de-DE")))
    }

    @Test
    fun `the helper's choice wins over the phone`() {
        assertEquals("ne", voiceLocaleFor("ne-NP", Locale.ENGLISH)?.language)
        assertEquals("en", voiceLocaleFor("en", Locale.forLanguageTag("ne-NP"))?.language)
    }

    @Test
    fun `Indonesian has a sentence under its old and new code`() {
        assertEquals("Indonesian", voiceLocaleFor("id", Locale.ENGLISH)?.getDisplayLanguage(Locale.ENGLISH))
        @Suppress("DEPRECATION")
        assertEquals("Indonesian", voiceLocaleFor(null, Locale("in", "ID"))?.getDisplayLanguage(Locale.ENGLISH))
    }

    @Test
    fun `a stored language Dialer no longer has says nothing`() {
        assertNull(voiceLocaleFor("xx", Locale.ENGLISH))
    }
}
