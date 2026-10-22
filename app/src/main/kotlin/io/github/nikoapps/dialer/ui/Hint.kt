package io.github.nikoapps.dialer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.nikoapps.commons.speech.VoiceStatus
import io.github.nikoapps.commons.ui.voiceMessage
import io.github.nikoapps.dialer.R

/** What the hint line under the grid says. Problems that stop a call come first. */
enum class Hint {
    Ready,
    NoPeople,
    Voice,
    CallPermission,
    CallBlocked,
    CallFailed,

    /** "Only ring for saved contacts" is on, but Dialer lost the right to silence calls. */
    SilencingLost,

    /** A recorded name may not play when that person calls. */
    RecordedNames,

    /** A phone setting, such as pausing unused apps, may stop calls later. */
    NeedsAttention,
}

/**
 * [callsBlocked] is when the call permission was refused and the phone won't ask again.
 * With nobody on the grid there's no face to tap, so that comes before the voice and the
 * permission hints. Voice and calls come before other health warnings, as the spec asks.
 * [silencingLost] comes next, since unsaved numbers ring again, then [recordedNames], when a
 * recorded name may not play. [needsAttention] is any other problem listed in settings.
 */
fun hintFor(
    hasPeople: Boolean,
    canCall: Boolean,
    callsBlocked: Boolean,
    lastCallFailed: Boolean,
    voice: VoiceStatus = VoiceStatus.Ready,
    silencingLost: Boolean = false,
    recordedNames: Boolean = false,
    needsAttention: Boolean = false,
): Hint = when {
    lastCallFailed -> Hint.CallFailed
    !canCall && callsBlocked -> Hint.CallBlocked
    !hasPeople -> Hint.NoPeople
    voice.isProblem -> Hint.Voice
    !canCall -> Hint.CallPermission
    silencingLost -> Hint.SilencingLost
    recordedNames -> Hint.RecordedNames
    needsAttention -> Hint.NeedsAttention
    else -> Hint.Ready
}

/** Starting isn't a problem: the voice is on its way. */
val VoiceStatus.isProblem: Boolean
    get() = this != VoiceStatus.Starting && this != VoiceStatus.Ready

@Composable
fun hintText(hint: Hint, voice: VoiceStatus): String = when (hint) {
    Hint.Ready -> stringResource(R.string.hint_ready)
    Hint.NoPeople -> stringResource(R.string.hint_no_people)
    Hint.Voice -> voiceMessage(voice) ?: stringResource(R.string.hint_ready)
    Hint.CallPermission -> stringResource(R.string.hint_call_permission)
    Hint.CallBlocked -> stringResource(R.string.hint_call_blocked)
    Hint.CallFailed -> stringResource(R.string.hint_call_failed)
    Hint.SilencingLost -> stringResource(R.string.hint_silencing_lost)
    Hint.RecordedNames -> stringResource(R.string.hint_recorded_names)
    Hint.NeedsAttention -> stringResource(R.string.hint_needs_attention)
}
