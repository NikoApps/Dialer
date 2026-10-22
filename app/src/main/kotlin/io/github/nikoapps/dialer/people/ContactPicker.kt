package io.github.nikoapps.dialer.people

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.result.contract.ActivityResultContract

/** A name and number picked from the phone's contacts. */
data class PickedContact(val name: String, val number: String)

/**
 * The system contact picker, showing phone numbers. The contacts app grants Dialer a one-time
 * read of the number the helper picked, so this needs no contacts permission.
 */
class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent = Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

/** Reads the picked number. Null if it can't be read, such as when the grant was refused. Call off the main thread. */
fun readPickedContact(context: Context, uri: Uri): PickedContact? = runCatching {
    context.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val name = cursor.getString(0).orEmpty().trim()
        val number = cursor.getString(1).orEmpty().trim()
        PickedContact(name, number).takeIf { number.isNotEmpty() }
    }
}.getOrNull()
