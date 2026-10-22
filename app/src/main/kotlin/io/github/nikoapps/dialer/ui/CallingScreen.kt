package io.github.nikoapps.dialer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nikoapps.commons.theme.NikoTheme
import io.github.nikoapps.commons.ui.BigButton
import io.github.nikoapps.commons.ui.SecondaryButton
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.personColor

/**
 * Shown between the tap on a face and the phone's own call screen: the face, the name, and
 * Stop at the bottom. With [askFirst] a big Call button sits above Stop instead of the wait.
 * Nothing on it moves or counts down.
 */
@Composable
fun CallingScreen(person: Person, askFirst: Boolean, onCall: () -> Unit, onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Silhouette(
                color = personColor(person.id),
                modifier = Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true),
            )
        }
        BasicText(
            text = stringResource(R.string.calling_name, person.name),
            style = MaterialTheme.typography.headlineLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = MaterialTheme.typography.headlineLarge.fontSize),
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        if (askFirst) {
            BigButton(text = stringResource(R.string.call_name, person.name), onClick = onCall)
            // As big as the Call button, so it's just as easy to hit.
            SecondaryButton(text = stringResource(R.string.stop), onClick = onStop, modifier = Modifier.heightIn(min = 96.dp))
        } else {
            BigButton(text = stringResource(R.string.stop), onClick = onStop)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun CallingScreenPreview() {
    NikoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CallingScreen(Person(id = "a", name = "Mum", number = "9800000001", place = 0), askFirst = false, onCall = {}, onStop = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun CallingScreenAskFirstPreview() {
    NikoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CallingScreen(Person(id = "a", name = "Mum", number = "9800000001", place = 0), askFirst = true, onCall = {}, onStop = {})
        }
    }
}
