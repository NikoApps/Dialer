package io.github.nikoapps.dialer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    // An unreadable file starts again from the defaults rather than crashing Dialer, or the call screening, each time.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Reads and writes [DialerSettings]. One DataStore per process. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<DialerSettings> = store.data.map { it.toSettings() }

    suspend fun current(): DialerSettings = settings.first()

    suspend fun update(transform: (DialerSettings) -> DialerSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }
}

private object Keys {
    val peopleOnScreen = intPreferencesKey("people_on_screen")
    val waitBeforeCalling = stringPreferencesKey("wait_before_calling")
    val theme = stringPreferencesKey("theme")
    val voiceLanguage = stringPreferencesKey("voice_language")
    val speechRate = floatPreferencesKey("speech_rate")
    val onlySavedContacts = booleanPreferencesKey("only_saved_contacts")
    val setupDone = booleanPreferencesKey("setup_done")
    val pinHash = stringPreferencesKey("helper_pin_hash")
}

/** Reads stored settings. Anything missing or unknown, such as an option from a newer version, falls back to its default. */
internal fun Preferences.toSettings(): DialerSettings {
    val d = DialerSettings()
    return DialerSettings(
        peopleOnScreen = this[Keys.peopleOnScreen]?.takeIf { it in PEOPLE_ON_SCREEN_CHOICES } ?: d.peopleOnScreen,
        waitBeforeCalling = this[Keys.waitBeforeCalling]
            ?.let { name -> WaitBeforeCalling.entries.firstOrNull { it.name == name } }
            ?: d.waitBeforeCalling,
        theme = this[Keys.theme]?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } } ?: d.theme,
        voiceLanguage = this[Keys.voiceLanguage],
        speechRate = this[Keys.speechRate]?.takeIf { it > 0f } ?: d.speechRate,
        onlySavedContacts = this[Keys.onlySavedContacts] ?: d.onlySavedContacts,
        setupDone = this[Keys.setupDone] ?: d.setupDone,
        helperPinHash = this[Keys.pinHash],
    )
}

internal fun MutablePreferences.write(s: DialerSettings) {
    this[Keys.peopleOnScreen] = s.peopleOnScreen
    this[Keys.waitBeforeCalling] = s.waitBeforeCalling.name
    this[Keys.theme] = s.theme.name
    if (s.voiceLanguage == null) remove(Keys.voiceLanguage) else this[Keys.voiceLanguage] = s.voiceLanguage
    this[Keys.speechRate] = s.speechRate
    this[Keys.onlySavedContacts] = s.onlySavedContacts
    this[Keys.setupDone] = s.setupDone
    if (s.helperPinHash == null) remove(Keys.pinHash) else this[Keys.pinHash] = s.helperPinHash
}
