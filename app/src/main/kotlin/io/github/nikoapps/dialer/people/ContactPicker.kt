package io.github.nikoapps.dialer.people

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.RawContacts
import androidx.activity.result.contract.ActivityResultContract

/**
 * A name and number picked from the phone's contacts. [photoUris] are the contact's photo, big
 * one first, empty when it has none. They can only be read straight after picking.
 * [link] is the contact, for setting its ringtone, and [account] where the picked number is
 * stored, such as the SIM. Either is null when the contacts app didn't say.
 */
data class PickedContact(
    val name: String,
    val number: String,
    val photoUris: List<String> = emptyList(),
    val link: ContactLink? = null,
    val account: ContactAccount? = null,
)

/** A phone contact, found again by [lookupKey] even when its id changes. */
data class ContactLink(val contactId: Long, val lookupKey: String)

/**
 * The system contact picker, showing phone numbers. The contacts app grants Dialer a one-time
 * read of the number the helper picked, so this needs no contacts permission.
 */
class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent = Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

/**
 * Reads the picked number. Null if it can't be read, such as when the grant was refused. Call off
 * the main thread. The contact and its account come with the same one-time read; if a contacts
 * app won't give them, the name and number are still read on their own.
 */
fun readPickedContact(context: Context, uri: Uri): PickedContact? =
    runCatching { read(context, uri, withLink = true) }.getOrNull() ?: runCatching { read(context, uri, withLink = false) }.getOrNull()

private fun read(context: Context, uri: Uri, withLink: Boolean): PickedContact? {
    val basic = arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_URI, Phone.PHOTO_THUMBNAIL_URI)
    val projection = if (withLink) basic + arrayOf(Phone.CONTACT_ID, Phone.LOOKUP_KEY, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE) else basic
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val name = cursor.getString(0).orEmpty().trim()
        val number = cursor.getString(1).orEmpty().trim()
        val photos = listOfNotNull(cursor.getString(2), cursor.getString(3)).filter { it.isNotBlank() }.distinct()
        val link = if (withLink) {
            cursor.getString(5)?.takeIf { it.isNotBlank() }?.let { ContactLink(cursor.getLong(4), it) }
        } else {
            null
        }
        val account = if (withLink) ContactAccount(name = cursor.getString(6), type = cursor.getString(7)) else null
        PickedContact(name, number, photos, link, account).takeIf { number.isNotEmpty() }
    }
}
