package io.github.nikoapps.dialer.settings

/** What happens after a face is tapped. */
enum class WaitBeforeCalling {
    /** The call goes out straight away, while the name is spoken. */
    CallNow,

    /** The name is spoken, then the call goes out after 3 seconds unless Stop is pressed. */
    Wait3s,

    /** The name is spoken, then a big Call button waits to be pressed. */
    AskFirst,
}

/** Everything a helper can set. Defaults follow the Dialer spec. */
data class DialerSettings(
    /** How many places the grid has: 1, 2, 4 or 6. See [PEOPLE_ON_SCREEN_CHOICES]. */
    val peopleOnScreen: Int = DEFAULT_PEOPLE_ON_SCREEN,
    val waitBeforeCalling: WaitBeforeCalling = WaitBeforeCalling.Wait3s,
    /** The helper finished or skipped first-run setup. */
    val setupDone: Boolean = false,
    /** SHA-256 of the helper's 4-digit PIN, or null when there is no PIN. */
    val helperPinHash: String? = null,
)

/** The grid sizes a helper can pick. The layout depends only on this number. */
val PEOPLE_ON_SCREEN_CHOICES = listOf(1, 2, 4, 6)

const val DEFAULT_PEOPLE_ON_SCREEN = 4

/** The most places a grid can have, and so the most people Dialer keeps. */
const val MAX_PLACES = 6
