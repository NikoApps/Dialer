package io.github.nikoapps.dialer.screening

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningDecisionTest {

    private val android10 = 29
    private val android16 = 36

    @Test
    fun `an incoming call from an unsaved number is silenced while the setting is on`() {
        assertEquals(Screening.Silence, screeningFor(android10, onlySavedContacts = true, CallDirection.Incoming))
        assertEquals(Screening.Silence, screeningFor(android16, onlySavedContacts = true, CallDirection.Incoming))
    }

    @Test
    fun `with the setting off every call rings`() {
        for (direction in CallDirection.entries) {
            assertEquals(Screening.Allow, screeningFor(android16, onlySavedContacts = false, direction))
        }
    }

    @Test
    fun `outgoing calls and calls of unknown direction are never touched`() {
        assertEquals(Screening.Allow, screeningFor(android16, onlySavedContacts = true, CallDirection.Outgoing))
        assertEquals(Screening.Allow, screeningFor(android16, onlySavedContacts = true, CallDirection.Unknown))
    }

    @Test
    fun `before Android 10 nothing is silenced`() {
        for (sdk in 26..28) {
            assertEquals(Screening.Allow, screeningFor(sdk, onlySavedContacts = true, CallDirection.Incoming))
        }
    }
}
