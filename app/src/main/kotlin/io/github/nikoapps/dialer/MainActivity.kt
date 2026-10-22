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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.github.nikoapps.commons.theme.NikoTheme
import io.github.nikoapps.dialer.call.CallState
import io.github.nikoapps.dialer.call.CallViewModel
import io.github.nikoapps.dialer.call.PhoneCalls
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.gridShape
import io.github.nikoapps.dialer.people.placesOnScreen
import io.github.nikoapps.dialer.ui.CallingScreen
import io.github.nikoapps.dialer.ui.MainScreen
import io.github.nikoapps.dialer.ui.SettingsScreen
import io.github.nikoapps.dialer.ui.SettingsViewModel
import io.github.nikoapps.dialer.ui.hintFor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NikoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

/**
 * The screens. Helper screens (PIN unlock, set PIN, first-run setup, people editor) slot in here
 * as more entries, with Settings reached through Unlock once a PIN is set.
 *
 * The calling screen isn't one of them: it shows over [Screen.Main] whenever [CallViewModel] has a
 * call waiting, so Stop and a placed call always land back on the grid.
 */
private enum class Screen { Main, Settings }

@Composable
private fun App(settingsViewModel: SettingsViewModel = viewModel(), callViewModel: CallViewModel = viewModel()) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val people by settingsViewModel.people.collectAsStateWithLifecycle()
    val call by callViewModel.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    // The call permission was refused and the phone won't ask again. Kept here so it survives
    // a visit to the calling screen or settings.
    var callsBlocked by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = screen != Screen.Main) { screen = Screen.Main }

    val current = settings ?: return
    val everyone = people ?: return

    when (screen) {
        Screen.Main -> when (val state = call) {
            is CallState.Waiting -> CallingStep(state.person, askFirst = false, callViewModel)
            is CallState.Asking -> CallingStep(state.person, askFirst = true, callViewModel)
            is CallState.Idle -> {
                val places = remember(everyone, current.peopleOnScreen) { placesOnScreen(everyone, current.peopleOnScreen) }
                GridStep(
                    places = places,
                    columns = gridShape(current.peopleOnScreen).columns,
                    lastCallFailed = state.lastCallFailed,
                    callsBlocked = callsBlocked,
                    onCallsBlocked = { callsBlocked = it },
                    callViewModel = callViewModel,
                    onOpenSettings = { screen = Screen.Settings },
                )
            }
        }
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Main })
    }
}

/** The grid, plus asking for the call permission the first time a face is tapped. */
@Composable
private fun GridStep(
    places: List<Person?>,
    columns: Int,
    lastCallFailed: Boolean,
    callsBlocked: Boolean,
    onCallsBlocked: (Boolean) -> Unit,
    callViewModel: CallViewModel,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // Checked again each time the app comes back, such as after allowing calls in the phone's settings.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val canCall = remember(lifecycle) { PhoneCalls.hasCallPermission(context) }
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

    val hint = hintFor(
        hasPeople = places.any { it != null },
        canCall = canCall,
        callsBlocked = callsBlocked,
        lastCallFailed = lastCallFailed,
    )

    MainScreen(
        places = places,
        columns = columns,
        hint = hint.text,
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
