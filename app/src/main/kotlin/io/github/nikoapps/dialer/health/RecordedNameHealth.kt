package io.github.nikoapps.dialer.health

/** What can stop recorded names playing when people call. */
enum class RecordedNameProblem {
    /** Contacts aren't allowed, so Dialer can't set or check the ringtones. */
    ContactsPermission,

    /** The ringer is on silent or vibrate, or the ring volume is at zero. */
    RingerOff,

    /** A recorded name isn't its contact's ringtone, and Dialer couldn't quietly put it back. */
    NotSet,
}

/**
 * The recorded name problems to show, in order. Nothing matters until a name is recorded. Without
 * contacts Dialer can't tell whether a ringtone is set, so [notSet] only counts with contacts allowed.
 */
fun recordedNameProblems(
    anyRecording: Boolean,
    contactsAllowed: Boolean,
    ringerAudible: Boolean,
    notSet: Boolean,
): List<RecordedNameProblem> {
    if (!anyRecording) return emptyList()
    return buildList {
        if (!contactsAllowed) add(RecordedNameProblem.ContactsPermission)
        if (!ringerAudible) add(RecordedNameProblem.RingerOff)
        if (contactsAllowed && notSet) add(RecordedNameProblem.NotSet)
    }
}
