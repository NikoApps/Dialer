package io.github.nikoapps.dialer.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import io.github.nikoapps.commons.theme.NikoTheme
import io.github.nikoapps.commons.ui.ChoiceRow
import io.github.nikoapps.commons.ui.ProvideInfoStrings
import io.github.nikoapps.commons.ui.ScreenTopBar
import io.github.nikoapps.commons.ui.SecondaryButton
import io.github.nikoapps.commons.ui.SolidButton
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.call.CallAction
import io.github.nikoapps.dialer.call.PhoneCalls
import io.github.nikoapps.dialer.people.EditorProblem
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.PickPhoneNumber
import io.github.nikoapps.dialer.people.editorProblems
import io.github.nikoapps.dialer.people.placeChoices
import io.github.nikoapps.dialer.people.placePosition
import io.github.nikoapps.dialer.people.readPickedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.nikoapps.commons.R as CommonsR

/**
 * Adds a person at [place], or changes the person already there: name, number and place.
 * Photos and recorded names come later. This helper screen may scroll; the main screen never does.
 *
 * [onSave] gets the trimmed name and number and the chosen place, and only once they're valid.
 */
@Composable
fun PersonEditorScreen(
    people: List<Person>,
    place: Int,
    peopleOnScreen: Int,
    onSave: (id: String?, name: String, number: String, place: Int) -> Unit,
    onRemove: (Person) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
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

    val editing = people.firstOrNull { it.id == editingId }
    val choices = remember(people, editingId, peopleOnScreen) {
        (placeChoices(people, editingId, peopleOnScreen) + place).distinct().sorted()
    }
    val problems = editorProblems(name, number)
    val emergency = remember(number) { PhoneCalls.actionFor(context, number) is CallAction.Dial }

    val pickContact = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { readPickedContact(context, uri) }
            if (picked == null) {
                message = R.string.editor_contact_unreadable
            } else {
                message = null
                if (picked.name.isNotEmpty()) name = picked.name
                number = picked.number
            }
        }
    }

    val save = {
        when {
            problems.isNotEmpty() -> showProblems = true
            chosenPlace !in choices -> message = R.string.editor_place_taken
            else -> onSave(editingId, name.trim(), number.trim(), chosenPlace)
        }
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
                onSave = { _, _, _, _ -> },
                onRemove = {},
                onBack = {},
            )
        }
    }
}
