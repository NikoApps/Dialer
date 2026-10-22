package io.github.nikoapps.dialer.settings

import io.github.nikoapps.commons.settings.SpeechSpeed

/** What happens after a face is tapped. */
enum class WaitBeforeCalling {
    /** The call goes out straight away, while the name is spoken. */
    CallNow,

    /** The name is spoken, then the call goes out after 3 seconds unless Stop is pressed. */
    Wait3s,

    /** The name is spoken, then a big Call button waits to be pressed. */
    AskFirst,
}

/** Light or dark screens. Follows the phone unless a helper picks one. */
enum class ThemeChoice { Phone, Light, Dark }

/** Everything a helper can set. Defaults follow the Dialer spec. */
data class DialerSettings(
    /** How many places the grid has: 1, 2, 4 or 6. See [PEOPLE_ON_SCREEN_CHOICES]. */
    val peopleOnScreen: Int = DEFAULT_PEOPLE_ON_SCREEN,
    val waitBeforeCalling: WaitBeforeCalling = WaitBeforeCalling.Wait3s,
    val theme: ThemeChoice = ThemeChoice.Phone,
    /** Voice language as a BCP 47 tag such as "en", or null to follow the phone's language. */
    val voiceLanguage: String? = null,
    /** 1.0 is the engine's normal speed. The default is slightly slower, as in Clock. */
    val speechRate: Float = SpeechSpeed.Normal.rate,
    /** The helper finished or skipped first-run setup. */
    val setupDone: Boolean = false,
    /** The helper's 4-digit PIN hashed with [DialerHelperPin], or null when there is no PIN. */
    val helperPinHash: String? = null,
) {
    val speechSpeed: SpeechSpeed
        get() = SpeechSpeed.closestTo(speechRate)
}

/** The grid sizes a helper can pick. The layout depends only on this number. */
val PEOPLE_ON_SCREEN_CHOICES = listOf(1, 2, 4, 6)

const val DEFAULT_PEOPLE_ON_SCREEN = 4

/** The most places a grid can have, and so the most people Dialer keeps. */
const val MAX_PLACES = 6
