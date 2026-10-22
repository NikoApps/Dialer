package io.github.nikoapps.dialer.ui

import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.nikoapps.commons.ui.ScreenTopBar
import io.github.nikoapps.commons.ui.SecondaryButton
import io.github.nikoapps.commons.ui.SolidButton
import io.github.nikoapps.dialer.R
import io.github.nikoapps.dialer.people.CropSquare
import io.github.nikoapps.dialer.people.PersonPhotos
import io.github.nikoapps.dialer.people.PhotoCrop
import io.github.nikoapps.dialer.people.PhotoSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import io.github.nikoapps.commons.R as CommonsR

/**
 * Crops a photo to the square face. It starts as the biggest square in the middle; the helper
 * drags and pinches, or uses the buttons, which do the same, and Fit whole photo shows all of it.
 *
 * [onCropped] gets the 512 pixel JPEG in the cache. [onUnreadable] is called when [source] isn't
 * a photo that can be opened.
 */
@Composable
fun PhotoCropScreen(source: String, onCropped: (File) -> Unit, onCancel: () -> Unit, onUnreadable: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onCancel)

    val photo by produceState<Bitmap?>(null, source) {
        value = withContext(Dispatchers.IO) { PersonPhotos.openForCrop(context, source.toUri()) }
        if (value == null) onUnreadable()
    }
    // Kept through turning the phone, so the helper's crop isn't lost.
    var crop by rememberSaveable(source, stateSaver = CropSaver) { mutableStateOf<CropSquare?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    val bitmap = photo
    val size = bitmap?.let { PhotoSize(it.width, it.height) }
    LaunchedEffect(size) {
        if (size != null && crop == null) crop = PhotoCrop.centred(size)
    }
    val update = { change: (CropSquare, PhotoSize) -> CropSquare ->
        val current = crop
        if (current != null && size != null) crop = change(current, size)
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenTopBar(
            title = stringResource(R.string.crop_title),
            backIcon = painterResource(CommonsR.drawable.ic_arrow_back),
            backLabel = stringResource(CommonsR.string.cancel),
            onBack = onCancel,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val image = remember(bitmap) { bitmap?.asImageBitmap() }
            val square = crop
            if (bitmap == null || image == null || square == null || size == null) {
                Text(
                    text = stringResource(R.string.crop_opening),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                )
                SecondaryButton(text = stringResource(CommonsR.string.cancel), onClick = onCancel)
                return@Column
            }

            CropView(
                image = image,
                crop = square,
                onDrag = { dx, dy -> update { c, s -> PhotoCrop.move(c, s, dx, dy) } },
                onPinch = { factor, x, y -> update { c, s -> PhotoCrop.zoom(c, s, factor, x, y) } },
                label = stringResource(R.string.crop_preview),
            )
            Text(
                text = stringResource(R.string.crop_help),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
            ButtonPair(
                R.string.crop_zoom_out to { update { c, s -> PhotoCrop.zoom(c, s, 1 / PhotoCrop.ZOOM_STEP) } },
                R.string.crop_zoom_in to { update { c, s -> PhotoCrop.zoom(c, s, PhotoCrop.ZOOM_STEP) } },
            )
            ButtonPair(
                R.string.crop_move_left to { update { c, s -> PhotoCrop.move(c, s, -PhotoCrop.MOVE_STEP, 0f) } },
                R.string.crop_move_right to { update { c, s -> PhotoCrop.move(c, s, PhotoCrop.MOVE_STEP, 0f) } },
            )
            ButtonPair(
                R.string.crop_move_up to { update { c, s -> PhotoCrop.move(c, s, 0f, -PhotoCrop.MOVE_STEP) } },
                R.string.crop_move_down to { update { c, s -> PhotoCrop.move(c, s, 0f, PhotoCrop.MOVE_STEP) } },
            )
            SecondaryButton(text = stringResource(R.string.crop_fit_whole), onClick = { crop = PhotoCrop.fitWhole(size) })

            if (saveFailed) {
                Text(
                    text = stringResource(R.string.crop_save_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            SolidButton(
                text = stringResource(R.string.crop_use),
                onClick = {
                    if (!saving) {
                        saving = true
                        scope.launch {
                            val file = withContext(Dispatchers.IO) { PersonPhotos.writeCrop(context, bitmap, square) }
                            saving = false
                            saveFailed = file == null
                            if (file != null) onCropped(file)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(text = stringResource(CommonsR.string.cancel), onClick = onCancel)
        }
    }
}

/**
 * The photo inside the square, exactly as it will be saved. One finger drags it, two pinch it.
 * TalkBack reads it as a picture; the buttons under it do the same things.
 */
@Composable
private fun CropView(
    image: ImageBitmap,
    crop: CropSquare,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onPinch: (factor: Float, focusX: Float, focusY: Float) -> Unit,
    label: String,
) {
    val shape = RoundedCornerShape(16.dp)
    val drag by rememberUpdatedState(onDrag)
    val pinch by rememberUpdatedState(onPinch)
    Canvas(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), shape)
            .clip(shape)
            // Matches the plain white the saved photo gets where the square reaches past the photo.
            .background(Color.White)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val side = size.width.toFloat()
                    if (side <= 0f) return@detectTransformGestures
                    if (pan.x != 0f || pan.y != 0f) drag(pan.x / side, pan.y / side)
                    if (zoom != 1f) pinch(zoom, centroid.x / side, centroid.y / side)
                }
            },
    ) {
        val scale = size.width / crop.side
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset((-crop.left * scale).roundToInt(), (-crop.top * scale).roundToInt()),
            dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** Two buttons side by side, each at least 64dp tall, labelled in words. */
@Composable
private fun ButtonPair(first: Pair<Int, () -> Unit>, second: Pair<Int, () -> Unit>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SecondaryButton(text = stringResource(first.first), onClick = first.second, modifier = Modifier.weight(1f))
        SecondaryButton(text = stringResource(second.first), onClick = second.second, modifier = Modifier.weight(1f))
    }
}

private val CropSaver = listSaver<CropSquare?, Float>(
    save = { crop -> crop?.let { listOf(it.left, it.top, it.side) } ?: emptyList() },
    restore = { values -> if (values.size == 3) CropSquare(values[0], values[1], values[2]) else null },
)
