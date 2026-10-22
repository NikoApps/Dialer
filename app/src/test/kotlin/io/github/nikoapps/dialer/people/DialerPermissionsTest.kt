package io.github.nikoapps.dialer.people

import org.junit.Assert.assertEquals
import org.junit.Test

class DialerPermissionsTest {
    private val mic = "android.permission.RECORD_AUDIO"
    private val read = "android.permission.READ_CONTACTS"
    private val write = "android.permission.WRITE_CONTACTS"
    private val storage = "android.permission.WRITE_EXTERNAL_STORAGE"
    private val none: (String) -> Boolean = { false }

    @Test
    fun `saving a contact only asks for contacts, on every Android version`() {
        for (sdk in listOf(26, 28, 29, 37)) {
            assertEquals(listOf(read, write), permissionsToAsk(AskFor.SaveContact, sdk, none))
        }
    }

    @Test
    fun `the first recording asks for storage on Android 8 and 9 only`() {
        assertEquals(listOf(mic, read, write, storage), permissionsToAsk(AskFor.Record, 26, none))
        assertEquals(listOf(mic, read, write, storage), permissionsToAsk(AskFor.Record, 28, none))
        assertEquals(listOf(mic, read, write), permissionsToAsk(AskFor.Record, 29, none))
        assertEquals(listOf(mic, read, write), permissionsToAsk(AskFor.Record, 37, none))
    }

    @Test
    fun `what's already allowed isn't asked again`() {
        assertEquals(listOf(storage), permissionsToAsk(AskFor.Record, 28) { it != storage })
        assertEquals(listOf(write), permissionsToAsk(AskFor.Record, 33) { it != write })
        assertEquals(emptyList<String>(), permissionsToAsk(AskFor.Record, 33) { true })
    }
}
