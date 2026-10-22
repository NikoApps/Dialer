package io.github.nikoapps.dialer.people

import java.util.Base64

/**
 * Turns the people list into one short string for DataStore and back. Six people don't need
 * a database or a serialisation library.
 *
 * Each person is `place;id;name;number;photoVersion;contactLookupKey;contactId;recordingVersion;ringtoneUri`,
 * with the text fields in URL-safe Base64 so any script or punctuation is safe. People are joined
 * with commas. Records that don't read back are skipped rather than losing everyone.
 *
 * Older records are shorter and still read: those saved before photos have 4 fields, those saved
 * before recorded names have 5. Missing fields read as no photo, no contact and no recording.
 */
object PeopleCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(people: List<Person>): String =
        people.joinToString(",") { p ->
            listOf(
                p.place.toString(),
                b64(p.id),
                b64(p.name),
                b64(p.number),
                p.photoVersion.toString(),
                b64(p.contactLookupKey),
                p.contactId.toString(),
                p.recordingVersion.toString(),
                b64(p.ringtoneUri),
            ).joinToString(";")
        }

    fun decode(text: String?): List<Person> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(",").mapNotNull { record ->
            val fields = record.split(";")
            // Fields a later version adds are left alone rather than losing the person.
            if (fields.size < 4) return@mapNotNull null
            runCatching {
                Person(
                    id = unb64(fields[1]),
                    name = unb64(fields[2]),
                    number = unb64(fields[3]),
                    place = fields[0].toInt(),
                    photoVersion = fields.getOrNull(4)?.toInt()?.coerceAtLeast(0) ?: 0,
                    contactLookupKey = fields.getOrNull(5)?.let(::unb64) ?: "",
                    contactId = fields.getOrNull(6)?.toLong()?.coerceAtLeast(0) ?: 0,
                    recordingVersion = fields.getOrNull(7)?.toInt()?.coerceAtLeast(0) ?: 0,
                    ringtoneUri = fields.getOrNull(8)?.let(::unb64) ?: "",
                )
            }.getOrNull()
        }
    }

    private fun b64(s: String): String = encoder.encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun unb64(s: String): String = String(decoder.decode(s), Charsets.UTF_8)
}
