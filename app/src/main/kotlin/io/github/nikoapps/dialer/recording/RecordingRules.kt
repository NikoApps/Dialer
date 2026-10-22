package io.github.nikoapps.dialer.recording

import io.github.nikoapps.dialer.people.personFileStem

// The recorded name rules, without Android, so they're tested on the JVM: how long a recording
// is, what its files are called, and whether the contact's ringtone is Dialer's.

/** One tap starts, one tap stops, and it stops by itself after this long. */
const val MAX_RECORDING_MS = 5_000

/** Silence after the name, so the ringtone loops as "Mum is calling ... Mum is calling". */
const val SILENCE_MS = 1_500

/** Anything shorter is an accidental double tap, not a name. */
const val MIN_RECORDING_MS = 300

/** The master recording's file name in Dialer's own files, from the person's id. */
fun recordingFileName(personId: String): String = personFileStem(personId) + ".wav"

/** Characters file systems or the ringtone list don't take, and line breaks. */
private val unsafe = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
private val spaces = Regex("\\s+")

/** Longest title kept, so the file name stays well inside what any file system allows. */
private const val MAX_TITLE = 80

/**
 * The ringtone's title in the phone's ringtone list, such as "Mum is calling - Niko Dialer", made
 * by [format] from the person's name only, never their number. The file is named after it too,
 * so the list shows the same words even when the phone reads names from the file.
 * [fallbackName] stands in for a name that has nothing left once cleaned.
 */
fun ringtoneTitle(name: String, fallbackName: String, format: (String) -> String): String {
    val shown = clean(name).ifEmpty { clean(fallbackName) }
    return clean(format(shown)).take(MAX_TITLE).trim()
}

/** The ringtone's file name in the shared Ringtones folder. */
fun ringtoneFileName(title: String): String = clean(title).ifEmpty { "Niko Dialer" } + ".wav"

private fun clean(text: String): String = text.replace(unsafe, " ").replace(spaces, " ").trim().trim('.').trim()

/** Whether the contact's ringtone is the one Dialer means it to be. */
enum class RingtoneState {
    /** It's Dialer's recording now. */
    Set,

    /**
     * Never set yet, or Dialer's own ringtone went missing, such as after moving to a new phone, or
     * the contact's ringtone points at a sound that's gone. Safe to set again quietly.
     */
    NeedsSetting,

    /** Someone chose another ringtone, or the phone's default, such as in the contacts app. Only set again when the helper asks. */
    ChosenElsewhere,
}

/**
 * [contactRingtone] is the contact's ringtone now, null or blank for the phone's default. [ours]
 * is the ringtone Dialer last set for this person, blank when it never set one, and [oursExists]
 * whether that ringtone is still on this phone. [contactRingtoneGone] is true when the contact's
 * ringtone is known to point at a sound that's no longer on the phone.
 *
 * A blank ringtone is only set again quietly when Dialer never set one or its own one is gone too,
 * as after a move. Otherwise someone chose the phone's default, and that choice stays.
 */
fun ringtoneState(contactRingtone: String?, ours: String, oursExists: Boolean, contactRingtoneGone: Boolean): RingtoneState = when {
    ours.isNotBlank() && contactRingtone == ours -> if (oursExists) RingtoneState.Set else RingtoneState.NeedsSetting
    contactRingtone.isNullOrBlank() -> if (ours.isBlank() || !oursExists) RingtoneState.NeedsSetting else RingtoneState.ChosenElsewhere
    contactRingtoneGone -> RingtoneState.NeedsSetting
    else -> RingtoneState.ChosenElsewhere
}

/** True when the contact's ringtone is one Dialer set, so removing the recording may clear it. A ringtone chosen elsewhere stays. */
fun ringtoneIsOurs(contactRingtone: String?, ours: Set<String>): Boolean =
    !contactRingtone.isNullOrBlank() && contactRingtone in ours
