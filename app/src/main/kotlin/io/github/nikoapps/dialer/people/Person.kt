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
) {
    val hasPhoto: Boolean get() = photoVersion > 0
}
