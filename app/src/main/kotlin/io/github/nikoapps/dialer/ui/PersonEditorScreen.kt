package io.github.nikoapps.dialer.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import io.github.nikoapps.commons.health.PhoneHealth
import io.github.nikoapps.commons.theme.NikoTheme
import io.github.nikoapps.commons.ui.ChoiceRow
import io.github.nikoapps.commons.ui.ProvideInfoStrings
import io.github.nikoapps.commons.ui.ScreenTopBar
import io.github.nikoapps.commons.ui.SecondaryButton
import io.github.nikoapps.commons.ui.SolidButton
import io.github.nikoapps.commons.ui.openPage
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.call.CallAction
import io.github.nikoapps.dialer.call.PhoneCalls
import io.github.nikoapps.dialer.people.ContactEdit
import io.github.nikoapps.dialer.people.ContactLink
import io.github.nikoapps.dialer.people.AskFor
import io.github.nikoapps.dialer.people.DialerPermissions
import io.github.nikoapps.dialer.people.EditorProblem
import io.github.nikoapps.dialer.people.PhoneContacts
import io.github.nikoapps.dialer.people.contactAfter
import io.github.nikoapps.dialer.people.contactLink
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.PersonPhotos
import io.github.nikoapps.dialer.people.PersonEdit
import io.github.nikoapps.dialer.people.PhotoEdit
import io.github.nikoapps.dialer.people.PickPhoneNumber
import io.github.nikoapps.dialer.people.editorProblems
import io.github.nikoapps.dialer.people.personColor
import io.github.nikoapps.dialer.people.placeChoices
import io.github.nikoapps.dialer.people.placePosition
import io.github.nikoapps.dialer.people.readPickedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import io.github.nikoapps.commons.R as CommonsR

/**
 * Adds a person at [place], or changes the person already there: name, number, photo and place.
 * Recorded names come later. This helper screen may scroll; the main screen never does.
 *
 * [onSave] gets the trimmed name and number, what to do with the photo and the contact, and the
 * chosen place, and only once they're valid. A new photo is cropped here and only kept once the
 * person is saved. A contact picked here stays linked while its number is the one to call.
 */
