package io.github.nikoapps.dialer.screening

import android.os.Build

/** Which way a call is going, as Android reports it to the screening service. */
enum class CallDirection { Incoming, Outgoing, Unknown }

/** What Dialer tells Android to do with a call it was asked about. */
enum class Screening {
    /** Let it ring as usual. */
    Allow,

    /** Don't ring. The call still shows in the call log and as a missed call. */
    Silence,
}

/** The first Android version that lets an app other than the phone app hold the call screening role. */
const val SCREENING_MIN_SDK = Build.VERSION_CODES.Q

/**
 * Android only asks a call screening app that isn't the phone app about numbers that aren't
 * saved in the phone's contacts, so any incoming call that reaches here is from an unsaved
 * number. It's silenced only while the helper has "Only ring for saved contacts" on.
 * Outgoing calls and calls of unknown direction always go through untouched.
 */
fun screeningFor(sdkInt: Int, onlySavedContacts: Boolean, direction: CallDirection): Screening = when {
    sdkInt < SCREENING_MIN_SDK -> Screening.Allow
    !onlySavedContacts -> Screening.Allow
    direction != CallDirection.Incoming -> Screening.Allow
    else -> Screening.Silence
}
