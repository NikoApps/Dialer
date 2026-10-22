package io.github.nikoapps.dialer.ui

import android.app.Application
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nikoapps.dialer.people.ContactByNumber
import io.github.nikoapps.dialer.people.contactAfter
import io.github.nikoapps.dialer.people.contactLink
import io.github.nikoapps.dialer.people.DialerPermissions
import io.github.nikoapps.dialer.people.FileBackup
import io.github.nikoapps.dialer.people.PeopleRepository
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.PersonEdit
import io.github.nikoapps.dialer.people.PersonPhotos
import io.github.nikoapps.dialer.people.PhoneContacts
import io.github.nikoapps.dialer.people.PhotoEdit
import io.github.nikoapps.dialer.people.RecordingEdit
import io.github.nikoapps.dialer.people.removePerson as withoutPerson
import io.github.nikoapps.dialer.people.savePerson as withPersonSaved
import io.github.nikoapps.dialer.people.setContact
import io.github.nikoapps.dialer.people.setPhoto
import io.github.nikoapps.dialer.people.setRecording
import io.github.nikoapps.dialer.recording.RecordedNames
import io.github.nikoapps.dialer.screening.SilenceLog
import io.github.nikoapps.dialer.settings.DialerSettings
import io.github.nikoapps.dialer.settings.SettingsRepository
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Helper settings and the people on the grid, shared by the main screen and the helper screens. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val peopleRepository = PeopleRepository(application)

    /** Held while a person's files, recorded names and ringtones change, so a save, a removal and a check never overlap. */
    private val personFiles = Mutex()

    /**
     * False in a new process, until the screen saved before Android stopped Dialer has been
     * checked against the PIN. A config change keeps this view model, so it stays true then.
     */
    var screenRestored = false

    private val _recordedNameNotSet = MutableStateFlow(false)

    /** The last check found a recorded name that isn't its contact's ringtone and couldn't be put back quietly. */
    val recordedNameNotSet: StateFlow<Boolean> = _recordedNameNotSet.asStateFlow()

    /** Null until the stored settings have loaded, so the screen never flashes defaults. */
    val settings: StateFlow<DialerSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until loaded, so the grid never flashes empty. */
    val people: StateFlow<List<Person>?> = peopleRepository.people
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** When a call was last silenced, for "Last silenced call". Null until one is. */
    val lastSilencedAt: StateFlow<Instant?> = SilenceLog(application).lastSilencedAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Screen text can only follow its own language on Android 13+. */
    val canChooseScreenLanguage: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The screen language as a language code ("en", "ne"), or null when it follows the phone. */
    fun screenLanguage(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = getApplication<Application>().getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) null else locales[0].language
    }

    /** Changes only the screen text, not the voice. The activity restarts in the new language. */
    fun setScreenLanguage(tag: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        getApplication<Application>().getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    fun update(transform: (DialerSettings) -> DialerSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    /** Use the functions in Places.kt, such as `updatePeople { movePerson(it, id, place) }`, so nobody else moves. */
    fun updatePeople(transform: (List<Person>) -> List<Person>) {
        viewModelScope.launch { peopleRepository.update(transform) }
    }

    /**
     * Saves a new person ([id] null) or changes one, with what the editor did to their photo,
     * contact and recorded name. A new photo or recording is put in place before the person is
     * saved, so they never point at a missing file, and the old one is kept aside until the save
     * is in. If the person can't be saved after all, the old files come back, and a removed photo
     * or recording is only deleted once the save is in. Then the person is linked to the phone's
     * contacts and their recorded name set as that contact's ringtone, as far as the permissions
     * allow. A ringtone chosen elsewhere is only replaced when the recording or the contact changed.
     */
    fun savePerson(edit: PersonEdit) {
        val app = getApplication<Application>()
        val (id, name, number, place, photo) = edit
        val recording = edit.recording
        viewModelScope.launch {
            val personId = id ?: UUID.randomUUID().toString()
            personFiles.withLock {
                val before = peopleRepository.current().firstOrNull { it.id == personId }
                var photoBackup: FileBackup? = null
                var recordingBackup: FileBackup? = null
                val (photoSaved, recordingSaved) = withContext(Dispatchers.IO) {
                    val photoSaved = (photo as? PhotoEdit.Replace)?.let {
                        photoBackup = FileBackup.of(PersonPhotos.file(app, personId))
                        PersonPhotos.save(app, personId, File(it.path)).also { _ -> File(it.path).delete() }
                    } ?: false
                    val recordingSaved = (recording as? RecordingEdit.Replace)?.let {
                        recordingBackup = FileBackup.of(RecordedNames.masterFile(app, personId))
                        RecordedNames.saveMaster(app, personId, File(it.path)).also { _ -> File(it.path).delete() }
                    } ?: false
                    photoSaved to recordingSaved
                }
                if (photoSaved) PhotoCache.forget(personId)
                var saved = false
                peopleRepository.update { people ->
                    // withPersonSaved hands back the same list when the person can't be saved there.
                    var updated = withPersonSaved(people, personId, name, number, place, newId = { personId })
                    if (updated === people) return@update people
                    saved = true
                    updated = setContact(updated, personId, contactAfter(before?.contactLink, edit.contact))
                    updated = when {
                        photoSaved -> setPhoto(updated, personId, hasPhoto = true)
                        photo == PhotoEdit.Remove -> setPhoto(updated, personId, hasPhoto = false)
                        else -> updated
                    }
                    when {
                        recordingSaved -> setRecording(updated, personId, hasRecording = true)
                        recording == RecordingEdit.Remove -> setRecording(updated, personId, hasRecording = false)
                        else -> updated
                    }
                }
                if (!saved) {
                    withContext(Dispatchers.IO) {
                        photoBackup?.let { if (photoSaved) it.restore() else it.drop() }
                        recordingBackup?.let { if (recordingSaved) it.restore() else it.drop() }
                    }
                    // A face read while the new photo was in place mustn't stay on screen.
                    if (photoSaved) PhotoCache.forget(personId)
                    return@withLock
                }
                // Saved, so what the old files were for can go now.
                withContext(Dispatchers.IO) {
                    photoBackup?.drop()
                    recordingBackup?.drop()
                    if (photo == PhotoEdit.Remove) PersonPhotos.delete(app, personId)
                    if (before != null) {
                        when {
                            // The old copy in Ringtones goes; a new one is made from the new master below.
                            recordingSaved -> RecordedNames.replaceCopy(app, before)
                            recording == RecordingEdit.Remove -> RecordedNames.remove(app, before)
                        }
                    }
                }
                if (photo == PhotoEdit.Remove) PhotoCache.forget(personId)
                val after = peopleRepository.current().firstOrNull { it.id == personId } ?: return@withLock
                val contactChanged = before?.contactLink != after.contactLink
                // A new contact for someone with a recording: the old contact gets its own ringtone back.
                if (before != null && contactChanged && after.hasRecording) {
                    withContext(Dispatchers.IO) { RecordedNames.clearContact(app, before, before.contactLink) }
                }
                linkContact(personId, mayCreate = true)
                // A ringtone chosen elsewhere is only replaced for a new recording or a new contact.
                val force = recordingSaved || contactChanged
                if (syncRecordedName(personId, fix = force) is RecordedNames.Outcome.NotSet) _recordedNameNotSet.value = true
            }
            // Brings the health check up to date for everyone, now this person is done.
            checkRecordedNames()
        }
    }

    /**
     * Sets one person's recorded name as their contact's ringtone, and keeps what Dialer learnt
     * (the contact, the ringtone copy) on the person. Call with [personFiles] held.
     */
    private suspend fun syncRecordedName(personId: String, fix: Boolean): RecordedNames.Outcome? {
        val app = getApplication<Application>()
        val person = peopleRepository.current().firstOrNull { it.id == personId } ?: return null
        val outcome = withContext(Dispatchers.IO) { RecordedNames.sync(app, person, fix) }
        val learnt = when (outcome) {
            is RecordedNames.Outcome.Ready -> outcome.person
            is RecordedNames.Outcome.NotSet -> outcome.person
        }
        if (learnt != person) {
            peopleRepository.update { people ->
                people.map {
                    // Only onto the same recording; if it changed meanwhile, the next check catches up.
                    if (it.id != personId || it.recordingVersion != person.recordingVersion) {
                        it
                    } else {
                        it.copy(contactLookupKey = learnt.contactLookupKey, contactId = learnt.contactId, ringtoneUri = learnt.ringtoneUri)
                    }
                }
            }
        }
        return outcome
    }

    /**
     * Links a person with no contact to the phone's contacts, while contacts are allowed: to the
     * contact with their number if there is exactly one (or one by their name), otherwise, with none, when [mayCreate], to a new contact saved
     * on the phone. Typed people are saved this way, so silencing never catches their calls and a
     * recorded name has a contact to ring for.
     */
    private suspend fun linkContact(personId: String, mayCreate: Boolean) {
        val app = getApplication<Application>()
        if (!DialerPermissions.hasContacts(app)) return
        val person = peopleRepository.current().firstOrNull { it.id == personId } ?: return
        if (person.hasContact) return
        val link = withContext(Dispatchers.IO) {
            when (val match = PhoneContacts.findByNumber(app, person.number, person.name)) {
                is ContactByNumber.One -> match.link
                ContactByNumber.None -> if (mayCreate) PhoneContacts.saveOnPhone(app, person.name, person.number) else null
                // Several contacts share the number: none is guessed, and no extra one is made.
                ContactByNumber.Several -> null
            }
        } ?: return
        // Only if they're still the same person with no contact; the helper may have changed them meanwhile.
        peopleRepository.update { people ->
            val now = people.firstOrNull { it.id == personId }
            if (now == null || now.hasContact || now.number != person.number) people else setContact(people, personId, link)
        }
    }

    /**
     * Checks each recorded name still rings for its contact, and quietly puts back what's
     * missing: the copy in Ringtones, or the contact's ringtone, such as after moving to a new
     * phone. A ringtone chosen in the contacts app is left alone and reported. Run each time
     * Dialer comes to the front; skipped while a save or another check is running.
     *
     * With [fix], as when the helper taps the problem in settings, it also saves people with no
     * contact in the phone's contacts and sets recorded names over other ringtones.
     */
    fun checkRecordedNames(fix: Boolean = false) {
        if (!fix && personFiles.isLocked) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            personFiles.withLock {
                var notSet = false
                for (person in peopleRepository.current().filter { it.hasRecording }) {
                    val outcome = syncRecordedName(person.id, fix) as? RecordedNames.Outcome.NotSet ?: continue
                    if (outcome.reason == RecordedNames.Reason.NoMaster) {
                        // The recording's file is gone, so there's nothing to ring with. The editor then offers Record again.
                        withContext(Dispatchers.IO) { RecordedNames.clearContact(app, outcome.person, outcome.person.contactLink) }
                        peopleRepository.update { setRecording(it, person.id, hasRecording = false) }
                    } else {
                        notSet = true
                    }
                }
                _recordedNameNotSet.value = notSet
            }
        }
    }

    /**
     * Removes a person from the grid and deletes their photo and recorded name. Their contact
     * stays in the phone's contacts, back on the phone's default ringtone. Nobody else moves.
     */
    fun removePerson(person: Person) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            personFiles.withLock {
                val stored = peopleRepository.current().firstOrNull { it.id == person.id } ?: person
                peopleRepository.update { withoutPerson(it, person.id) }
                withContext(Dispatchers.IO) {
                    PersonPhotos.delete(app, person.id)
                    RecordedNames.remove(app, stored)
                }
            }
            PhotoCache.forget(person.id)
            checkRecordedNames()
        }
    }
}