@Composable
fun PersonEditorScreen(
    people: List<Person>,
    place: Int,
    peopleOnScreen: Int,
    onSave: (PersonEdit) -> Unit,
    onRemove: (Person) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit = onBack,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val existing = remember(people, place) { people.firstOrNull { it.place == place } }
    // Kept by id, so the screen still knows who it's editing if they're moved while it's open.
    val editingId by rememberSaveable { mutableStateOf(existing?.id) }

    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var number by rememberSaveable { mutableStateOf(existing?.number.orEmpty()) }
    var chosenPlace by rememberSaveable { mutableIntStateOf(place) }
    var showProblems by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    // A new photo, cropped and waiting in the cache until Save.
    var newPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var photoRemoved by rememberSaveable { mutableStateOf(false) }
    // The picked contact's photo, copied while the contacts app allowed reading it.
    var contactPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    // The photo being cropped. While set, the crop screen shows instead of the form.
    var cropSource by rememberSaveable { mutableStateOf<String?>(null) }
    var photoMessage by rememberSaveable { mutableStateOf<Int?>(null) }
    // The contact picked here, if any, and the number it was picked with.
    var pickedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedId by rememberSaveable { mutableLongStateOf(0L) }
    var linkedNumber by rememberSaveable { mutableStateOf(existing?.number.orEmpty()) }
    // The picked number is stored on the SIM, which can't hold a ringtone.
    var pickedOnSim by rememberSaveable { mutableStateOf(false) }
    var contactMessage by rememberSaveable { mutableStateOf<Int?>(null) }
    // Contacts were refused when saving, so the person is saved in Dialer only. Shows why that matters.
    var savedWithoutContacts by rememberSaveable { mutableStateOf(false) }
    var contactsBlocked by rememberSaveable { mutableStateOf(false) }
    // What to do once the contacts question closes.
    var afterContacts by rememberSaveable { mutableStateOf<AfterContacts?>(null) }

    val editing = people.firstOrNull { it.id == editingId }
    val choices = remember(people, editingId, peopleOnScreen) {
        (placeChoices(people, editingId, peopleOnScreen) + place).distinct().sorted()
    }
    val problems = editorProblems(name, number)
    val emergency = remember(number) { PhoneCalls.actionFor(context, number) is CallAction.Dial }

    val pickContact = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (picked, photo, onSim) = withContext(Dispatchers.IO) {
                val picked = readPickedContact(context, uri)
                Triple(
                    picked,
                    picked?.let { PersonPhotos.copyContactPhoto(context, it.photoUris) },
                    picked != null && PhoneContacts.isOnSim(context, picked.account),
                )
            }
            if (picked == null) {
                message = R.string.editor_contact_unreadable
            } else {
                message = null
                if (picked.name.isNotEmpty()) name = picked.name
                number = picked.number
                contactPhoto = photo?.path
                pickedKey = picked.link?.lookupKey.orEmpty()
                pickedId = picked.link?.contactId ?: 0L
                linkedNumber = picked.number
                pickedOnSim = onSim
                contactMessage = null
            }
        }
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) cropSource = PersonPhotos.cameraPicture(context).toString()
    }
    // Uses the phone's photo picker, or the older document picker on phones without it.
    val choosePhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropSource = uri.toString()
    }
    val hasPhoto = newPhoto != null || (!photoRemoved && editing?.hasPhoto == true)
    val photoEdit = when {
        newPhoto != null -> PhotoEdit.Replace(newPhoto!!)
        photoRemoved -> PhotoEdit.Remove
        else -> PhotoEdit.Keep
    }

    val contactEdit = when {
        // A number typed or changed by hand: the old contact's ringtone isn't this number's.
        number.trim() != linkedNumber.trim() -> ContactEdit.Unlink
        pickedKey == null -> ContactEdit.Keep
        pickedKey.isNullOrEmpty() -> ContactEdit.Unlink
        else -> ContactEdit.Link(ContactLink(pickedId, pickedKey!!))
    }

    // Without a contact, Save also keeps the person in the phone's contacts, so the ringtone has
    // somewhere to live and silencing doesn't catch their calls.
    val linkedAfterSave = contactAfter(editing?.contactLink, contactEdit) != null
    val hasContactsPermission = DialerPermissions.hasContacts(context)

    val saveNow = { askedForContacts: Boolean ->
        onSave(PersonEdit(editingId, name.trim(), number.trim(), chosenPlace, photoEdit, contactEdit))
        if (askedForContacts && !DialerPermissions.hasContacts(context)) savedWithoutContacts = true else onDone()
    }
    val copyToPhone = {
        scope.launch {
            val link = withContext(Dispatchers.IO) { PhoneContacts.saveOnPhone(context, name.trim(), number.trim()) }
            if (link == null) {
                contactMessage = R.string.editor_sim_copy_failed
            } else {
                pickedKey = link.lookupKey
                pickedId = link.contactId
                linkedNumber = number
                pickedOnSim = false
                contactMessage = R.string.editor_sim_copied
            }
        }
    }
    val askForContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = DialerPermissions.hasContacts(context)
        contactsBlocked = !granted && activity != null &&
            DialerPermissions.contacts.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        when (afterContacts) {
            AfterContacts.Save -> saveNow(true)
            AfterContacts.Copy -> if (granted) copyToPhone() else contactMessage = R.string.editor_sim_needs_contacts
            null -> Unit
        }
        afterContacts = null
    }
    val askContactsThen = { then: AfterContacts ->
        afterContacts = then
        askForContacts.launch(DialerPermissions.toAsk(context, AskFor.SaveContact).toTypedArray())
    }

    val save = {
        when {
            problems.isNotEmpty() -> showProblems = true
            chosenPlace !in choices -> message = R.string.editor_place_taken
            !linkedAfterSave && !hasContactsPermission -> askContactsThen(AfterContacts.Save)
            else -> saveNow(false)
        }
    }

    cropSource?.let { source ->
        PhotoCropScreen(
            source = source,
            onCropped = { file ->
                newPhoto?.let { File(it).delete() }
                newPhoto = file.path
                photoRemoved = false
                photoMessage = null
                cropSource = null
            },
            onCancel = { cropSource = null },
            onUnreadable = {
                photoMessage = R.string.editor_photo_unreadable
                cropSource = null
            },
        )
        return
    }

    ProvideInfoStrings {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenTopBar(
                title = stringResource(if (editing == null) R.string.editor_add_title else R.string.editor_change_title),
                backIcon = painterResource(CommonsR.drawable.ic_arrow_back),
                backLabel = stringResource(CommonsR.string.back),
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
            ) {
                val side = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                SecondaryButton(
                    text = stringResource(R.string.editor_from_contacts),
                    onClick = {
                        try {
                            pickContact.launch(Unit)
                        } catch (_: ActivityNotFoundException) {
                            message = R.string.editor_no_contacts_app
                        }
                    },
                    modifier = side,
                )
                message?.let { Note(stringResource(it), warning = true, modifier = side) }

                val nameMissing = showProblems && EditorProblem.NoName in problems
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.editor_name)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    isError = nameMissing,
                    supportingText = if (nameMissing) {
                        { Text(stringResource(R.string.editor_name_missing), style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = side.fillMaxWidth().heightIn(min = 64.dp),
                )
                val numberMissing = showProblems && EditorProblem.NoNumber in problems
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.editor_number)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    isError = numberMissing,
                    supportingText = if (numberMissing) {
                        { Text(stringResource(R.string.editor_number_missing), style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    modifier = side.fillMaxWidth().heightIn(min = 64.dp),
                )
                if (emergency) Note(stringResource(R.string.editor_emergency), warning = false, modifier = side)
                ContactNotes(
                    name = name.trim(),
                    onSim = pickedOnSim && number.trim() == linkedNumber.trim(),
                    typed = !linkedAfterSave,
                    hasPermission = hasContactsPermission,
                    message = contactMessage,
                    onCopy = {
                        contactMessage = null
                        when {
                            EditorProblem.NoName in problems || EditorProblem.NoNumber in problems -> showProblems = true
                            hasContactsPermission -> copyToPhone()
                            else -> askContactsThen(AfterContacts.Copy)
                        }
                    },
                    modifier = side,
                )

                PhotoSection(
                    hasPhoto = hasPhoto,
                    newPhoto = newPhoto,
                    editing = editing.takeIf { !photoRemoved },
                    colorId = editingId ?: "new",
                    canUseContactPhoto = contactPhoto != null,
                    message = photoMessage,
                    onTake = {
                        photoMessage = null
                        try {
                            takePhoto.launch(PersonPhotos.cameraUri(context))
                        } catch (_: ActivityNotFoundException) {
                            photoMessage = R.string.editor_no_camera_app
                        }
                    },
                    onChoose = {
                        photoMessage = null
                        try {
                            choosePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } catch (_: ActivityNotFoundException) {
                            photoMessage = R.string.editor_no_photo_app
                        }
                    },
                    onUseContactPhoto = {
                        photoMessage = null
                        contactPhoto?.let { cropSource = Uri.fromFile(File(it)).toString() }
                    },
                    onRemove = {
                        newPhoto?.let { File(it).delete() }
                        newPhoto = null
                        photoRemoved = true
                        photoMessage = null
                    },
                    modifier = side,
                )

                ChoiceRow(
                    title = stringResource(R.string.editor_place),
                    options = choices,
                    selected = chosenPlace,
                    optionLabel = { stringResource(placeLabel(placePosition(it, peopleOnScreen))) },
                    onSelect = { chosenPlace = it; message = null },
                    cancelLabel = stringResource(CommonsR.string.cancel),
                )

                SolidButton(
                    text = stringResource(R.string.editor_save),
                    onClick = save,
                    modifier = side.fillMaxWidth(),
                )
                if (editing != null) {
                    SecondaryButton(
                        text = stringResource(R.string.editor_remove),
                        onClick = { confirmRemove = true },
                        modifier = side,
                    )
                }
            }
        }
    }

    if (savedWithoutContacts) {
        SavedWithoutContactsDialog(
            name = name.trim(),
            blocked = contactsBlocked,
            onOpenAppInfo = { openPage(context, PhoneHealth.appInfo(context)) },
            onDone = {
                savedWithoutContacts = false
                onDone()
            },
        )
    }

    if (confirmRemove && editing != null) {
        RemoveDialog(
            name = editing.name,
            onRemove = {
                confirmRemove = false
                onRemove(editing)
            },
            onKeep = { confirmRemove = false },
        )
    }
}

