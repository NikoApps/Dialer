package io.github.nikoapps.dialer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nikoapps.commons.health.HealthCheck
import io.github.nikoapps.commons.ui.HealthRows
import io.github.nikoapps.commons.ui.PhoneMakerRow
import io.github.nikoapps.commons.ui.PinRows
import io.github.nikoapps.commons.ui.SecondaryButton
import io.github.nikoapps.commons.ui.SetupFrame
import io.github.nikoapps.commons.ui.SetupStep
import io.github.nikoapps.commons.ui.ValueRow
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.health.DialerHealth
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.placesOnScreen
import io.github.nikoapps.dialer.settings.DialerSettings
import io.github.nikoapps.commons.R as CommonsR

/**
 * First-run setup for the helper: people, recorded names, calls, only ringing for saved contacts,
 * PIN and phone, in the spec's order. The frame with Next, Skip and Back is shared with other Niko
 * apps; these are Dialer's steps, using the same rows as settings.
 * [step] is kept by the caller so it survives a trip to the PIN screen or the people editor.
 */
@Composable
fun SetupScreen(
    step: Int,
    onStep: (Int) -> Unit,
    settings: DialerSettings,
    people: List<Person>,
    callsAllowed: CallsAllowed,
    problems: List<HealthCheck>,
    silencing: SilencingState,
    onTurnOnSilencing: () -> Unit,
    onUpdate: ((DialerSettings) -> DialerSettings) -> Unit,
    onEditPlace: (Int) -> Unit,
    onAllowCalls: () -> Unit,
    onSetPin: () -> Unit,
    onRemovePin: () -> Unit,
    onFix: (HealthCheck) -> Unit,
    onFinish: () -> Unit,
) {
    val steps = listOf(
        SetupStep(stringResource(R.string.section_people)) {
            PeopleOnScreenRow(settings, onUpdate)
            StepText(stringResource(R.string.setup_people_info))
            PeopleRows(people, settings.peopleOnScreen, onEditPlace)
            MissingPhotos(people, settings.peopleOnScreen, onEditPlace)
        },
        // Skippable like every step: names can be recorded later from each person in Settings.
        SetupStep(stringResource(R.string.section_recorded_names)) {
            StepText(stringResource(R.string.setup_record_info))
            RecordNameRows(people, settings.peopleOnScreen, onEditPlace)
            HealthRows(problems.filter { it in DialerHealth.recordedNameChecks }, onFix)
            StepText(stringResource(R.string.editor_recording_kept))
        },
        SetupStep(stringResource(R.string.section_calls)) {
            StepText(stringResource(R.string.setup_calls_reason))
            CallPermissionRow(callsAllowed, onAllowCalls)
            WaitBeforeCallingRow(settings, onUpdate)
        },
        // Skippable like every step: Next leaves it off.
        SetupStep(stringResource(R.string.setting_silence)) {
            StepText(stringResource(R.string.setup_silence_reason))
            SilencingRows(
                state = silencing,
                lastSilencedAt = null,
                showLast = false,
                onTurnOn = onTurnOnSilencing,
                onTurnOff = { onUpdate { it.copy(onlySavedContacts = false) } },
            )
        },
        SetupStep(stringResource(CommonsR.string.section_pin)) {
            PinRows(settings.helperPinHash != null, stringResource(R.string.info_pin), onSetPin, onRemovePin)
        },
        SetupStep(stringResource(R.string.section_phone)) {
            // Calls, silencing and recorded names have their own steps, so only the other phone settings are listed here.
            val phoneProblems = problems.filterNot {
                it === DialerHealth.CallPermission || it === DialerHealth.Silencing || it in DialerHealth.recordedNameChecks
            }
            if (phoneProblems.isEmpty()) StepText(stringResource(R.string.setup_phone_ok)) else HealthRows(phoneProblems)
            PhoneMakerRow(stringResource(R.string.info_phone_maker))
            CallingSimRow()
        },
    )
    // The saved step is only a number. If an update changed the steps, it may point past the end,
    // so it is kept in range here, before Back counts down from it.
    SetupFrame(steps = steps, step = step.coerceIn(0, steps.lastIndex), onStep = onStep, onFinish = onFinish)
}

/** Asks for a photo of each person on screen who has none, with a button that opens their editor. */
@Composable
private fun MissingPhotos(people: List<Person>, peopleOnScreen: Int, onEditPlace: (Int) -> Unit) {
    val missing = placesOnScreen(people, peopleOnScreen).filterNotNull().filterNot { it.hasPhoto }
    if (missing.isEmpty()) return
    StepText(stringResource(R.string.setup_photos_info))
    missing.forEach { person ->
        SecondaryButton(
            text = stringResource(R.string.setup_add_photo, person.name),
            onClick = { onEditPlace(person.place) },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

/** One row per person on screen, saying whether their name is recorded, each opening their editor. */
@Composable
private fun RecordNameRows(people: List<Person>, peopleOnScreen: Int, onEditPlace: (Int) -> Unit) {
    val onScreen = placesOnScreen(people, peopleOnScreen).filterNotNull()
    if (onScreen.isEmpty()) {
        StepText(stringResource(R.string.setup_record_no_people))
        return
    }
    val chevron = painterResource(CommonsR.drawable.ic_chevron_right)
    onScreen.forEach { person ->
        ValueRow(
            title = person.name,
            value = stringResource(if (person.hasRecording) R.string.setup_recorded else R.string.setup_not_recorded),
            trailing = chevron,
            onClick = { onEditPlace(person.place) },
        )
    }
}

@Composable
private fun StepText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
    )
}
