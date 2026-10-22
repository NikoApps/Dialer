package io.github.nikoapps.dialer.call

import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.settings.WaitBeforeCalling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a call is between the tap on a face and the phone's own call screen. */
sealed interface CallState {
    /** No call waiting: the grid shows. [lastCallFailed] when the last call couldn't start. */
    data class Idle(val lastCallFailed: Boolean = false) : CallState

    /** The name was spoken and the call goes out when the wait ends, unless Stop is pressed. */
    data class Waiting(val person: Person) : CallState

    /** The name was spoken and a big Call button waits to be pressed. */
    data class Asking(val person: Person) : CallState
}

/** Speaks "Calling {name}". */
interface CallSpeech {
    fun sayCalling(person: Person)
    fun stop()
}

/**
 * The calling screen's state machine. Plain Kotlin on a [CoroutineScope], so the wait and Stop are
 * tested with virtual time.
 *
 * Everything runs on one thread (the main thread in the app), so once [stop] returns the pending
 * call can't go out: the wait is cancelled, and the call only starts if the state is still the
 * same [CallState.Waiting].
 */
class CallFlow(
    private val scope: CoroutineScope,
    private val speech: CallSpeech,
    /** Starts the call; returns false if it couldn't. */
    private val placeCall: (Person) -> Boolean,
    private val waitMillis: Long = WAIT_MILLIS,
) {
    private val _state = MutableStateFlow<CallState>(CallState.Idle())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var wait: Job? = null

    /** A face was tapped. A second tap while a call is already waiting is ignored. */
    fun start(person: Person, mode: WaitBeforeCalling) {
        if (_state.value !is CallState.Idle) return
        speech.sayCalling(person)
        when (mode) {
            WaitBeforeCalling.CallNow -> place(person)
            WaitBeforeCalling.Wait3s -> {
                val waiting = CallState.Waiting(person)
                _state.value = waiting
                wait = scope.launch {
                    delay(waitMillis)
                    if (_state.value == waiting) place(person)
                }
            }
            WaitBeforeCalling.AskFirst -> _state.value = CallState.Asking(person)
        }
    }

    /** The Call button on the ask-first screen. */
    fun confirm() {
        val asking = _state.value as? CallState.Asking ?: return
        place(asking.person)
    }

    /** Stop: cancels the waiting call and the speech, and goes back to the grid. */
    fun stop() {
        wait?.cancel()
        wait = null
        speech.stop()
        _state.value = CallState.Idle()
    }

    /** The calling screen went away, such as the screen turning off. A waiting call never goes out unseen. */
    fun onLeft() {
        if (_state.value !is CallState.Idle) stop()
    }

    private fun place(person: Person) {
        wait = null
        val started = placeCall(person)
        _state.value = CallState.Idle(lastCallFailed = !started)
    }

    companion object {
        const val WAIT_MILLIS = 3_000L
    }
}
