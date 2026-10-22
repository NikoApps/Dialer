package io.github.nikoapps.dialer.ui

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.nikoapps.dialer.people.Person
import io.github.nikoapps.dialer.people.PersonPhotos
import io.github.nikoapps.dialer.people.personColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Faces already read from their files, so the grid and the calling screen show them at once.
 * Each one is 512 pixels square, about 1MB, so a dozen covers every face on screen and a few more.
 */
object PhotoCache {
    private val cache = LruCache<String, ImageBitmap>(12)

    fun key(person: Person): String = "${person.id}#${person.photoVersion}"

    operator fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, photo: ImageBitmap) {
        cache.put(key, photo)
    }

    /** Forgets every version of one person's face, after it's changed or removed. */
    fun forget(personId: String) {
        cache.snapshot().keys.filter { it.startsWith("$personId#") }.forEach(cache::remove)
    }
}

/** A photo file read off the main thread, or null while it's loading or if it can't be read. Kept in [PhotoCache] under [key]. */
@Composable
fun rememberPhoto(key: String, file: () -> File): ImageBitmap? {
    val photo by produceState(PhotoCache[key], key) {
        value = PhotoCache[key] ?: withContext(Dispatchers.IO) {
            PersonPhotos.load(file())?.asImageBitmap()?.also { PhotoCache.put(key, it) }
        }
    }
    return photo
}

/**
 * The person's face: their photo, or the silhouette on their own colour when they have none or
 * it can't be read. While a photo loads the space stays empty, so nothing jumps.
 * Purely a picture: the name next to it carries the meaning, so it has no TalkBack label.
 */
@Composable
fun PersonPicture(person: Person, modifier: Modifier = Modifier) {
    if (!person.hasPhoto) {
        Silhouette(color = personColor(person.id), modifier = modifier)
        return
    }
    val context = LocalContext.current.applicationContext
    val key = PhotoCache.key(person)
    val loaded by produceState<PhotoLoad>(PhotoCache[key]?.let(PhotoLoad::Ready) ?: PhotoLoad.Loading, key) {
        value = PhotoCache[key]?.let(PhotoLoad::Ready) ?: withContext(Dispatchers.IO) {
            PersonPhotos.load(PersonPhotos.file(context, person.id))?.asImageBitmap()
                ?.also { PhotoCache.put(key, it) }
                ?.let(PhotoLoad::Ready)
                ?: PhotoLoad.Missing
        }
    }
    when (val state = loaded) {
        is PhotoLoad.Ready -> FacePhoto(state.photo, modifier)
        PhotoLoad.Loading -> Box(modifier)
        PhotoLoad.Missing -> Silhouette(color = personColor(person.id), modifier = modifier)
    }
}

/** A square face photo with the same rounded corners as the silhouette. */
@Composable
fun FacePhoto(photo: ImageBitmap, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        bitmap = photo,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
    )
}

private sealed interface PhotoLoad {
    data object Loading : PhotoLoad
    data object Missing : PhotoLoad
    data class Ready(val photo: ImageBitmap) : PhotoLoad
}
