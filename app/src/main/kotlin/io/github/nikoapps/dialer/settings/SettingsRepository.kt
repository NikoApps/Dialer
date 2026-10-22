package io.github.nikoapps.dialer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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

    private object Keys {
        val peopleOnScreen = intPreferencesKey("people_on_screen")
        val waitBeforeCalling = stringPreferencesKey("wait_before_calling")
        val setupDone = booleanPreferencesKey("setup_done")
        val pinHash = stringPreferencesKey("helper_pin_hash")
    }

    private fun Preferences.toSettings(): DialerSettings {
        val d = DialerSettings()
        return DialerSettings(
            peopleOnScreen = this[Keys.peopleOnScreen]?.takeIf { it in PEOPLE_ON_SCREEN_CHOICES } ?: d.peopleOnScreen,
            waitBeforeCalling = this[Keys.waitBeforeCalling]
                ?.let { name -> WaitBeforeCalling.entries.firstOrNull { it.name == name } }
                ?: d.waitBeforeCalling,
            setupDone = this[Keys.setupDone] ?: d.setupDone,
            helperPinHash = this[Keys.pinHash],
        )
    }

    private fun MutablePreferences.write(s: DialerSettings) {
        this[Keys.peopleOnScreen] = s.peopleOnScreen
        this[Keys.waitBeforeCalling] = s.waitBeforeCalling.name
        this[Keys.setupDone] = s.setupDone
        if (s.helperPinHash == null) remove(Keys.pinHash) else this[Keys.pinHash] = s.helperPinHash
    }
}
