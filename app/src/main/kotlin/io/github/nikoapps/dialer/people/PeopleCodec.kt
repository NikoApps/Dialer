package io.github.nikoapps.dialer.people

import java.util.Base64

/**
 * Turns the people list into one short string for DataStore and back. Six people don't need
 * a database or a serialisation library.
 *
 * Each person is `place;id;name;number`, with the three text fields in URL-safe Base64 so any
 * script or punctuation in a name is safe. People are joined with commas. Records that don't
 * read back are skipped rather than losing everyone.
 */
object PeopleCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(people: List<Person>): String =
        people.joinToString(",") { p -> listOf(p.place.toString(), b64(p.id), b64(p.name), b64(p.number)).joinToString(";") }

    fun decode(text: String?): List<Person> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(",").mapNotNull { record ->
            val fields = record.split(";")
            if (fields.size != 4) return@mapNotNull null
            runCatching {
                Person(
                    id = unb64(fields[1]),
                    name = unb64(fields[2]),
                    number = unb64(fields[3]),
                    place = fields[0].toInt(),
                )
            }.getOrNull()
        }
    }

    private fun b64(s: String): String = encoder.encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun unb64(s: String): String = String(decoder.decode(s), Charsets.UTF_8)
}
