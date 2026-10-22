package io.github.nikoapps.dialer.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nikoapps.commons.health.PhoneHealth
import io.github.nikoapps.commons.ui.SwitchRow
import io.github.nikoapps.commons.ui.ValueRow
import io.github.nikoapps.commons.ui.openPage
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.screening.CallScreening
import io.github.nikoapps.dialer.screening.LastSilenced
import io.github.nikoapps.dialer.screening.ScreeningSupport
import io.github.nikoapps.dialer.screening.lastSilenced
import java.time.Instant
import java.time.ZoneId
import io.github.nikoapps.commons.R as CommonsR

/**
 * "Only ring for saved contacts" as the helper sees it.
 *
 * @param on the setting as stored. A lost role shows in Needs attention, not here.
 * @param refused the last request for the call screening role came back without it.
 */
data class SilencingState(
    val support: ScreeningSupport,
    val on: Boolean,
    val refused: Boolean,
)

/**
 * The switch, and while it's on, "Last silenced call". Turning it on asks for the call screening
 * role first ([onTurnOn]); turning it off just stops silencing. [lastSilencedAt] null with
 * [showLast] false leaves out the last silenced row, as in setup.
 */
@Composable
fun SilencingRows(
    state: SilencingState,
    lastSilencedAt: Instant?,
    showLast: Boolean,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
) {
    val title = stringResource(R.string.setting_silence)
    val info = stringResource(R.string.info_silence)
    when (state.support) {
        // Shown but off, so a helper isn't left wondering where it went.
        ScreeningSupport.NeedsAndroid10 -> ValueRow(title, stringResource(R.string.silence_needs_android_10), onClick = null, info = info)
        ScreeningSupport.NotOnThisPhone -> ValueRow(title, stringResource(R.string.silence_not_on_this_phone), onClick = null, info = info)
        ScreeningSupport.Yes -> {
            SwitchRow(
                title = title,
                checked = state.on,
                onCheckedChange = { turnOn -> if (turnOn) onTurnOn() else onTurnOff() },
                value = stringResource(if (state.refused && !state.on) R.string.silence_not_allowed else R.string.silence_value),
                info = info,
            )
            if (state.refused && !state.on && CallScreening.mayBeRestricted) RestrictedSettingsSteps()
            if (state.on && showLast) LastSilencedRow(lastSilencedAt)
        }
    }
}

/** Android 15 can block the role dialog for an app installed from a browser until restricted settings are allowed. */
@Composable
private fun RestrictedSettingsSteps() {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.silence_restricted_steps),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
    )
    ValueRow(
        title = stringResource(R.string.open_app_info),
        value = stringResource(R.string.open_app_info_value),
        trailing = painterResource(CommonsR.drawable.ic_chevron_right),
        onClick = { openPage(context, PhoneHealth.appInfo(context)) },
    )
}

@Composable
private fun LastSilencedRow(at: Instant?) {
    ValueRow(
        title = stringResource(R.string.last_silenced),
        value = lastSilencedText(at),
        onClick = null,
        info = stringResource(R.string.info_last_silenced),
    )
}

/** "None yet", "Today, 2:00 PM", "Yesterday, 9:05 AM" or the date, in the phone's time format. */
@Composable
private fun lastSilencedText(at: Instant?): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    val last = remember(at, locale, is24Hour) {
        lastSilenced(at, Instant.now(), ZoneId.systemDefault(), is24Hour, locale)
    }
    return when (last) {
        LastSilenced.None -> stringResource(R.string.last_silenced_none)
        is LastSilenced.Today -> stringResource(R.string.last_silenced_today, last.time)
        is LastSilenced.Yesterday -> stringResource(R.string.last_silenced_yesterday, last.time)
        is LastSilenced.Earlier -> stringResource(R.string.last_silenced_earlier, last.date, last.time)
    }
}