/**
 * The face as it will look, and the ways to change it. With no photo it says plainly that one
 * helps. The photo is a picture next to buttons that say what they do.
 */
@Composable
private fun PhotoSection(
    hasPhoto: Boolean,
    newPhoto: String?,
    editing: Person?,
    colorId: String,
    canUseContactPhoto: Boolean,
    message: Int?,
    onTake: () -> Unit,
    onChoose: () -> Unit,
    onUseContactPhoto: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.editor_photo),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        val pictureModifier = Modifier.size(160.dp)
        val photo = when {
            newPhoto != null -> rememberPhoto(newPhoto) { File(newPhoto) }
            editing != null && editing.hasPhoto -> rememberPhoto(PhotoCache.key(editing)) { PersonPhotos.file(context, editing.id) }
            else -> null
        }
        if (hasPhoto && photo != null) {
            FacePhoto(photo, pictureModifier, contentDescription = stringResource(R.string.editor_photo_label))
        } else if (!hasPhoto) {
            Silhouette(color = personColor(colorId), modifier = pictureModifier)
            Note(stringResource(R.string.editor_photo_missing), warning = false)
        }
        message?.let { Note(stringResource(it), warning = true) }
        SecondaryButton(text = stringResource(R.string.editor_photo_take), onClick = onTake)
        SecondaryButton(text = stringResource(R.string.editor_photo_choose), onClick = onChoose)
        if (canUseContactPhoto) SecondaryButton(text = stringResource(R.string.editor_photo_contact), onClick = onUseContactPhoto)
        if (hasPhoto) SecondaryButton(text = stringResource(R.string.editor_photo_remove), onClick = onRemove)
    }
}

