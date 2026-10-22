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
    private val placingMillis: Long = PLACING_MILLIS,
) {
    private val _state = MutableStateFlow<CallState>(CallState.Idle())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var wait: Job? = null

    /**
     * Set for a moment after a call goes out. The grid is back at once, so without it a double tap
     * on a face would place a second call before the phone's call screen covers Dialer.
     */
    private var placing: Job? = null

    /**
     * A face was tapped. A second tap while a call is already waiting is ignored, and so is any
     * tap just after a call went out, until Dialer goes to the background or [placingMillis] pass.
     */
    fun start(person: Person, mode: WaitBeforeCalling) {
        if (_state.value !is CallState.Idle || placing != null) return
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

    /**
     * Dialer went to the background, such as the screen turning off or the phone's call screen
     * covering it. A waiting call never goes out unseen, and the next tap after a call counts again.
     */
    fun onLeft() {
        placing?.cancel()
        placing = null
        if (_state.value !is CallState.Idle) stop()
    }

    private fun place(person: Person) {
        wait = null
        val started = placeCall(person)
        _state.value = CallState.Idle(lastCallFailed = !started)
        // A call that couldn't start can be tried again straight away.
        if (started) {
            placing?.cancel()
            placing = scope.launch {
                delay(placingMillis)
                placing = null
            }
        }
    }

    companion object {
        const val WAIT_MILLIS = 3_000L

        /** How long taps are ignored after a call goes out, if Dialer stays in front. */
        const val PLACING_MILLIS = 2_000L
    }
}
