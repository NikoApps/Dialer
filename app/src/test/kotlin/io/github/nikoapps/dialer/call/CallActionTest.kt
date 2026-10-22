package io.github.nikoapps.dialer.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallActionTest {

    @Test
    fun `a normal number is called straight away`() {
        assertEquals(CallAction.Call("9800000001"), callActionFor(" 9800000001 ") { false })
    }

    @Test
    fun `an emergency number opens the dialer with the number ready`() {
        assertEquals(CallAction.Dial("112"), callActionFor("112") { it == "112" })
    }

    @Test
    fun `a blank number calls nothing and never asks the phone`() {
        var asked = false
        assertEquals(CallAction.Nothing, callActionFor("  ") { asked = true; false })
        assertEquals(CallAction.Nothing, callActionFor("+-") { asked = true; false })
        assertFalse(asked)
    }

    @Test
    fun `the phone's own answer wins`() {
        assertTrue(isEmergencyNumber("12345", systemAnswer = true))
        assertFalse(isEmergencyNumber("112", systemAnswer = false))
    }

    @Test
    fun `without an answer from the phone, common emergency numbers still open the dialer`() {
        assertTrue(isEmergencyNumber("112", systemAnswer = null))
        assertTrue(isEmergencyNumber("1 0 2", systemAnswer = null))
        assertTrue(isEmergencyNumber("911", systemAnswer = null))
        assertFalse(isEmergencyNumber("9800000001", systemAnswer = null))
    }
}
