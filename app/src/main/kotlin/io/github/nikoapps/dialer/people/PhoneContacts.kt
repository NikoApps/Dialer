package io.github.nikoapps.dialer.people

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.PhoneLookup
import android.provider.ContactsContract.RawContacts

/**
 * The phone's contacts, as far as Dialer needs them: finding a person's contact again, saving a
 * typed person on the phone, and setting a contact's ringtone. Everything here needs the contacts
 * permission and reads or writes the contacts database, so call it off the main thread. Each
 * function returns null or false rather than throwing when contacts can't be read.
 */
object PhoneContacts {

    /** A contact as it is now: its current link, and its ringtone (null for the phone's default). */
    data class Found(val link: ContactLink, val ringtone: String?)

    /** Finds the contact again. Null when it was deleted, or contacts can't be read. */
    fun find(context: Context, link: ContactLink): Found? = runCatching {
        val uri = if (link.contactId > 0) {
            Contacts.getLookupUri(link.contactId, link.lookupKey)
        } else {
            Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, Uri.encode(link.lookupKey))
        }
        context.contentResolver.query(uri, arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.CUSTOM_RINGTONE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            Found(ContactLink(c.getLong(0), c.getString(1) ?: link.lookupKey), c.getString(2)?.takeIf { it.isNotBlank() })
        }
    }.getOrNull()

    /**
     * The saved contact with this number, such as one picked before Dialer kept the link. When
     * several contacts have it, only the one named [name] counts. See [contactForNumber].
     */
    fun findByNumber(context: Context, number: String, name: String?): ContactByNumber = runCatching {
        if (number.isBlank()) return@runCatching ContactByNumber.None
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val matches = context.contentResolver.query(
            uri,
            arrayOf(PhoneLookup._ID, PhoneLookup.LOOKUP_KEY, PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val key = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    add(NumberMatch(ContactLink(c.getLong(0), key), c.getString(2)))
                }
            }
        }.orEmpty()
        contactForNumber(matches, name)
    }.getOrDefault(ContactByNumber.None)

    /**
     * Saves [name] and [number] as a new contact on the phone, never on the SIM, so a ringtone can
     * be set on it and silencing never catches its calls. Returns the new contact, or null if it
     * couldn't be saved.
     */
    fun saveOnPhone(context: Context, name: String, number: String): ContactLink? = runCatching {
        val resolver = context.contentResolver
        val account = localAccount(context)
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                .withValue(RawContacts.ACCOUNT_NAME, account.name)
                .build(),
            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, name)
                .build(),
            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, number)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build(),
        )
        val raw = resolver.applyBatch(ContactsContract.AUTHORITY, ops).firstOrNull()?.uri ?: return@runCatching null
        val lookup = RawContacts.getContactLookupUri(resolver, raw) ?: return@runCatching null
        resolver.query(lookup, arrayOf(Contacts._ID, Contacts.LOOKUP_KEY), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            c.getString(1)?.let { ContactLink(c.getLong(0), it) }
        }
    }.getOrNull()

    /** Sets the contact's ringtone to [ringtone], or back to the phone's default when it's null. */
    fun setRingtone(context: Context, link: ContactLink, ringtone: String?): Boolean = runCatching {
        val found = find(context, link) ?: return@runCatching false
        val values = ContentValues().apply { put(Contacts.CUSTOM_RINGTONE, ringtone) }
        val uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, found.link.contactId)
        context.contentResolver.update(uri, values, null, null) > 0
    }.getOrDefault(false)

    /** True when the picked number is stored on the SIM card, which can't hold a ringtone. */
    fun isOnSim(context: Context, account: ContactAccount?): Boolean =
        account != null && isSimAccount(account, simAccounts(context))

    /** The SIM accounts the phone lists itself, from Android 12. */
    private fun simAccounts(context: Context): List<ContactAccount> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        return runCatching {
            ContactsContract.SimContacts.getSimAccounts(context.contentResolver).map { ContactAccount(it.accountName, it.accountType) }
        }.getOrDefault(emptyList())
    }

    /**
     * The phone's own storage for new contacts. Android 15 and newer says which account that is.
     * Before that, a maker's known local account already in use on the phone is chosen, and
     * otherwise no account, which is the phone's own storage on AOSP. See [chooseLocalAccount].
     */
    private fun localAccount(context: Context): ContactAccount {
        val platform = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            runCatching {
                ContactAccount(RawContacts.getLocalAccountName(context), RawContacts.getLocalAccountType(context))
            }.getOrNull()
        } else {
            null
        }
        return chooseLocalAccount(existingAccounts(context), platform)
    }

    private fun existingAccounts(context: Context): List<ContactAccount> = runCatching {
        val accounts = LinkedHashSet<ContactAccount>()
        context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.ACCOUNT_TYPE} IS NOT NULL AND ${RawContacts.DELETED} = 0",
            null,
            null,
        )?.use { c -> while (c.moveToNext()) accounts += ContactAccount(c.getString(0), c.getString(1)) }
        accounts.toList()
    }.getOrDefault(emptyList())
}
