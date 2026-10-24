package io.github.nikoapps.dialer.people

import io.github.nikoapps.commons.files.safeFileStem

/**
 * The file name for a person's photo. Ids are UUIDs, used as they are. Anything else is turned
 * into a hash, so no id can reach outside the photos folder.
 */
fun photoFileName(personId: String): String = safeFileStem(personId) + ".jpg"
