package io.github.nikoapps.dialer.people

import androidx.compose.ui.graphics.Color
import io.github.nikoapps.commons.theme.NikoColors

/**
 * Background colours for a person without a photo, from the Niko app colours. All are light,
 * so the silhouette is always drawn in [SilhouetteColor] on top, in light and dark theme.
 * The Light app's yellow is left out because it sits too close to Clock's.
 */
val PersonPalette: List<Color> = listOf(
    NikoColors.Apps.Dialer,
    NikoColors.Apps.Status,
    NikoColors.Apps.Reminders,
    NikoColors.Apps.Clock,
    NikoColors.Apps.DayClock,
    NikoColors.Apps.Flash,
)

val SilhouetteColor: Color = NikoColors.Ink

/** The same person always gets the same colour, wherever they sit on the grid. */
fun personColorIndex(id: String, paletteSize: Int = PersonPalette.size): Int = Math.floorMod(id.hashCode(), paletteSize)

fun personColor(id: String): Color = PersonPalette[personColorIndex(id)]
