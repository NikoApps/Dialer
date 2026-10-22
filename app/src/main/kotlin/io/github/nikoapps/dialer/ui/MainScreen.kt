package io.github.nikoapps.dialer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nikoapps.commons.theme.NikoTheme
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.gridShape
import io.github.nikoapps.dialer.people.placesOnScreen

/**
 * The main screen: title bar, the fixed photo grid, and the hint line.
 *
 * Every part has a height that depends only on the font size, never on what's saved or what the
 * [hint] says, so faces stay exactly where they are. [places] has one entry per place; null is an
 * empty place.
 */
@Composable
fun MainScreen(
    places: List<Person?>,
    columns: Int,
    hint: String,
    onCall: (Person) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        TitleBar(onOpenSettings = onOpenSettings)
        PhotoGrid(
            places = places,
            columns = columns,
            onCall = onCall,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        HintLine(
            text = hint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

/**
 * App name centred, settings at the top right. The settings button takes at most a third of the
 * width and the name gets the middle, so the name stays centred. Both shrink their text rather
 * than wrap at large font sizes.
 */
@Composable
private fun TitleBar(onOpenSettings: () -> Unit) {
    Layout(
        content = {
            BasicText(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
                modifier = Modifier.semantics { heading() },
            )
            SettingsButton(onClick = onOpenSettings)
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val button = measurables[1].measure(Constraints(maxWidth = width / 3, maxHeight = constraints.maxHeight))
        val titleWidth = (width - 2 * button.width).coerceAtLeast(0)
        val title = measurables[0].measure(Constraints.fixedWidth(titleWidth))
        val height = maxOf(button.height, title.height)
        layout(width, height) {
            title.place(button.width, (height - title.height) / 2)
            button.place(width - button.width, (height - button.height) / 2)
        }
    }
}

/** Gear with "Settings" under it. One touch target, at least 64dp, read once by TalkBack. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(28.dp),
        )
        BasicText(
            text = stringResource(R.string.settings_open),
            style = SettingsLabelStyle.copy(color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = SettingsLabelStyle.fontSize),
        )
    }
}

private val SettingsLabelStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

/** The grid's shape comes only from the number of places, never from how many people are saved. */
@Composable
private fun PhotoGrid(places: List<Person?>, columns: Int, onCall: (Person) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        places.chunked(columns).forEach { row ->
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { person ->
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    // An empty place is plain space: not focusable, skipped by TalkBack.
                    if (person == null) Spacer(cell) else PersonTile(person = person, onClick = { onCall(person) }, modifier = cell)
                }
            }
        }
    }
}

/** The whole tile is one button: the face, and the name under it on one line. TalkBack says "Call Mum". */
@Composable
private fun PersonTile(person: Person, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.call_name, person.name)
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier
            .sizeIn(minWidth = 96.dp, minHeight = 96.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick { onClick(); true }
            },
    ) {
        BoxWithConstraints(modifier = Modifier.padding(10.dp)) {
            // The name gets up to 40% of the tile and shrinks to fit; the face takes the rest.
            val nameMaxHeight = maxHeight * 0.4f
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PersonPicture(
                        person = person,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true),
                    )
                }
                BasicText(
                    text = person.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .heightIn(max = nameMaxHeight),
                )
            }
        }
    }
}

/**
 * How to use the screen, or what will stop a call. Always two lines tall (capped at 88dp), whatever
 * it says, so the grid above never moves. The text shrinks to fit. TalkBack reads it when it changes.
 */
@Composable
private fun HintLine(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    val height = with(LocalDensity.current) { (style.lineHeight * 2).toDp() }.coerceAtMost(88.dp)
    Box(modifier = modifier.height(height), contentAlignment = Alignment.Center) {
        BasicText(
            text = text,
            style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center),
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize),
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun MainScreenPreview() {
    val people = listOf(
        Person(id = "a", name = "Mum", number = "9800000001", place = 0),
        Person(id = "b", name = "Ram", number = "9800000002", place = 1),
        Person(id = "c", name = "Sita Didi", number = "9800000003", place = 3),
        Person(id = "d", name = "Doctor", number = "9800000004", place = 4),
    )
    NikoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MainScreen(
                places = placesOnScreen(people, 6),
                columns = gridShape(6).columns,
                hint = stringResource(R.string.hint_ready),
                onCall = {},
                onOpenSettings = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun MainScreenLargeTextPreview() = MainScreenPreview()
