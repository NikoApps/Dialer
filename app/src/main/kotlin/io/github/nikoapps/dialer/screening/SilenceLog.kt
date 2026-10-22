package io.github.nikoapps.dialer.screening

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.silenceLogStore: DataStore<Preferences> by preferencesDataStore(name = "silence_log")

/**
 * When Dialer last silenced a call, so a family member can check silencing works and see if
 * someone important was caught. Only the time is kept, never the number.
 */
class SilenceLog(context: Context) {
    private val store = context.applicationContext.silenceLogStore

    val lastSilencedAt: Flow<Instant?> = store.data.map { prefs -> prefs[Keys.lastSilenced]?.let(Instant::ofEpochMilli) }

    suspend fun record(at: Instant) {
        store.edit { prefs -> prefs[Keys.lastSilenced] = at.toEpochMilli() }
    }

    private object Keys {
        val lastSilenced = longPreferencesKey("last_silenced_at_millis")
    }
}
