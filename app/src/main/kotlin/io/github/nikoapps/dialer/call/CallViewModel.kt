package io.github.nikoapps.dialer.call

import android.app.Application
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Resources
import android.media.AudioAttributes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nikoapps.commons.speech.Speaker
import io.github.nikoapps.commons.speech.VoiceStatus
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.settings.DialerSettings
import io.github.nikoapps.dialer.settings.SettingsRepository
import io.github.nikoapps.dialer.speech.voiceLocaleFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** [engine] is the speech engine's name, shown to helpers so they know which voice is in use. */
data class VoiceState(val voice: VoiceStatus = VoiceStatus.Starting, val engine: String? = null)

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private var settings = DialerSettings()

    /** False until the stored settings are read, so a tap never uses the default wait instead of the helper's choice. */
    private var settingsLoaded = false

    /**
     * How each tapped number is called, by number. Asking the phone whether a number is an
     * emergency number can be slow, so it's worked out off the main thread when the face is tapped.
     * Only used on the main thread.
     */
    private val actions = HashMap<String, CallAction>()

    /** The language "Calling Mum" is said in, or null when Dialer has no sentence for it. */
    private var locale: Locale? = null

    /** Dialer's strings in [locale], so the sentence and the voice are always the same language. */
    private var phrases: Resources? = null

    /** "Calling Mum" asked for before the speech engine was ready. Spoken once it is, if the call is still waiting. */
    private var pendingSpeech: String? = null

    private val _voice = MutableStateFlow(VoiceState())
    val voice: StateFlow<VoiceState> = _voice.asStateFlow()

    // Someone who just tapped a face wants to hear the name now, so speak on the accessibility
    // stream, which Do Not Disturb never mutes.
    private val speaker = Speaker(
        context = application,
        audioUsage = AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
        onReady = { ready -> viewModelScope.launch { onSpeakerReady(ready) } },
    )

    private val speech = object : CallSpeech {
        override fun sayCalling(person: Person) {
            val sentence = sentence(person.name) ?: return
            pendingSpeech = if (speakNow(sentence)) null else sentence
        }

        override fun stop() {
            pendingSpeech = null
            speaker.stop()
        }
    }

    private val flow = CallFlow(
        scope = viewModelScope,
        speech = speech,
        placeCall = { person ->
            PhoneCalls.start(application, actions[person.number] ?: PhoneCalls.actionFor(application, person.number))
        },
    )

    val state: StateFlow<CallState> = flow.state

    init {
        // Language and speed come from helper settings and can change while the screen is open.
        viewModelScope.launch {
            SettingsRepository(application).settings.collect { new ->
                val languageChanged = new.voiceLanguage != settings.voiceLanguage || locale == null
                settings = new
                settingsLoaded = true
                speaker.setRate(new.speechRate)
                if (languageChanged) chooseLanguage()
            }
        }
    }

    /**
     * A face was tapped. The call starts unless it needs the call permission and Dialer doesn't
     * have it yet; then [onNeedsPermission] asks for it, and this is called again once it's granted.
     * Taps before the settings have loaded are ignored.
     */
    fun onFaceTapped(person: Person, onNeedsPermission: (Person) -> Unit = {}) {
        if (!settingsLoaded) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val action = withContext(Dispatchers.Default) { PhoneCalls.actionFor(app, person.number) }
            actions[person.number] = action
            if (action is CallAction.Call && !PhoneCalls.hasCallPermission(app)) {
                onNeedsPermission(person)
            } else {
                // A second tap that arrives meanwhile is ignored here, so only one call goes out.
                flow.start(person, settings.waitBeforeCalling)
            }
        }
    }

    fun onCall() = flow.confirm()

    fun onStop() = flow.stop()

    fun onLeftScreen() = flow.onLeft()

    /** "Calling Mum" with the chosen language and speed, for the helper's Test the voice. */
    fun onTestVoice() {
        val sample = phrases?.getString(R.string.voice_test_name) ?: return
        sentence(sample)?.let(::speakNow)
    }

    /** Dialer came back to the front. A helper may have installed a voice or changed the phone's language. */
    fun onResume() = chooseLanguage()

    // Only reads Dialer's own strings in the voice language. It doesn't change the screen's language
    // or need language packs from a store.
    @SuppressLint("AppBundleLocaleChanges")
    private fun chooseLanguage() {
        val app = getApplication<Application>()
        // "Same as phone" means the language Dialer's own screens are in.
        val chosen = voiceLocaleFor(settings.voiceLanguage, app.resources.configuration.locales[0])
        locale = chosen
        phrases = chosen?.let { lang ->
            val config = Configuration(app.resources.configuration).apply { setLocale(lang) }
            app.createConfigurationContext(config).resources
        }
        checkVoice()
    }

    /** One whole sentence per language, with the name as the helper typed it. */
    private fun sentence(name: String): String? = phrases?.getString(R.string.calling_name, name)

    /** False while the engine is still starting, so the sentence waits for it. */
    private fun speakNow(sentence: String): Boolean {
        // A helper may have installed a voice since the last check.
        if (_voice.value.voice != VoiceStatus.Ready) checkVoice()
        return when (_voice.value.voice) {
            VoiceStatus.Ready -> speaker.speak(sentence)
            VoiceStatus.Starting -> false
            // No voice for the language: say nothing rather than the wrong words. The hint line says why.
            else -> true
        }
    }

    private fun checkVoice() {
        val lang = locale
        if (lang == null) {
            setVoice(VoiceStatus.LanguageNotSupported)
            return
        }
        if (!speaker.isReady) return
        _voice.update { it.copy(engine = speaker.engineName()) }
        setVoice(if (speaker.useVoiceFor(lang)) VoiceStatus.Ready else VoiceStatus.NoVoice)
    }

    private fun setVoice(status: VoiceStatus) = _voice.update { it.copy(voice = status) }

    private fun onSpeakerReady(ready: Boolean) {
        if (!ready) {
            setVoice(VoiceStatus.NoEngine)
            return
        }
        checkVoice()
        val sentence = pendingSpeech ?: return
        pendingSpeech = null
        if (state.value !is CallState.Idle) speaker.speak(sentence)
    }

    override fun onCleared() {
        speaker.shutdown()
    }
}
