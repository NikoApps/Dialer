package io.github.nikoapps.dialer.ui

import io.github.nikoapps.commons.speech.VoiceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HintTest {

    @Test
    fun `all well says how to use it`() {
        assertEquals(Hint.Ready, hintFor(hasPeople = true, canCall = true, callsBlocked = false, lastCallFailed = false))
    }

    @Test
    fun `missing call permission is a warning`() {
        assertEquals(Hint.CallPermission, hintFor(hasPeople = true, canCall = false, callsBlocked = false, lastCallFailed = false))
        assertEquals(Hint.CallBlocked, hintFor(hasPeople = true, canCall = false, callsBlocked = true, lastCallFailed = false))
    }

    @Test
    fun `an empty grid says a helper adds people, unless calls are blocked`() {
        assertEquals(Hint.NoPeople, hintFor(hasPeople = false, canCall = false, callsBlocked = false, lastCallFailed = false))
        assertEquals(Hint.CallBlocked, hintFor(hasPeople = false, canCall = false, callsBlocked = true, lastCallFailed = false))
    }

    @Test
    fun `a failed call comes first`() {
        assertEquals(Hint.CallFailed, hintFor(hasPeople = true, canCall = true, callsBlocked = false, lastCallFailed = true))
    }

    @Test
    fun `allowed again clears the blocked warning`() {
        assertEquals(Hint.Ready, hintFor(hasPeople = true, canCall = true, callsBlocked = true, lastCallFailed = false))
    }

    @Test
    fun `a voice problem comes before the call permission and other warnings`() {
        for (voice in listOf(VoiceStatus.NoVoice, VoiceStatus.NoEngine, VoiceStatus.LanguageNotSupported)) {
            assertEquals(
                Hint.Voice,
                hintFor(hasPeople = true, canCall = false, callsBlocked = false, lastCallFailed = false, voice = voice, needsAttention = true),
            )
        }
    }

    @Test
    fun `a voice that is still starting isn't a problem`() {
        assertEquals(
            Hint.Ready,
            hintFor(hasPeople = true, canCall = true, callsBlocked = false, lastCallFailed = false, voice = VoiceStatus.Starting),
        )
    }

    @Test
    fun `blocked calls, a failed call and an empty grid still come before the voice`() {
        val noVoice = VoiceStatus.NoVoice
        assertEquals(Hint.CallBlocked, hintFor(hasPeople = true, canCall = false, callsBlocked = true, lastCallFailed = false, voice = noVoice))
        assertEquals(Hint.CallFailed, hintFor(hasPeople = true, canCall = true, callsBlocked = false, lastCallFailed = true, voice = noVoice))
        assertEquals(Hint.NoPeople, hintFor(hasPeople = false, canCall = true, callsBlocked = false, lastCallFailed = false, voice = noVoice))
    }

    @Test
    fun `other phone settings show last, after voice and calls`() {
        assertEquals(
            Hint.NeedsAttention,
            hintFor(hasPeople = true, canCall = true, callsBlocked = false, lastCallFailed = false, needsAttention = true),
        )
        assertEquals(
            Hint.CallPermission,
            hintFor(hasPeople = true, canCall = false, callsBlocked = false, lastCallFailed = false, needsAttention = true),
        )
    }

    @Test
    fun `lost silencing comes after voice and calls, before other phone settings`() {
        assertEquals(
            Hint.SilencingLost,
            hintFor(hasPeople = true, canCall = true, callsBlocked = false, lastCallFailed = false, silencingLost = true, needsAttention = true),
        )
        assertEquals(
            Hint.CallPermission,
            hintFor(hasPeople = true, canCall = false, callsBlocked = false, lastCallFailed = false, silencingLost = true, needsAttention = true),
        )
        assertEquals(
            Hint.Voice,
            hintFor(
                hasPeople = true,
                canCall = true,
                callsBlocked = false,
                lastCallFailed = false,
                voice = VoiceStatus.NoVoice,
                silencingLost = true,
            ),
        )
        assertEquals(
            Hint.NoPeople,
            hintFor(hasPeople = false, canCall = true, callsBlocked = false, lastCallFailed = false, silencingLost = true),
        )
    }
}
