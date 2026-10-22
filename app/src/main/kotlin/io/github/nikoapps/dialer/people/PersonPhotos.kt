package io.github.nikoapps.dialer.people

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

/**
 * Where faces are kept and how they're made. Each person's photo is a 512 pixel square JPEG in
 * Dialer's own files, named after their id. Nothing is ever written to the phone's contacts.
 *
 * Everything here reads or writes files, so call it off the main thread.
 */
object PersonPhotos {
    /** Long side of a photo while it's being cropped. Plenty for a 512 pixel face, and small enough to hold in memory. */
    private const val WORKING_SIZE = 1600
    private const val JPEG_QUALITY = 90

    fun file(context: Context, personId: String): File = File(File(context.filesDir, "photos"), photoFileName(personId))

    /** Copies a cropped photo into place. Written to a temporary file first, so a half-written photo is never shown. */
    fun save(context: Context, personId: String, cropped: File): Boolean = runCatching {
        val target = file(context, personId)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        cropped.inputStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
        if (!temp.renameTo(target)) {
            temp.delete()
            return@runCatching false
        }
        true
    }.getOrDefault(false)

    fun delete(context: Context, personId: String) {
        file(context, personId).delete()
    }

    /** Photos on their way in: the camera's picture, a contact's photo, crops not saved yet. The phone may clear them. */
    private fun workDir(context: Context): File = File(context.cacheDir, "photos").apply { mkdirs() }

    /** The camera app's picture, in a folder of its own: the only one Dialer's file provider shares. */
    private fun cameraFile(context: Context): File = File(File(context.cacheDir, "camera").apply { mkdirs() }, "photo.jpg")

    /** Where the camera app puts its picture, writable by it only while it takes that one picture. */
    fun cameraUri(context: Context): Uri {
        val file = cameraFile(context)
        file.delete()
        return FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
    }

    /** The camera's picture, as a plain file uri for opening it again. */
    fun cameraPicture(context: Context): Uri = Uri.fromFile(cameraFile(context))

    /**
     * Copies a contact's photo while the contacts app still allows reading it. Returns the copy,
     * or null when the contact has no photo or it can't be read.
     */
    fun copyContactPhoto(context: Context, photoUris: List<String>): File? {
        val target = File(workDir(context), "contact.jpg")
        for (uri in photoUris) {
            val copied = runCatching {
                context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                } != null
            }.getOrDefault(false)
            if (copied && target.length() > 0 && readSize(context, Uri.fromFile(target)) != null) return target
        }
        target.delete()
        return null
    }

    /**
     * Opens a photo for cropping: shrunk so its long side is at most about [WORKING_SIZE] pixels
     * and turned upright from its EXIF orientation. Null if it isn't a photo Android can read.
     */
    fun openForCrop(context: Context, uri: Uri): Bitmap? = runCatching {
        val (width, height) = readSize(context, uri) ?: return@runCatching null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(width, height, WORKING_SIZE) }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@runCatching null
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        turnUpright(bitmap, uprightFor(orientation))
    }.getOrNull()

    /** Draws [crop] of [photo] into a 512 pixel square and writes it as a new JPEG in the cache. Null if it couldn't be written. */
    fun writeCrop(context: Context, photo: Bitmap, crop: CropSquare): File? = runCatching {
        val size = PhotoCrop.OUTPUT_SIZE
        val out = createBitmap(size, size)
        val scale = size / crop.side
        val matrix = Matrix().apply {
            setTranslate(-crop.left, -crop.top)
            postScale(scale, scale)
        }
        Canvas(out).apply {
            // Plain white where the square reaches past the photo; JPEG has no see-through.
            drawColor(Color.WHITE)
            drawBitmap(photo, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        val file = File(workDir(context), "crop-${UUID.randomUUID()}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        out.recycle()
        file
    }.getOrNull()

    /** A saved face or a crop, ready to show. */
    fun load(file: File): Bitmap? = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()

    private fun readSize(context: Context, uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options); true } ?: return null
        return (options.outWidth to options.outHeight).takeIf { it.first > 0 && it.second > 0 }
    }

    private fun turnUpright(bitmap: Bitmap, upright: Upright): Bitmap {
        if (upright.degrees == 0 && !upright.mirror) return bitmap
        val matrix = Matrix().apply {
            setRotate(upright.degrees.toFloat())
            if (upright.mirror) postScale(-1f, 1f)
        }
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }
}
