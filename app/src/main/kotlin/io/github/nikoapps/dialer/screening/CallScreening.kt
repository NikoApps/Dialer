package io.github.nikoapps.dialer.screening

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService

/** Whether this phone lets Dialer silence calls, as the helper sees it. */
enum class ScreeningSupport {
    Yes,

    /** Android 8 or 9: only the phone app can screen calls. */
    NeedsAndroid10,

    /** Android 10 or newer, but this phone doesn't offer the call screening role, such as some tablets. */
    NotOnThisPhone,
}

/** The call screening role, which lets Dialer silence calls from unsaved numbers. It's granted in a system dialog, not a permission. */
object CallScreening {

    fun support(context: Context): ScreeningSupport = when {
        Build.VERSION.SDK_INT < SCREENING_MIN_SDK -> ScreeningSupport.NeedsAndroid10
        context.getSystemService<RoleManager>()?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) != true ->
            ScreeningSupport.NotOnThisPhone
        else -> ScreeningSupport.Yes
    }

    fun hasRole(context: Context): Boolean =
        Build.VERSION.SDK_INT >= SCREENING_MIN_SDK &&
            context.getSystemService<RoleManager>()?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

    /** The system dialog that asks to let Dialer screen calls, or null where there's none. */
    fun requestRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roles = context.getSystemService<RoleManager>() ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }

    /**
     * Android 15 can block role dialogs for apps installed from a browser or file manager until
     * "Allow restricted settings" is turned on in app info. The dialog then closes without asking,
     * and Dialer can't tell that apart from a "No", so from Android 15 the steps are shown either way.
     */
    val mayBeRestricted: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
}
