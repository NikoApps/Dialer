package io.github.nikoapps.dialer.health

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.core.content.getSystemService
import io.github.nikoapps.commons.health.HealthCheck
import io.github.nikoapps.commons.health.PhoneHealth
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.call.PhoneCalls
import io.github.nikoapps.dialer.people.DialerPermissions
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.screening.CallScreening
import io.github.nikoapps.dialer.screening.ScreeningSupport
import io.github.nikoapps.dialer.settings.DialerSettings

/**
 * The phone settings that stop Dialer placing calls, silencing calls or playing recorded names.
 * The shared checks and their fixes live in Commons. Voice problems come from the speech engine,
 * not the phone's settings, so they're shown next to these with Commons' voice row rather than
 * as a check here.
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

    /**
     * Contacts aren't allowed while a name is recorded, so its ringtone can't be set or checked.
     * The app asks for contacts itself when this is fixed, and opens app info once the phone won't ask.
     */
    val RecordedNamesContacts = HealthCheck(
        title = R.string.health_recorded_contacts,
        fix = R.string.health_recorded_contacts_value,
        isProblem = { context -> !DialerPermissions.hasContacts(context) },
        fixIntent = PhoneHealth::appInfo,
    )

    /** The ringer is silent, on vibrate or at zero while a name is recorded, so nobody hears it. */
    val RingerOff = HealthCheck(
        title = R.string.health_ringer,
        fix = R.string.health_ringer_value,
        isProblem = { context -> !ringerAudible(context) },
        fixIntent = { Intent(Settings.ACTION_SOUND_SETTINGS) },
    )

    /**
     * A recorded name isn't its contact's ringtone, and the quiet check couldn't put it back, such
     * as when another ringtone was chosen in the contacts app. Fixed by setting it again. Whether
     * it's a problem is decided by the last ringtone check, which reads contacts off the main
     * thread, so it's only listed by [problems] when that check found one.
     */
    val RecordedNameNotSet = HealthCheck(
        title = R.string.health_recorded_not_set,
        fix = R.string.health_recorded_not_set_value,
        isProblem = { true },
        fixIntent = PhoneHealth::appInfo,
    )

    /**
     * What's wrong on this phone now, in the order the hint line and settings show them: calls,
     * silencing, recorded names, then other phone settings. Run again each time Dialer comes to
     * the front. [recordedNameNotSet] is the last ringtone check's answer.
     */
    fun problems(context: Context, settings: DialerSettings, people: List<Person>, recordedNameNotSet: Boolean): List<HealthCheck> {
        val recorded = recordedNameProblems(
            anyRecording = people.any { it.hasRecording },
            contactsAllowed = DialerPermissions.hasContacts(context),
            ringerAudible = ringerAudible(context),
            notSet = recordedNameNotSet,
        ).map {
            when (it) {
                RecordedNameProblem.ContactsPermission -> RecordedNamesContacts
                RecordedNameProblem.RingerOff -> RingerOff
                RecordedNameProblem.NotSet -> RecordedNameNotSet
            }
        }
        val calls = buildList {
            add(CallPermission)
            if (settings.onlySavedContacts) add(Silencing)
        }
        return PhoneHealth.problems(context, calls) + recorded + PhoneHealth.problems(context, listOf(PhoneHealth.Hibernation))
    }

    /** The recorded names checks, for telling them apart from the others. */
    val recordedNameChecks: Set<HealthCheck> get() = setOf(RecordedNamesContacts, RingerOff, RecordedNameNotSet)

    /** True when a call would ring out loud: ringer on normal and ring volume above zero. */
    fun ringerAudible(context: Context): Boolean {
        val audio = context.getSystemService<AudioManager>() ?: return true
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL && audio.getStreamVolume(AudioManager.STREAM_RING) > 0
    }
}
