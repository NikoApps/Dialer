package io.github.nikoapps.dialer

/**
 * The screens. Settings is reached through Unlock once a PIN is set; the PIN screen and the
 * people editor open from settings or setup and go back to where they came from.
 *
 * The calling screen isn't one of them: it shows over [Screen.Main] whenever the call view model
 * has a call waiting, so Stop and a placed call always land back on the grid.
 */
internal enum class Screen {
    Main,
    Unlock,
    Settings,
    SetPin,
    Setup,
    EditPerson,
    ;

    /** Only a helper who knows the PIN gets here, once one is set. */
    val behindPin: Boolean
        get() = this != Main && this != Unlock
}

/**
 * The screen to show when Android brings Dialer back after stopping it in the background. The
 * saved screen comes back with it, but the PIN was entered in the old process, so a screen behind
 * the PIN goes back to Unlock while a PIN is set. A config change, such as turning the phone,
 * keeps the process and never comes here.
 */
internal fun screenAfterRestart(saved: Screen, pinSet: Boolean): Screen =
    if (pinSet && saved.behindPin) Screen.Unlock else saved

/**
 * The screen to keep when Dialer leaves the front, such as when Home is pressed. With a PIN set,
 * a screen behind it goes back to the grid, so the next person to open Dialer can't change
 * settings. Turning the phone ([changingConfig]) keeps it, and so does a screen Dialer opened
 * itself ([openedAnotherScreen]), such as the contact picker or the camera, so a helper is never
 * thrown out halfway through adding someone.
 */
internal fun screenAfterLeaving(current: Screen, pinSet: Boolean, changingConfig: Boolean, openedAnotherScreen: Boolean): Screen =
    if (pinSet && current.behindPin && !changingConfig && !openedAnotherScreen) Screen.Main else current
