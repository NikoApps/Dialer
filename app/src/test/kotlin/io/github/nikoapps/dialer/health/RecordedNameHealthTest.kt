package io.github.nikoapps.dialer.health

import io.github.nikoapps.dialer.health.RecordedNameProblem.ContactsPermission
import io.github.nikoapps.dialer.health.RecordedNameProblem.NotSet
import io.github.nikoapps.dialer.health.RecordedNameProblem.RingerOff
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordedNameHealthTest {

    @Test
    fun `nothing matters until a name is recorded`() {
        assertEquals(emptyList<RecordedNameProblem>(), recordedNameProblems(anyRecording = false, contactsAllowed = false, ringerAudible = false, notSet = true))
    }

    @Test
    fun `all well is no problem`() {
        assertEquals(emptyList<RecordedNameProblem>(), recordedNameProblems(anyRecording = true, contactsAllowed = true, ringerAudible = true, notSet = false))
    }

    @Test
    fun `missing contacts is a problem, and hides the ringtone check it stops`() {
        assertEquals(listOf(ContactsPermission), recordedNameProblems(anyRecording = true, contactsAllowed = false, ringerAudible = true, notSet = true))
    }

    @Test
    fun `a silent ringer is a problem`() {
        assertEquals(listOf(RingerOff), recordedNameProblems(anyRecording = true, contactsAllowed = true, ringerAudible = false, notSet = false))
    }

    @Test
    fun `a name that isn't the contact's ringtone is a problem`() {
        assertEquals(listOf(NotSet), recordedNameProblems(anyRecording = true, contactsAllowed = true, ringerAudible = true, notSet = true))
    }

    @Test
    fun `problems come in a fixed order`() {
        assertEquals(listOf(RingerOff, NotSet), recordedNameProblems(anyRecording = true, contactsAllowed = true, ringerAudible = false, notSet = true))
        assertEquals(listOf(ContactsPermission, RingerOff), recordedNameProblems(anyRecording = true, contactsAllowed = false, ringerAudible = false, notSet = true))
    }
}
