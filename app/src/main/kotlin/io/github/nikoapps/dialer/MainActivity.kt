package io.github.nikoapps.dialer

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nikoapps.commons.health.PhoneHealth
import io.github.nikoapps.commons.speech.VoiceStatus
import io.github.nikoapps.commons.theme.NikoTheme
import io.github.nikoapps.commons.ui.SetPinScreen
import io.github.nikoapps.commons.ui.UnlockPinScreen
import io.github.nikoapps.commons.ui.openPage
import io.github.nikoapps.dialer.call.CallState
import io.github.nikoapps.dialer.call.CallViewModel
import io.github.nikoapps.dialer.call.PhoneCalls
import io.github.nikoapps.dialer.health.DialerHealth
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.gridShape
import io.github.nikoapps.dialer.people.placesOnScreen
import io.github.nikoapps.dialer.settings.DialerHelperPin
import io.github.nikoapps.dialer.settings.ThemeChoice
import io.github.nikoapps.dialer.ui.CallingScreen
import io.github.nikoapps.dialer.ui.CallsAllowed
import io.github.nikoapps.dialer.ui.Hint
import io.github.nikoapps.dialer.ui.MainScreen
import io.github.nikoapps.dialer.ui.PersonEditorScreen
import io.github.nikoapps.dialer.ui.SettingsScreen
import io.github.nikoapps.dialer.ui.SettingsViewModel
import io.github.nikoapps.dialer.ui.SetupScreen
import io.github.nikoapps.dialer.ui.hintFor
import io.github.nikoapps.dialer.ui.hintText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings?.theme) {
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
                ThemeChoice.Phone, null -> isSystemInDarkTheme()
            }
            NikoTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(settingsViewModel)
                }
            }
        }
    }
}

/**
 * The screens. Settings is reached through Unlock once a PIN is set; the PIN screen and the
 * people editor open from settings or setup and go back to where they came from.
 *
 * The calling screen isn't one of them: it shows over [Screen.Main] whenever [CallViewModel] has a
 * call waiting, so Stop and a placed call always land back on the grid.
 */
private enum class Screen { Main, Unlock, Settings, SetPin, Setup, EditPerson }

