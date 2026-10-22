package io.github.nikoapps.dialer.people

import androidx.compose.runtime.Immutable

/**
 * Someone the person using Dialer can call.
 *
 * [place] is the person's fixed spot on the grid, counted from 0 at the top left, row by row.
 * It only changes when the helper moves them. A place at or past the grid size is kept but
 * not shown, so making the grid smaller and then bigger again brings everyone back.
 */
@Immutable
data class Person(
    val id: String,
    /** As the helper typed it, in any script. Shown and spoken as it is. */
    val name: String,
    val number: String,
    val place: Int,
    /**
     * 0 when there's no photo. Otherwise the photo is in Dialer's own files under the person's
     * id, and this goes up by one each time it changes, so the face shows the new one.
     */
    val photoVersion: Int = 0,
    /**
     * The phone contact this person is linked to, so their recorded name can be set as that
     * contact's ringtone. Empty when they aren't linked, such as a typed person saved while
     * contacts weren't allowed. The lookup key keeps working when the contact's id changes.
     */
    val contactLookupKey: String = "",
    /** The contact's id when it was linked, 0 when unknown. Only a hint: [contactLookupKey] decides. */
    val contactId: Long = 0,
    /**
     * 0 when there's no recorded name. Otherwise the master recording is in Dialer's own files
     * under the person's id, and this goes up by one each time it's recorded again.
     */
    val recordingVersion: Int = 0,
    /**
     * The phone's ringtone item Dialer last set as the contact's ringtone, as a content uri.
     * Empty when none is set. Kept so Dialer can tell its own ringtone from one chosen in the
     * contacts app, even after moving to a new phone, where this uri no longer exists.
     */
    val ringtoneUri: String = "",
) {
    val hasPhoto: Boolean get() = photoVersion > 0
    val hasRecording: Boolean get() = recordingVersion > 0
    val hasContact: Boolean get() = contactLookupKey.isNotEmpty()
}
