package io.github.nikoapps.dialer.recording

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import java.io.File

/**
 * Ringtone copies of recorded names in the phone's shared Ringtones folder, where the system
 * ringer can read them; it can't read Dialer's own files. They show in the phone's ringtone list
 * and stay if Dialer is uninstalled.
 *
 * Android 10 and newer: added through MediaStore under Ringtones/, which needs no permission, and
 * Dialer can only see and delete the ones it added. Android 8 and 9: written to the public
 * Ringtones folder, which needs the storage permission, then added to MediaStore.
 *
 * Everything here reads or writes files, so call it off the main thread. Nothing throws.
 */
object RingtoneStore {
    private const val MIME = "audio/x-wav"

    /** Copies [master] into Ringtones as [title]. Returns the new item's uri, or null if it couldn't. */
    fun add(context: Context, master: File, title: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) addShared(context, master, title) else addLegacy(context, master, title)
    }.getOrNull()

    /**
     * True when [uri] is still a ringtone item Dialer added. From Android 10 MediaStore only shows
     * Dialer its own items, so another app's ringtone never counts. On Android 8 and 9 the file
     * must be in the Ringtones folder and named like Dialer's.
     */
    fun exists(context: Context, uri: String): Boolean = runCatching {
        if (uri.isBlank()) return@runCatching false
        context.contentResolver.query(uri.toUri(), arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, DATA), null, null, null)?.use { c ->
            c.moveToFirst() && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || isLegacyOurs(c.getString(1)))
        } ?: false
    }.getOrDefault(false)

    /** Deletes a ringtone item Dialer added. Anything else is left alone. */
    fun delete(context: Context, uri: String): Boolean = runCatching {
        if (!exists(context, uri)) return@runCatching false
        val path = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) dataPath(context, uri.toUri()) else null
        val deleted = context.contentResolver.delete(uri.toUri(), null, null) > 0
        path?.let { File(it).delete() }
        deleted
    }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun addShared(context: Context, master: File, title: String): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = baseValues(title).apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES + "/")
            // Hidden from the ringtone list until the whole file is written.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { out -> master.inputStream().use { it.copyTo(out) } } != null
        }.getOrDefault(false)
        if (!written) {
            resolver.delete(uri, null, null)
            return null
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri.toString()
    }

    @Suppress("DEPRECATION")
    private fun addLegacy(context: Context, master: File, title: String): String? {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val file = freeFile(dir, ringtoneFileName(title))
        master.copyTo(file)
        val values = baseValues(title).apply {
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
            put(MediaStore.MediaColumns.SIZE, file.length())
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            file.delete()
            return null
        }
        return uri.toString()
    }

    private fun baseValues(title: String) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, ringtoneFileName(title))
        put(MediaStore.MediaColumns.TITLE, title)
        put(MediaStore.MediaColumns.MIME_TYPE, MIME)
        put(MediaStore.Audio.AudioColumns.IS_RINGTONE, 1)
        put(MediaStore.Audio.AudioColumns.IS_NOTIFICATION, 0)
        put(MediaStore.Audio.AudioColumns.IS_ALARM, 0)
        put(MediaStore.Audio.AudioColumns.IS_MUSIC, 0)
    }

    /** "Mum is calling - Niko Dialer.wav", or with " (2)" and up when that's taken. */
    private fun freeFile(dir: File, name: String): File {
        val stem = name.removeSuffix(".wav")
        var file = File(dir, name)
        var n = 2
        while (file.exists()) file = File(dir, "$stem ($n).wav").also { n++ }
        return file
    }

    @Suppress("DEPRECATION")
    private val DATA = MediaStore.MediaColumns.DATA

    private fun dataPath(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(DATA), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** Android 8 and 9: a file Dialer wrote is a WAV straight in the public Ringtones folder. */
    @Suppress("DEPRECATION")
    private fun isLegacyOurs(path: String?): Boolean {
        if (path == null) return false
        val file = File(path)
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
        return file.parentFile?.absolutePath == dir.absolutePath && file.name.endsWith(".wav")
    }
}
