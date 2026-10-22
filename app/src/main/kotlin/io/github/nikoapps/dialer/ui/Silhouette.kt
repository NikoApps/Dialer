package io.github.nikoapps.dialer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.nikoapps.dialer.people.SilhouetteColor

/**
 * A plain head-and-shoulders shape on the person's own colour, for someone without a photo.
 * Purely a picture: the name next to it carries the meaning, so it has no TalkBack label.
 */
@Composable
fun Silhouette(color: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color),
    ) {
        val side = minOf(size.width, size.height)
        val centreX = size.width / 2
        drawCircle(color = SilhouetteColor, radius = side * 0.2f, center = Offset(centreX, size.height * 0.4f))
        // Shoulders: the top of an oval that runs off the bottom edge.
        drawOval(
            color = SilhouetteColor,
            topLeft = Offset(centreX - side * 0.36f, size.height * 0.66f),
            size = Size(side * 0.72f, side * 0.6f),
        )
    }
}
