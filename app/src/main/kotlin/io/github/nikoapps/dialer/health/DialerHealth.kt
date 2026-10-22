package io.github.nikoapps.dialer.health

import android.content.Context
import io.github.nikoapps.commons.health.HealthCheck
import io.github.nikoapps.commons.health.PhoneHealth
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.call.PhoneCalls
import io.github.nikoapps.dialer.screening.CallScreening
import io.github.nikoapps.dialer.screening.ScreeningSupport
import io.github.nikoapps.dialer.settings.DialerSettings

/**
 * The phone settings that stop Dialer placing calls. The shared checks and their fixes live in
 * Commons. Voice problems come from the speech engine, not the phone's settings, so they're
 * shown next to these with Commons' voice row rather than as a check here.
 */
object DialerHealth {

    /**
     * Calls aren't allowed. The app asks for the permission itself when this is fixed from
     * settings or setup, and opens app info only once the phone won't ask again.
     */
    val CallPermission = HealthCheck(
        title = R.string.health_calls,
        fix = R.string.health_calls_value,
        isProblem = { context -> !PhoneCalls.hasCallPermission(context) },
        fixIntent = PhoneHealth::appInfo,
    )

    /**
     * "Only ring for saved contacts" is on, but Dialer no longer holds the call screening role,
     * so calls from unsaved numbers ring again. The app asks for the role again when this is fixed.
     * Only checked while the setting is on.
     */
    val Silencing = HealthCheck(
        title = R.string.health_silencing,
        fix = R.string.health_silencing_value,
        isProblem = { context ->
            CallScreening.support(context) == ScreeningSupport.Yes && !CallScreening.hasRole(context)
        },
        fixIntent = PhoneHealth::appInfo,
    )

    /** Checked in this order, so the hint line and settings list them the same way. */
    private fun checks(settings: DialerSettings): List<HealthCheck> = buildList {
        add(CallPermission)
        if (settings.onlySavedContacts) add(Silencing)
        add(PhoneHealth.Hibernation)
    }

    /** What's wrong on this phone now. Run again each time Dialer comes to the front. */
    fun problems(context: Context, settings: DialerSettings): List<HealthCheck> =
        PhoneHealth.problems(context, checks(settings))
}
