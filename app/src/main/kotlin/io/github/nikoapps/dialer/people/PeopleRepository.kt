package io.github.nikoapps.dialer.people

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.peopleStore: DataStore<Preferences> by preferencesDataStore(name = "people")

/**
 * Reads and writes the people on the grid. Change them with the functions in Places.kt
 * inside [update], such as `update { movePerson(it, id, place) }`, so nobody else moves.
 */
class PeopleRepository(context: Context) {
    private val store = context.applicationContext.peopleStore

    val people: Flow<List<Person>> = store.data.map { PeopleCodec.decode(it[Keys.people]) }

    suspend fun current(): List<Person> = people.first()

    suspend fun update(transform: (List<Person>) -> List<Person>) {
        store.edit { prefs ->
            val updated = transform(PeopleCodec.decode(prefs[Keys.people]))
            if (updated.isEmpty()) prefs.remove(Keys.people) else prefs[Keys.people] = PeopleCodec.encode(updated)
        }
    }

    private object Keys {
        val people = stringPreferencesKey("people")
    }
}
