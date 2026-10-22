package io.github.nikoapps.dialer.recording

import android.content.Context
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.people.ContactByNumber
import io.github.nikoapps.dialer.people.ContactLink
import io.github.nikoapps.dialer.people.DialerPermissions
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.PhoneContacts
import io.github.nikoapps.dialer.people.contactLink
import java.io.File

/**
 * Recorded names: the master recording in Dialer's own files, its ringtone copy in the phone's
 * Ringtones, and the contact's ringtone pointing at that copy. The master is the one that
 * matters: the copy and the contact's ringtone are made again from it whenever they're missing,
 * such as after moving to a new phone (Hub ADR 0033), where only Dialer's own files come along.
 *
 * Everything here reads or writes files and contacts, so call it off the main thread.
 */
object RecordedNames {

    fun masterFile(context: Context, personId: String): File =
        File(File(context.filesDir, "recordings"), recordingFileName(personId))

    /** A recording in the editor, not saved yet. The phone may clear these. */
    fun newRecordingFile(context: Context, name: String): File =
        File(File(context.cacheDir, "recordings").apply { mkdirs() }, name)

    /** Moves a new recording into place as the person's master. Written to a temporary file first. */
    fun saveMaster(context: Context, personId: String, recording: File): Boolean = runCatching {
        val target = masterFile(context, personId)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        recording.inputStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
        if (!temp.renameTo(target)) {
            temp.delete()
            return@runCatching false
        }
        true
    }.getOrDefault(false)

    /** How far a person's recorded name got. */
    sealed interface Outcome {
        /** Set as the contact's ringtone, or there's no recording to set. [person] has the link and ringtone Dialer now knows. */
        data class Ready(val person: Person) : Outcome

        /** Recorded, but not ringing for the contact yet. [person] has whatever did get done. */
        data class NotSet(val person: Person, val reason: Reason) : Outcome
    }

    enum class Reason {
        /** Contacts aren't allowed, so the ringtone can't be set or checked. */
        NoContactsPermission,

        /** Android 8 or 9 without the storage permission, so the copy can't go in Ringtones. */
        NoStoragePermission,

        /** The person has no contact, or it was deleted, and none could be made. */
        NoContact,

        /** The copy couldn't be added to Ringtones. */
        CopyFailed,

        /** The contact has another ringtone, chosen elsewhere. */
        ChosenElsewhere,

        /** The master recording is gone, so there's nothing to set. */
        NoMaster,

        /** The contacts app didn't take the new ringtone. */
        SetFailed,
    }

    /**
     * Makes sure the person's recorded name rings for their contact: the copy in Ringtones exists,
     * and the contact's ringtone points at it. Quietly ([fix] false) it only puts back what's
     * missing, and leaves a ringtone someone chose in the contacts app alone, the phone's default
     * included. When the helper asks ([fix] true) it also saves the person in contacts if they have
     * no contact, and sets the recording over another ringtone.
     */
    fun sync(context: Context, person: Person, fix: Boolean): Outcome {
        if (!person.hasRecording) return Outcome.Ready(person)
        val master = masterFile(context, person.id)
        if (!master.isFile) return Outcome.NotSet(person, Reason.NoMaster)
        if (!DialerPermissions.hasContacts(context)) return Outcome.NotSet(person, Reason.NoContactsPermission)

        val found = person.contactLink?.let { PhoneContacts.find(context, it) }
            ?: findOrMakeContact(context, person, fix)?.let { PhoneContacts.find(context, it) }
            ?: return Outcome.NotSet(person, Reason.NoContact)
        val now = person.copy(contactLookupKey = found.link.lookupKey, contactId = found.link.contactId)

        // Decided before any copy is made, so a ringtone chosen elsewhere never leaves a stray copy behind.
        val ours = person.ringtoneUri
        val oursExists = RingtoneStore.exists(context, ours)
        val contactRingtone = found.ringtone
        val gone = contactRingtone != null && contactRingtone != ours && RingtoneStore.isGone(context, contactRingtone)
        when (ringtoneState(contactRingtone, ours, oursExists, gone)) {
            RingtoneState.Set -> return Outcome.Ready(now)
            RingtoneState.ChosenElsewhere -> if (!fix) return Outcome.NotSet(now, Reason.ChosenElsewhere)
            RingtoneState.NeedsSetting -> Unit
        }

        if (oursExists) return setOn(context, now, found.link)
        if (!DialerPermissions.hasStorage(context)) return Outcome.NotSet(now, Reason.NoStoragePermission)
        val copy = RingtoneStore.add(context, master, title(context, person.name)) ?: return Outcome.NotSet(now, Reason.CopyFailed)
        val outcome = setOn(context, now.copy(ringtoneUri = copy), found.link)
        // The person only keeps a ringtone the contact really has, so Dialer still knows its last
        // one next time. A copy the contact didn't take goes again.
        if (outcome is Outcome.NotSet) {
            RingtoneStore.delete(context, copy)
            return Outcome.NotSet(now, outcome.reason)
        }
        return outcome
    }

    /**
     * Takes the recorded name off the person: puts their contact back on the phone's default
     * ringtone if it's still Dialer's, deletes the copy in Ringtones and the master. A ringtone
     * chosen in the contacts app stays. Without contacts allowed, the contact can't be changed;
     * the phone then plays its default ringtone, since the copy is gone.
     */
    fun remove(context: Context, person: Person) {
        clearContact(context, person, person.contactLink)
        if (person.ringtoneUri.isNotBlank()) RingtoneStore.delete(context, person.ringtoneUri)
        masterFile(context, person.id).delete()
    }

    /** Puts [link]'s ringtone back to the phone's default, only if it's still this person's recording. */
    fun clearContact(context: Context, person: Person, link: ContactLink?) {
        if (link == null || person.ringtoneUri.isBlank() || !DialerPermissions.hasContacts(context)) return
        val found = PhoneContacts.find(context, link) ?: return
        if (ringtoneIsOurs(found.ringtone, setOf(person.ringtoneUri))) PhoneContacts.setRingtone(context, found.link, null)
    }

    /** Re-recording: the old copy goes, and [sync] makes a new one from the new master. */
    fun replaceCopy(context: Context, person: Person): Person {
        if (person.ringtoneUri.isNotBlank()) RingtoneStore.delete(context, person.ringtoneUri)
        // The uri is kept, so the contact's ringtone still counts as Dialer's and is set to the new copy.
        return person
    }

    /** "Mum is calling - Niko Dialer": the name only, never the number. */
    fun title(context: Context, name: String): String = ringtoneTitle(name, context.getString(R.string.ringtone_someone)) {
        context.getString(R.string.ringtone_title, it, context.getString(R.string.app_name))
    }

    /** Several contacts with the number and none by the person's name: none is guessed, and none is made. */
    private fun findOrMakeContact(context: Context, person: Person, fix: Boolean): ContactLink? =
        when (val match = PhoneContacts.findByNumber(context, person.number, person.name)) {
            is ContactByNumber.One -> match.link
            ContactByNumber.None -> if (fix) PhoneContacts.saveOnPhone(context, person.name, person.number) else null
            ContactByNumber.Several -> null
        }

    private fun setOn(context: Context, person: Person, link: ContactLink): Outcome =
        if (PhoneContacts.setRingtone(context, link, person.ringtoneUri)) Outcome.Ready(person) else Outcome.NotSet(person, Reason.SetFailed)
}
