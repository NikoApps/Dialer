package io.github.nikoapps.dialer.speech

import java.util.Locale

/**
 * Languages Niko Dialer has a hand-written "Calling {name}" sentence for, in the translated strings files.
 * Add a language here only together with its translated `calling_name` and `voice_test_name`.
 */
private val TEMPLATE_LANGUAGES = setOf("en")

/** The voice language choices in settings, with null for "Same as phone". */
val VOICE_LANGUAGES: List<String?> = listOf(null) + TEMPLATE_LANGUAGES.sorted()

/**
 * The language to say "Calling Mum" in: the helper's choice, or the phone's language.
 * Null when Dialer has no sentence for it, so it says nothing rather than reading English
 * words with another language's voice.
 */
fun voiceLocaleFor(voiceLanguage: String?, phone: Locale): Locale? {
    val locale = voiceLanguage?.let(Locale::forLanguageTag) ?: phone
    return locale.takeIf { it.language in TEMPLATE_LANGUAGES }
}
