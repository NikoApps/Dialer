package io.github.nikoapps.dialer.call

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/** Plain Android calls: `ACTION_CALL` with a `tel:` URI, or `ACTION_DIAL` for emergency numbers. */
object PhoneCalls {

    fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun actionFor(context: Context, number: String): CallAction =
        callActionFor(number) { isEmergencyNumber(it, systemSaysEmergency(context, it)) }

    /** True when calling [number] needs the call permission and Dialer doesn't have it yet. */
    fun needsPermission(context: Context, number: String): Boolean =
        actionFor(context, number) is CallAction.Call && !hasCallPermission(context)

    /** Starts the call. Returns false if it couldn't, such as on a tablet with no phone. */
    fun start(context: Context, action: CallAction): Boolean {
        val intent = when (action) {
            is CallAction.Call -> Intent(Intent.ACTION_CALL, Uri.fromParts("tel", action.number, null))
            is CallAction.Dial -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.number, null))
            CallAction.Nothing -> return false
        }
        // Started from the app context while Dialer is on screen; the phone's call screen takes over.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            // The call permission was taken away between the tap and the call.
            false
        }
    }

    /** The phone's own answer, or null if it can't give one. */
    private fun systemSaysEmergency(context: Context, number: String): Boolean? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Throws when the phone has no telephony service running.
            context.getSystemService<TelephonyManager>()?.isEmergencyNumber(number)
        } else {
            @Suppress("DEPRECATION")
            PhoneNumberUtils.isEmergencyNumber(number)
        }
    }.getOrNull()
}
