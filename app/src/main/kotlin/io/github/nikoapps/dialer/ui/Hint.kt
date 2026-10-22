package io.github.nikoapps.dialer.ui

import androidx.annotation.StringRes
import io.github.nikoapps.dialer.R

/** What the hint line under the grid says. Problems that stop a call come first. */
enum class Hint(@StringRes val text: Int) {
    Ready(R.string.hint_ready),
    NoPeople(R.string.hint_no_people),
    CallPermission(R.string.hint_call_permission),
    CallBlocked(R.string.hint_call_blocked),
    CallFailed(R.string.hint_call_failed),
}

/**
 * [callsBlocked] is when the call permission was refused and the phone won't ask again.
 * With nobody on the grid there's no face to tap, so that comes before the permission hint.
 */
fun hintFor(hasPeople: Boolean, canCall: Boolean, callsBlocked: Boolean, lastCallFailed: Boolean): Hint = when {
    lastCallFailed -> Hint.CallFailed
    !canCall && callsBlocked -> Hint.CallBlocked
    !hasPeople -> Hint.NoPeople
    !canCall -> Hint.CallPermission
    else -> Hint.Ready
}
