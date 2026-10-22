package io.github.nikoapps.dialer.call

import android.app.Application
import android.media.AudioAttributes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nikoapps.commons.speech.Speaker
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.settings.DialerSettings
import io.github.nikoapps.dialer.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private var settings = DialerSettings()

    /** "Calling Mum" asked for before the speech engine was ready. Spoken once it is, if the call is still waiting. */
    private var pendingSpeech: String? = null

    // Someone who just tapped a face wants to hear the name now, so speak on the accessibility
    // stream, which Do Not Disturb never mutes.
    private val speaker = Speaker(
        context = application,
        audioUsage = AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
        onReady = { ready -> viewModelScope.launch { onSpeakerReady(ready) } },
    )

    private val speech = object : CallSpeech {
        override fun sayCalling(person: Person) {
            // One whole sentence per language, with the name as the helper typed it.
            val sentence = application.getString(R.string.calling_name, person.name)
            pendingSpeech = if (speaker.speak(sentence)) null else sentence
        }

        override fun stop() {
            pendingSpeech = null
            speaker.stop()
        }
    }

    private val flow = CallFlow(
        scope = viewModelScope,
        speech = speech,
        placeCall = { person -> PhoneCalls.start(application, PhoneCalls.actionFor(application, person.number)) },
    )

    val state: StateFlow<CallState> = flow.state

    init {
        viewModelScope.launch {
            SettingsRepository(application).settings.collect { settings = it }
        }
    }

    /** A face was tapped and, if needed, the call permission granted. */
    fun onFaceTapped(person: Person) = flow.start(person, settings.waitBeforeCalling)

    fun onCall() = flow.confirm()

    fun onStop() = flow.stop()

    fun onLeftScreen() = flow.onLeft()

    private fun onSpeakerReady(ready: Boolean) {
        if (!ready) return
        // The sentence is in the screen's language, so pick a voice for it.
        speaker.useVoiceFor(getApplication<Application>().resources.configuration.locales[0])
        val sentence = pendingSpeech ?: return
        pendingSpeech = null
        if (state.value !is CallState.Idle) speaker.speak(sentence)
    }

    override fun onCleared() {
        speaker.shutdown()
    }
}
