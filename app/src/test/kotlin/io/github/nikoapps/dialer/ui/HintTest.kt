package io.github.nikoapps.dialer.ui

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
}
