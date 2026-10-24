package io.github.nikoapps.dialer.people

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import io.github.nikoapps.commons.files.copyIntoPlace
import io.github.nikoapps.commons.photo.Photos
import java.io.File

/**
 * Where faces are kept. Each person's photo is a 512 pixel square JPEG in Dialer's own files,
 * named after their id. Nothing is ever written to the phone's contacts. Taking, opening and
 * cropping a photo is in Commons' [Photos].
 *
 * Everything here reads or writes files, so call it off the main thread.
 */
object PersonPhotos {
    fun file(context: Context, personId: String): File = File(File(context.filesDir, "photos"), photoFileName(personId))

    /** Copies a cropped photo into place. Written to a temporary file first, so a half-written photo is never shown. */
    fun save(context: Context, personId: String, cropped: File): Boolean = copyIntoPlace(cropped, file(context, personId))

    fun delete(context: Context, personId: String) {
        file(context, personId).delete()
    }

    /**
     * Copies a contact's photo while the contacts app still allows reading it. Returns the copy,
     * or null when the contact has no photo or it can't be read.
     */
    fun copyContactPhoto(context: Context, photoUris: List<String>): File? {
        val target = Photos.workFile(context, "contact.jpg")
        for (uri in photoUris) {
            val copied = runCatching {
                context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                } != null
            }.getOrDefault(false)
            if (copied && target.length() > 0 && Photos.isReadable(context, Uri.fromFile(target))) return target
        }
        target.delete()
        return null
    }
}
