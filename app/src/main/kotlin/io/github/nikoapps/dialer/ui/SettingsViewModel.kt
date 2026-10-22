package io.github.nikoapps.dialer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nikoapps.dialer.people.PeopleRepository
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.settings.DialerSettings
import io.github.nikoapps.dialer.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun update(transform: (DialerSettings) -> DialerSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    /** Use the functions in Places.kt, such as `updatePeople { movePerson(it, id, place) }`, so nobody else moves. */
    fun updatePeople(transform: (List<Person>) -> List<Person>) {
        viewModelScope.launch { peopleRepository.update(transform) }
    }
}
