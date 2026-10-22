package io.github.nikoapps.dialer

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTest {

    @Test
    fun `with a PIN set, screens behind it go back to Unlock`() {
        for (screen in listOf(Screen.Settings, Screen.SetPin, Screen.Setup, Screen.EditPerson)) {
            assertEquals(screen.name, Screen.Unlock, screenAfterRestart(screen, pinSet = true))
        }
    }

    @Test
    fun `the main screen and Unlock come back as they were`() {
        assertEquals(Screen.Main, screenAfterRestart(Screen.Main, pinSet = true))
        assertEquals(Screen.Unlock, screenAfterRestart(Screen.Unlock, pinSet = true))
    }

    @Test
    fun `without a PIN every screen comes back`() {
        for (screen in Screen.entries) {
            assertEquals(screen.name, screen, screenAfterRestart(screen, pinSet = false))
        }
    }
}
