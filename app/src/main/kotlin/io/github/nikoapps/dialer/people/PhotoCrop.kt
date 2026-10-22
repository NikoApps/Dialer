package io.github.nikoapps.dialer.people

import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

// The photo rules, without Android, so they're tested on the JVM: the crop square, how far a
// photo is shrunk while it's opened, how it's turned upright, and where it's kept.

/** A photo's size in pixels, after it has been turned upright. */
data class PhotoSize(val width: Int, val height: Int) {
    val shortSide: Int get() = min(width, height)
    val longSide: Int get() = max(width, height)
}

/**
 * The square of the photo that becomes the face, in the photo's own pixels. When zoomed out
 * past the photo's short side it reaches over the edges, and that part is filled plain.
 */
data class CropSquare(val left: Float, val top: Float, val side: Float)

object PhotoCrop {
    /** Saved photos are this many pixels square. */
    const val OUTPUT_SIZE = 512

    /** How far the zoom buttons zoom in one press. */
    const val ZOOM_STEP = 1.25f

    /** How far the move buttons move the photo in one press, as a share of the square. */
    const val MOVE_STEP = 0.1f

    /** The default: the biggest square that fits, in the middle of the photo. */
    fun centred(size: PhotoSize): CropSquare {
        val side = size.shortSide.toFloat()
        return CropSquare(left = (size.width - side) / 2f, top = (size.height - side) / 2f, side = side)
    }

    /** The whole photo inside the square, in the middle, with plain space beside it. */
    fun fitWhole(size: PhotoSize): CropSquare {
        val side = size.longSide.toFloat()
        return CropSquare(left = (size.width - side) / 2f, top = (size.height - side) / 2f, side = side)
    }

    /** Zoomed in as far as it goes: an eighth of the short side, but never under 64 pixels unless the photo is that small. */
    fun minSide(size: PhotoSize): Float = min(size.shortSide.toFloat(), max(size.shortSide / 8f, 64f))

    /** Zoomed out as far as it goes: the whole photo. */
    fun maxSide(size: PhotoSize): Float = size.longSide.toFloat()

    /**
     * Keeps the square on the photo. Along a side where the square is smaller than the photo it
     * can't move past the edge; along a side where it's bigger, the photo sits in the middle.
     */
    fun clamp(crop: CropSquare, size: PhotoSize): CropSquare {
        val side = crop.side.coerceIn(minSide(size), maxSide(size))
        return CropSquare(left = axis(crop.left, side, size.width), top = axis(crop.top, side, size.height), side = side)
    }

    /**
     * Moves the photo by a share of the square, as a finger drags it: [dx] 0.1 moves the photo a
     * tenth of the square to the right, so the square shows more of the photo's left.
     */
    fun move(crop: CropSquare, size: PhotoSize, dx: Float, dy: Float): CropSquare =
        clamp(crop.copy(left = crop.left - dx * crop.side, top = crop.top - dy * crop.side), size)

    /**
     * Zooms in by [factor] (below 1 zooms out), keeping the point at [focusX], [focusY] in the same
     * place on screen. The focus is a share of the square: 0.5, 0.5 is its middle.
     */
    fun zoom(crop: CropSquare, size: PhotoSize, factor: Float, focusX: Float = 0.5f, focusY: Float = 0.5f): CropSquare {
        if (factor <= 0f || factor.isNaN()) return clamp(crop, size)
        val side = (crop.side / factor).coerceIn(minSide(size), maxSide(size))
        val pointX = crop.left + focusX * crop.side
        val pointY = crop.top + focusY * crop.side
        return clamp(CropSquare(left = pointX - focusX * side, top = pointY - focusY * side, side = side), size)
    }

    private fun axis(offset: Float, side: Float, length: Int): Float =
        if (side >= length) (length - side) / 2f else offset.coerceIn(0f, length - side)
}

/**
 * How much to shrink a photo while it's opened, as a power of two, so its long side ends up
 * between [maxSide] and twice that. Phone photos are often 4000 pixels or more across.
 */
fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    val long = max(width, height)
    if (maxSide <= 0) return sample
    while (long / (sample * 2) >= maxSide) sample *= 2
    return sample
}

/** How to turn a photo upright: rotate clockwise by [degrees], then mirror left to right if [mirror]. */
data class Upright(val degrees: Int, val mirror: Boolean) {
    val swapsSides: Boolean get() = degrees == 90 || degrees == 270
}

/** The turn for an EXIF orientation value (1 to 8). Anything else is taken as already upright. */
fun uprightFor(exifOrientation: Int): Upright = when (exifOrientation) {
    2 -> Upright(0, mirror = true)
    3 -> Upright(180, mirror = false)
    4 -> Upright(180, mirror = true)
    5 -> Upright(90, mirror = true)
    6 -> Upright(90, mirror = false)
    7 -> Upright(270, mirror = true)
    8 -> Upright(270, mirror = false)
    else -> Upright(0, mirror = false)
}

private val safeId = Regex("[A-Za-z0-9_-]{1,64}")

/**
 * The file name for a person's photo. Ids are UUIDs, used as they are. Anything else is turned
 * into a hash, so no id can reach outside the photos folder.
 */
fun photoFileName(personId: String): String = personFileStem(personId) + ".jpg"

/** A person's id as a safe file name without extension, for their photo and their recording. */
fun personFileStem(personId: String): String {
    if (safeId.matches(personId)) return personId
    val digest = MessageDigest.getInstance("SHA-256").digest(personId.toByteArray(Charsets.UTF_8))
    return "id_" + digest.take(16).joinToString("") { "%02x".format(it) }
}