@Composable
private fun App(settingsViewModel: SettingsViewModel, callViewModel: CallViewModel = viewModel()) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val people by settingsViewModel.people.collectAsStateWithLifecycle()
    val call by callViewModel.state.collectAsStateWithLifecycle()
    val voice by callViewModel.voice.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    var setupStep by rememberSaveable { mutableIntStateOf(0) }
    var pinReturn by rememberSaveable { mutableStateOf(Screen.Settings) }
    var editorReturn by rememberSaveable { mutableStateOf(Screen.Settings) }
    var editPlace by rememberSaveable { mutableIntStateOf(0) }
    // The call permission was refused and the phone won't ask again. Kept here so it survives
    // a visit to the calling screen or settings.
    var callsBlocked by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = LocalActivity.current

    // Health checks run again each time Dialer comes back to the front, such as after fixing a
    // setting in the phone's settings, and after the permission question closes.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var healthCheckRun by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { callViewModel.onResume() }

    // Asked by a helper from settings or setup. Once the phone won't ask again, app info is the
    // only place to allow calls.
    val askForCalls = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        callsBlocked = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CALL_PHONE)
        healthCheckRun++
    }
    val allowCalls = {
        if (callsBlocked) {
            openPage(context, PhoneHealth.appInfo(context))
        } else {
            askForCalls.launch(Manifest.permission.CALL_PHONE)
        }
    }

    BackHandler(enabled = screen != Screen.Main) {
        screen = when (screen) {
            Screen.SetPin -> pinReturn
            Screen.EditPerson -> editorReturn
            else -> Screen.Main
        }
    }

    val current = settings ?: return
    val everyone = people ?: return
    val problems = remember(lifecycle, healthCheckRun) { DialerHealth.problems(context) }
    val canCall = DialerHealth.CallPermission !in problems

    // First run is for the helper. A phone that already has a PIN is past that, and skipping the
    // PIN for setup would let anyone into settings.
    LaunchedEffect(Unit) {
        if (!current.setupDone && current.helperPinHash == null && screen == Screen.Main) screen = Screen.Setup
    }
    val finishSetup = {
        settingsViewModel.update { it.copy(setupDone = true) }
        setupStep = 0
        screen = Screen.Main
    }
    val editPerson = { place: Int, from: Screen ->
        editPlace = place
        editorReturn = from
        screen = Screen.EditPerson
    }
    val removePin = { settingsViewModel.update { it.copy(helperPinHash = null) } }

    when (screen) {
        Screen.Main -> when (val state = call) {
            is CallState.Waiting -> CallingStep(state.person, askFirst = false, callViewModel)
            is CallState.Asking -> CallingStep(state.person, askFirst = true, callViewModel)
            is CallState.Idle -> {
                val places = remember(everyone, current.peopleOnScreen) { placesOnScreen(everyone, current.peopleOnScreen) }
                GridStep(
                    places = places,
                    columns = gridShape(current.peopleOnScreen).columns,
                    hint = hintFor(
                        hasPeople = places.any { it != null },
                        canCall = canCall,
                        callsBlocked = callsBlocked,
                        lastCallFailed = state.lastCallFailed,
                        voice = voice.voice,
                        needsAttention = problems.isNotEmpty(),
                    ),
                    voice = voice.voice,
                    onCallsBlocked = { blocked ->
                        callsBlocked = blocked
                        healthCheckRun++
                    },
                    callViewModel = callViewModel,
                    onOpenSettings = { screen = if (current.helperPinHash == null) Screen.Settings else Screen.Unlock },
                )
            }
        }
        Screen.Unlock -> UnlockPinScreen(
            helperPin = DialerHelperPin,
            pinHash = current.helperPinHash ?: "",
            onUnlocked = { screen = Screen.Settings },
            onCancel = { screen = Screen.Main },
        )
        Screen.Settings -> SettingsScreen(
            settings = current,
            people = everyone,
            voice = voice,
            problems = problems,
            onUpdate = settingsViewModel::update,
            onFix = { check ->
                if (check === DialerHealth.CallPermission) {
                    allowCalls()
                } else {
                    openPage(context, check.fixIntent(context), PhoneHealth.appInfo(context))
                }
            },
            onEditPlace = { place -> editPerson(place, Screen.Settings) },
            onTestVoice = callViewModel::onTestVoice,
            onSetPin = { pinReturn = Screen.Settings; screen = Screen.SetPin },
            onRemovePin = removePin,
            onStartSetup = { setupStep = 0; screen = Screen.Setup },
            onBack = { screen = Screen.Main },
        )
        Screen.Setup -> SetupScreen(
            step = setupStep,
            onStep = { setupStep = it },
            settings = current,
            people = everyone,
            callsAllowed = when {
                canCall -> CallsAllowed.Yes
                callsBlocked -> CallsAllowed.TurnedOff
                else -> CallsAllowed.NotYet
            },
            problems = problems,
            onUpdate = settingsViewModel::update,
            onEditPlace = { place -> editPerson(place, Screen.Setup) },
            onAllowCalls = allowCalls,
            onSetPin = { pinReturn = Screen.Setup; screen = Screen.SetPin },
            onRemovePin = removePin,
            onFinish = finishSetup,
        )
        Screen.SetPin -> SetPinScreen(
            onPinSet = { pin ->
                settingsViewModel.update { it.copy(helperPinHash = DialerHelperPin.hash(pin)) }
                screen = pinReturn
            },
            onCancel = { screen = pinReturn },
        )
        Screen.EditPerson -> PersonEditorScreen(
            people = everyone,
            place = editPlace,
            peopleOnScreen = current.peopleOnScreen,
            onSave = { id, name, number, place, photo ->
                settingsViewModel.savePerson(id, name, number, place, photo)
                screen = editorReturn
            },
            onRemove = { person ->
                settingsViewModel.removePerson(person)
                screen = editorReturn
            },
            onBack = { screen = editorReturn },
        )
    }
}

/** The grid, plus asking for the call permission the first time a face is tapped. */
@Composable
private fun GridStep(
    places: List<Person?>,
    columns: Int,
    hint: Hint,
    voice: VoiceStatus,
    onCallsBlocked: (Boolean) -> Unit,
    callViewModel: CallViewModel,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // The face tapped while the permission question is open; its call starts once calls are allowed.
    var waitingForPermission by rememberSaveable { mutableStateOf<String?>(null) }

    val askToCall = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val person = places.firstOrNull { it?.id == waitingForPermission }
        waitingForPermission = null
        if (granted) {
            onCallsBlocked(false)
            if (person != null) callViewModel.onFaceTapped(person)
        } else {
            onCallsBlocked(
                activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CALL_PHONE),
            )
        }
    }

    MainScreen(
        places = places,
        columns = columns,
        hint = hintText(hint, voice),
        onCall = { person ->
            if (PhoneCalls.needsPermission(context, person.number)) {
                waitingForPermission = person.id
                askToCall.launch(Manifest.permission.CALL_PHONE)
            } else {
                callViewModel.onFaceTapped(person)
            }
        },
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun CallingStep(person: Person, askFirst: Boolean, callViewModel: CallViewModel) {
    // Back is the same as Stop.
    BackHandler(onBack = callViewModel::onStop)
    // A waiting call never goes out while Dialer is off the screen, such as when the screen turns off.
    // Turning the phone round also stops the activity, but the call keeps waiting through that.
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) callViewModel.onLeftScreen()
    }
    CallingScreen(person = person, askFirst = askFirst, onCall = callViewModel::onCall, onStop = callViewModel::onStop)
}
