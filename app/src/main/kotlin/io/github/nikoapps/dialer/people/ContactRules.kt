package io.github.nikoapps.dialer.people

// The contact rules, without Android, so they're tested on the JVM: which contacts live on the
// SIM, and which account on the phone a new contact goes in.

/** The account a raw contact belongs to. Both null is the phone's own local storage on AOSP. */
data class ContactAccount(val name: String?, val type: String?)

/** Where new contacts go when the phone says nothing better: no account, the phone itself. */
val NoAccount = ContactAccount(name = null, type = null)

/**
 * Account types phone makers use for contacts kept on the SIM card. Matched without case.
 * Samsung uses `vnd.sec.contact.sim` and `vnd.sec.contact.sim2`, MediaTek phones (Tecno, Infinix,
 * many others) `SIM Account`, `USIM Account`, `RUIM Account` and `CSIM Account`, and AOSP and
 * Qualcomm builds `com.android.sim` or `com.android.contacts.sim`.
 */
private val knownSimTypes = setOf(
    "vnd.sec.contact.sim",
    "vnd.sec.contact.sim2",
    "sim account",
    "usim account",
    "ruim account",
    "csim account",
    "uim account",
    "com.android.sim",
    "com.android.contacts.sim",
    "com.qualcomm.simcontacts",
)

/**
 * "sim", "usim", "csim", "ruim" or "uim" as a word of its own, so "sim2" and "SIM Account" match but a
 * name such as "simplemobiletools" doesn't.
 */
private val simWord = Regex("(^|[^a-z])(u|c|r)?(sim|uim)(\\d*)($|[^a-z])")

/**
 * True when a contact in [account] is stored on the SIM card, which can't hold a ringtone.
 * [simAccounts] are the SIM accounts the phone itself lists (Android 12 and newer), checked first.
 */
fun isSimAccount(account: ContactAccount, simAccounts: List<ContactAccount> = emptyList()): Boolean {
    if (account.type == null) return false
    if (simAccounts.any { it.type == account.type && (it.name == null || it.name == account.name) }) return true
    val type = account.type.lowercase()
    return type in knownSimTypes || simWord.containsMatchIn(type)
}

/**
 * Local "on this phone" accounts some phone makers use instead of no account: Samsung's
 * `vnd.sec.contact.phone`, Sony's and MediaTek's. Contacts with no account work on these phones
 * too, but Dialer uses the maker's own one when the phone already has it, so a saved person sits
 * with the other contacts saved on the phone. Xiaomi (MIUI and HyperOS) keeps "Phone" contacts
 * under no account, as AOSP does, as far as is known. Check on each test phone.
 */
private val knownLocalTypes = listOf(
    "vnd.sec.contact.phone",
    "com.sonyericsson.localcontacts",
    "local phone account",
)

/**
 * The account a contact Dialer saves goes in: on the phone, never the SIM, and never a sync
 * account such as Google, so it doesn't leave the phone.
 *
 * [platformLocal] is the phone's own answer where Android gives one (Android 15 and newer),
 * already known to be local. Otherwise a known maker's local account already on the phone
 * ([existing]) is used, so its name is right, and otherwise no account.
 */
fun chooseLocalAccount(existing: List<ContactAccount>, platformLocal: ContactAccount? = null): ContactAccount {
    if (platformLocal != null && !isSimAccount(platformLocal)) return platformLocal
    for (type in knownLocalTypes) {
        existing.firstOrNull { it.type?.lowercase() == type && it.name != null }?.let { return it }
    }
    return NoAccount
}
