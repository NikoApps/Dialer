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
)
