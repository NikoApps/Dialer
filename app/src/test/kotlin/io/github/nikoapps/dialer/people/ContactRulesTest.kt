package io.github.nikoapps.dialer.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRulesTest {

    private fun type(type: String?) = ContactAccount(name = "x", type = type)

    @Test
    fun `phone makers' SIM accounts are SIM`() {
        for (t in listOf("vnd.sec.contact.sim", "vnd.sec.contact.sim2", "SIM Account", "USIM Account", "RUIM Account", "CSIM Account", "com.android.sim", "com.android.contacts.sim", "com.huawei.sim")) {
            assertTrue(t, isSimAccount(type(t)))
        }
    }

    @Test
    fun `phone, Google and other accounts aren't SIM`() {
        for (t in listOf(null, "vnd.sec.contact.phone", "com.google", "org.fossify.contacts", "com.simplemobiletools.contacts.pro", "com.whatsapp", "com.xiaomi", "Local Phone Account")) {
            assertFalse(t.toString(), isSimAccount(type(t)))
        }
    }

    @Test
    fun `an account the phone lists as a SIM is SIM, whatever its name`() {
        val sim = ContactAccount(name = "Card 1", type = "com.maker.cards")
        assertTrue(isSimAccount(sim, simAccounts = listOf(sim)))
        assertFalse(isSimAccount(sim))
        assertFalse(isSimAccount(sim.copy(name = "Card 2"), simAccounts = listOf(sim)))
    }

    @Test
    fun `new contacts go in no account on a plain phone`() {
        assertEquals(NoAccount, chooseLocalAccount(existing = emptyList()))
        assertEquals(NoAccount, chooseLocalAccount(existing = listOf(ContactAccount("me@example.org", "com.google"))))
    }

    @Test
    fun `new contacts go in Samsung's phone account when the phone has it`() {
        val samsung = ContactAccount("vnd.sec.contact.phone", "vnd.sec.contact.phone")
        val google = ContactAccount("me@example.org", "com.google")
        assertEquals(samsung, chooseLocalAccount(existing = listOf(google, samsung)))
    }

    @Test
    fun `the phone's own local account wins, but never a SIM`() {
        val local = ContactAccount("Phone", "com.maker.local")
        val samsung = ContactAccount("vnd.sec.contact.phone", "vnd.sec.contact.phone")
        assertEquals(local, chooseLocalAccount(existing = listOf(samsung), platformLocal = local))
        assertEquals(samsung, chooseLocalAccount(existing = listOf(samsung), platformLocal = ContactAccount("SIM", "vnd.sec.contact.sim")))
        assertEquals(NoAccount, chooseLocalAccount(existing = emptyList(), platformLocal = NoAccount))
        // Android saying "no account" doesn't override a maker's own local account.
        assertEquals(samsung, chooseLocalAccount(existing = listOf(samsung), platformLocal = NoAccount))
    }

    @Test
    fun `a SIM account is never chosen for new contacts`() {
        val sim = ContactAccount("SIM1", "vnd.sec.contact.sim")
        assertEquals(NoAccount, chooseLocalAccount(existing = listOf(sim)))
    }
}
