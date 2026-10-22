package io.github.nikoapps.dialer.speech

import java.util.Locale

/**
 * The voice language choices in settings, with null for "Same as phone", in the same order and with
 * the same tags as Niko Clock. Each has a hand-written "Calling {name}" sentence, `calling_name`,
 * and a `voice_test_name` in its translated strings file. Add a language here only together with those.
 * Arabic is the one Clock shows on screen but doesn't speak; Dialer's sentence has no numbers in it,
 * so it can.
 */
val VOICE_LANGUAGES: List<String?> = listOf(null, "en", "ne-NP", "hi-IN", "bn", "ur", "ar", "es", "pt-BR", "fr", "id")

/** Language codes with a sentence. Android still calls Indonesian "in"; newer code says "id". */
private val TEMPLATE_LANGUAGES: Set<String> =
    VOICE_LANGUAGES.filterNotNull().map { it.substringBefore('-') }.toSet() + "in"

/**
 * The language to say "Calling Mum" in: the helper's choice, or the phone's language.
 * Null when Dialer has no sentence for it, so it says nothing rather than reading another
 * language's words with this language's voice.
 */
fun voiceLocaleFor(voiceLanguage: String?, phone: Locale): Locale? {
    val locale = voiceLanguage?.let(Locale::forLanguageTag) ?: phone
    return locale.takeIf { it.language in TEMPLATE_LANGUAGES }
}
