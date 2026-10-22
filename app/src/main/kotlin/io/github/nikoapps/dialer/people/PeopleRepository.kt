package io.github.nikoapps.dialer.people

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

private const val STORE_NAME = "people"

/** Where an unreadable people file is kept. While it's there and nobody is saved, the main screen says so. */
private fun unreadableCopy(context: Context): File = File(context.filesDir, "people-unreadable.preferences_pb")

/**
 * One DataStore per process. A file that can't be read any more starts empty rather than
 * crashing Dialer each time it opens, but the old file is kept and the main screen says the
 * people couldn't be read, so nobody is lost without a word.
 */
private object PeopleStore {
    @Volatile
    private var instance: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> = instance ?: synchronized(this) {
        instance ?: run {
            val app = context.applicationContext
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler {
                    runCatching { app.preferencesDataStoreFile(STORE_NAME).copyTo(unreadableCopy(app), overwrite = true) }
                    emptyPreferences()
                },
                produceFile = { app.preferencesDataStoreFile(STORE_NAME) },
            ).also { instance = it }
        }
    }
}

/**
 * Reads and writes the people on the grid. Change them with the functions in Places.kt
 * inside [update], such as `update { movePerson(it, id, place) }`, so nobody else moves.
 */
class PeopleRepository(context: Context) {
    private val app = context.applicationContext
    private val store = PeopleStore.get(app)

    val people: Flow<List<Person>> = store.data.map { PeopleCodec.decode(it[Keys.people]) }

    /** True while nobody is saved because the saved people couldn't be read. Reads a file, so call it off the main thread. */
    val unreadable: Flow<Boolean> = people.map { it.isEmpty() && unreadableCopy(app).exists() }

    suspend fun current(): List<Person> = people.first()

    suspend fun update(transform: (List<Person>) -> List<Person>) {
        store.edit { prefs ->
            val updated = transform(PeopleCodec.decode(prefs[Keys.people]))
            if (updated.isEmpty()) prefs.remove(Keys.people) else prefs[Keys.people] = PeopleCodec.encode(updated)
        }
    }

    /** The helper has added people again, so the unreadable file and its message go. Call it off the main thread. */
    fun forgetUnreadable() {
        unreadableCopy(app).delete()
    }

    private object Keys {
        val people = stringPreferencesKey("people")
    }
}