/** What waits for the contacts question to close. */
private enum class AfterContacts { Save, Copy }

/**
 * Why the phone's contacts matter for this person. A typed person is also saved in the phone's
 * contacts; a contact on the SIM can be copied to the phone, since the SIM can't hold a ringtone.
 */
@Composable
private fun ContactNotes(
    name: String,
    onSim: Boolean,
    typed: Boolean,
    hasPermission: Boolean,
    message: Int?,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = name.ifEmpty { stringResource(R.string.editor_this_person) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            onSim -> {
                Note(stringResource(R.string.editor_sim_note, shown), warning = false)
                SecondaryButton(text = stringResource(R.string.editor_sim_copy), onClick = onCopy)
            }
            typed -> Note(
                stringResource(if (hasPermission) R.string.editor_typed_note else R.string.editor_typed_note_ask, shown),
                warning = false,
            )
        }
        message?.let { Note(stringResource(it, shown), warning = it != R.string.editor_sim_copied) }
    }
}

/** The person is saved in Dialer, but not in the phone's contacts, and what that means. */
@Composable
private fun SavedWithoutContactsDialog(name: String, blocked: Boolean, onOpenAppInfo: () -> Unit, onDone: () -> Unit) {
    Dialog(onDismissRequest = onDone) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.editor_saved_title, name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.editor_saved_no_contacts, name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (blocked) SecondaryButton(text = stringResource(R.string.open_app_info), onClick = onOpenAppInfo)
                SolidButton(text = stringResource(CommonsR.string.ok), onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** A plain question and two big buttons with verbs, as in the design system's ConfirmSheet. */
@Composable
private fun RemoveDialog(name: String, onRemove: () -> Unit, onKeep: () -> Unit) {
    Dialog(onDismissRequest = onKeep) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.editor_remove_question, name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                SolidButton(text = stringResource(R.string.editor_remove_confirm, name), onClick = onRemove, modifier = Modifier.fillMaxWidth())
                SecondaryButton(text = stringResource(R.string.editor_keep, name), onClick = onKeep)
            }
        }
    }
}

@Composable
private fun Note(text: String, warning: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun PersonEditorPreview() {
    NikoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PersonEditorScreen(
                people = listOf(Person(id = "a", name = "Mum", number = "9800000001", place = 0)),
                place = 0,
                peopleOnScreen = 4,
                onSave = {},
                onRemove = {},
                onBack = {},
            )
        }
    }
}
