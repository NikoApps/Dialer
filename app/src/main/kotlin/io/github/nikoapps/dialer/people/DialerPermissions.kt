package io.github.nikoapps.dialer.people

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The permissions Dialer asks for besides calls, each where it's needed. Contacts is one dialog
 * on Android, covering reading and writing.
 */
object DialerPermissions {
    val contacts: List<String> = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    const val MICROPHONE: String = Manifest.permission.RECORD_AUDIO

    /** Only Android 8 and 9 need it to put a file in the shared Ringtones folder. Declared with maxSdkVersion 28. */
    const val STORAGE: String = Manifest.permission.WRITE_EXTERNAL_STORAGE
    const val STORAGE_MAX_SDK: Int = Build.VERSION_CODES.P

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasContacts(context: Context): Boolean = contacts.all { granted(context, it) }

    fun hasMicrophone(context: Context): Boolean = granted(context, MICROPHONE)

    /** True where the shared Ringtones folder can be written, which is always from Android 10. */
    fun hasStorage(context: Context): Boolean =
        Build.VERSION.SDK_INT > STORAGE_MAX_SDK || granted(context, STORAGE)

    /** What to ask for now on this phone, for [purpose]. */
    fun toAsk(context: Context, purpose: AskFor): List<String> =
        permissionsToAsk(purpose, Build.VERSION.SDK_INT) { granted(context, it) }
}

/** Why Dialer is asking. */
enum class AskFor {
    /** Saving a typed person in the phone's contacts, or copying a SIM contact to the phone. */
    SaveContact,

    /** The first recording: the microphone, contacts to set the ringtone, and storage on Android 8 and 9. */
    Record,
}

/**
 * The permissions to ask for [purpose] on Android [sdk], leaving out those already [granted], in
 * the order the phone shows them. Storage is only ever asked on Android 8 and 9, the only versions
 * the manifest declares it for.
 */
fun permissionsToAsk(purpose: AskFor, sdk: Int, granted: (String) -> Boolean): List<String> = buildList {
    if (purpose == AskFor.Record) add(DialerPermissions.MICROPHONE)
    addAll(DialerPermissions.contacts)
    if (purpose == AskFor.Record && sdk <= DialerPermissions.STORAGE_MAX_SDK) add(DialerPermissions.STORAGE)
}.filterNot(granted)
