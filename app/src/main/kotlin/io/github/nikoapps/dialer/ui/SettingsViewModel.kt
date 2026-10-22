package io.github.nikoapps.dialer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nikoapps.dialer.people.PeopleRepository
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.PersonEdit
import io.github.nikoapps.dialer.people.PersonPhotos
import io.github.nikoapps.dialer.people.PhotoEdit
import io.github.nikoapps.dialer.people.contactAfter
import io.github.nikoapps.dialer.people.contactLink
import io.github.nikoapps.dialer.people.setContact
import io.github.nikoapps.dialer.people.removePerson as withoutPerson
import io.github.nikoapps.dialer.people.savePerson as withPersonSaved
import io.github.nikoapps.dialer.people.setPhoto
import io.github.nikoapps.dialer.screening.SilenceLog
import io.github.nikoapps.dialer.settings.DialerSettings
import io.github.nikoapps.dialer.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.time.Instant

/** Helper settings and the people on the grid, shared by the main screen and the helper screens. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val peopleRepository = PeopleRepository(application)

    /** Null until the stored settings have loaded, so the screen never flashes defaults. */
    val settings: StateFlow<DialerSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until loaded, so the grid never flashes empty. */
    val people: StateFlow<List<Person>?> = peopleRepository.people
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** When a call was last silenced, for "Last silenced call". Null until one is. */
    val lastSilencedAt: StateFlow<Instant?> = SilenceLog(application).lastSilencedAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (DialerSettings) -> DialerSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    /** Use the functions in Places.kt, such as `updatePeople { movePerson(it, id, place) }`, so nobody else moves. */
    fun updatePeople(transform: (List<Person>) -> List<Person>) {
        viewModelScope.launch { peopleRepository.update(transform) }
    }

    /**
     * Saves a new person ([id] null) or changes one, with what the editor did to their photo.
     * A new photo is put in place before the person is saved, so the face never points at a
     * missing file. If a new person can't be saved after all, their photo is deleted again.
     */
    fun savePerson(edit: PersonEdit) {
        val app = getApplication<Application>()
        val (id, name, number, place, photo) = edit
        viewModelScope.launch {
            val personId = id ?: UUID.randomUUID().toString()
            val photoSaved = withContext(Dispatchers.IO) {
                when (photo) {
                    is PhotoEdit.Replace -> PersonPhotos.save(app, personId, File(photo.path)).also { File(photo.path).delete() }
                    PhotoEdit.Remove -> false.also { PersonPhotos.delete(app, personId) }
                    PhotoEdit.Keep -> false
                }
            }
            if (photo != PhotoEdit.Keep) PhotoCache.forget(personId)
            var saved = false
            peopleRepository.update { people ->
                val before = people.firstOrNull { it.id == personId }?.contactLink
                // withPersonSaved hands back the same list when the person can't be saved there.
                val updated = withPersonSaved(people, personId, name, number, place, newId = { personId })
                    .let { if (it === people) it else setContact(it, personId, contactAfter(before, edit.contact)) }
                saved = updated.any { it.id == personId }
                when {
                    photoSaved -> setPhoto(updated, personId, hasPhoto = true)
                    photo == PhotoEdit.Remove -> setPhoto(updated, personId, hasPhoto = false)
                    else -> updated
                }
            }
            if (!saved && photoSaved) withContext(Dispatchers.IO) { PersonPhotos.delete(app, personId) }
        }
    }

    /** Removes a person from the grid and deletes their photo. Nobody else moves. */
    fun removePerson(person: Person) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            peopleRepository.update { withoutPerson(it, person.id) }
            withContext(Dispatchers.IO) { PersonPhotos.delete(app, person.id) }
            PhotoCache.forget(person.id)
        }
    }
}
