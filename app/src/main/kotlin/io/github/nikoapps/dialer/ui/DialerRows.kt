package io.github.nikoapps.dialer.ui

import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.nikoapps.commons.ui.ChoiceRow
import io.github.nikoapps.commons.ui.languageName
import io.github.nikoapps.commons.ui.ValueRow
import io.github.nikoapps.commons.ui.openPage
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.PlacePosition
import io.github.nikoapps.dialer.people.placePosition
import io.github.nikoapps.dialer.people.placesOnScreen
import io.github.nikoapps.dialer.settings.DialerSettings
import io.github.nikoapps.dialer.settings.PEOPLE_ON_SCREEN_CHOICES
import io.github.nikoapps.dialer.settings.ThemeChoice
import io.github.nikoapps.dialer.settings.WaitBeforeCalling
import io.github.nikoapps.commons.R as CommonsR

// Dialer's rows shared by the settings screen and first-run setup, so both always look and behave the same.
// Rows every Niko app has, such as voice and PIN, are in Commons.

/** Whether Dialer may place calls, as the helper sees it. */
enum class CallsAllowed { Yes, NotYet, TurnedOff }

/** One row per place on screen, in grid order: who is there, or Empty. Each opens the people editor for that place. */
@Composable
fun PeopleRows(people: List<Person>, peopleOnScreen: Int, onEditPlace: (Int) -> Unit) {
    val chevron = painterResource(CommonsR.drawable.ic_chevron_right)
    placesOnScreen(people, peopleOnScreen).forEachIndexed { place, person ->
        ValueRow(
            title = stringResource(placeLabel(placePosition(place, peopleOnScreen))),
            value = person?.name ?: stringResource(R.string.place_empty),
            trailing = chevron,
            onClick = { onEditPlace(place) },
        )
    }
}

@Composable
fun PeopleOnScreenRow(settings: DialerSettings, onUpdate: ((DialerSettings) -> DialerSettings) -> Unit) {
    ChoiceRow(
        title = stringResource(R.string.setting_people_on_screen),
        info = stringResource(R.string.info_people_on_screen),
        options = PEOPLE_ON_SCREEN_CHOICES,
        selected = settings.peopleOnScreen,
        optionLabel = { pluralStringResource(R.plurals.people_count, it, it) },
        onSelect = { count -> onUpdate { it.copy(peopleOnScreen = count) } },
        cancelLabel = stringResource(CommonsR.string.cancel),
    )
}

@Composable
fun WaitBeforeCallingRow(settings: DialerSettings, onUpdate: ((DialerSettings) -> DialerSettings) -> Unit) {
    ChoiceRow(
        title = stringResource(R.string.setting_wait),
        info = stringResource(R.string.info_wait),
        options = WaitBeforeCalling.entries,
        selected = settings.waitBeforeCalling,
        optionLabel = { stringResource(it.label()) },
        onSelect = { wait -> onUpdate { it.copy(waitBeforeCalling = wait) } },
        cancelLabel = stringResource(CommonsR.string.cancel),
    )
}

@Composable
fun ThemeRow(settings: DialerSettings, onUpdate: ((DialerSettings) -> DialerSettings) -> Unit) {
    ChoiceRow(
        title = stringResource(R.string.setting_theme),
        options = ThemeChoice.entries,
        selected = settings.theme,
        optionLabel = { stringResource(it.label()) },
        onSelect = { theme -> onUpdate { it.copy(theme = theme) } },
        cancelLabel = stringResource(CommonsR.string.cancel),
    )
}

/** Languages the screen text is translated into, the same as Niko Clock. Android still calls Indonesian "in". */
private val SCREEN_LANGUAGES: List<String?> = listOf(null, "en", "ne", "hi", "bn", "ur", "ar", "es", "pt", "fr", "in")

/** The screen language only, separate from the voice language. Android 13+ only. */
@Composable
fun ScreenLanguageRow(selected: String?, onSelect: (String?) -> Unit) {
    ChoiceRow(
        title = stringResource(R.string.setting_screen_language),
        options = SCREEN_LANGUAGES,
        selected = SCREEN_LANGUAGES.firstOrNull { it == selected?.replace("id", "in") },
        optionLabel = { languageName(it) },
        onSelect = onSelect,
        cancelLabel = stringResource(CommonsR.string.cancel),
    )
}

/** Allow calls: asks for the permission, or opens the phone's settings once it won't ask again. */
@Composable
fun CallPermissionRow(allowed: CallsAllowed, onAllow: () -> Unit) {
    ValueRow(
        title = stringResource(R.string.setup_calls_allow),
        value = stringResource(
            when (allowed) {
                CallsAllowed.Yes -> R.string.calls_allowed
                CallsAllowed.NotYet -> R.string.calls_not_allowed
                CallsAllowed.TurnedOff -> R.string.calls_turned_off
            },
        ),
        valueIsWarning = allowed != CallsAllowed.Yes,
        trailing = if (allowed == CallsAllowed.Yes) null else painterResource(CommonsR.drawable.ic_chevron_right),
        onClick = if (allowed == CallsAllowed.Yes) null else onAllow,
    )
}

/**
 * Dialer can't choose a SIM without reading the phone's state, so the helper sets a default
 * calling SIM. Opens the phone's calling accounts page, where Android keeps "Make calls with".
 */
@Composable
fun CallingSimRow() {
    val context = LocalContext.current
    ValueRow(
        title = stringResource(R.string.setup_sim),
        value = stringResource(R.string.setup_sim_value),
        trailing = painterResource(CommonsR.drawable.ic_chevron_right),
        onClick = {
            openPage(context, Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
        },
    )
}

@StringRes
fun placeLabel(position: PlacePosition): Int = when (position) {
    PlacePosition.Only -> R.string.place_only
    PlacePosition.Top -> R.string.place_top
    PlacePosition.Bottom -> R.string.place_bottom
    PlacePosition.TopLeft -> R.string.place_top_left
    PlacePosition.TopRight -> R.string.place_top_right
    PlacePosition.MiddleLeft -> R.string.place_middle_left
    PlacePosition.MiddleRight -> R.string.place_middle_right
    PlacePosition.BottomLeft -> R.string.place_bottom_left
    PlacePosition.BottomRight -> R.string.place_bottom_right
}

@StringRes
fun WaitBeforeCalling.label(): Int = when (this) {
    WaitBeforeCalling.CallNow -> R.string.wait_call_now
    WaitBeforeCalling.Wait3s -> R.string.wait_3s
    WaitBeforeCalling.AskFirst -> R.string.wait_ask
}

@StringRes
fun ThemeChoice.label(): Int = when (this) {
    ThemeChoice.Phone -> R.string.theme_phone
    ThemeChoice.Light -> R.string.theme_light
    ThemeChoice.Dark -> R.string.theme_dark
}
