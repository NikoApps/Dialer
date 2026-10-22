package io.github.nikoapps.dialer.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleCodecTest {

    @Test
    fun `people read back exactly as saved`() {
        val people = listOf(
            Person(id = "6f1c-4e", name = "आमा", number = "+977 980-000-0001", place = 0),
            Person(id = "b2", name = "Ram; Sita, \"Didi\"", number = "*123#", place = 5),
            Person(id = "c3", name = "", number = "", place = 3),
            Person(id = "d4", name = "Didi", number = "2", place = 1, photoVersion = 7),
            Person(
                id = "e5",
                name = "Mum",
                number = "3",
                place = 2,
                photoVersion = 1,
                contactLookupKey = "3789r1-2F2D;x,y",
                contactId = 42,
                recordingVersion = 3,
                ringtoneUri = "content://media/external_primary/audio/media/77",
            ),
        )
        assertEquals(people, PeopleCodec.decode(PeopleCodec.encode(people)))
    }

    @Test
    fun `nothing saved reads as nobody`() {
        assertEquals(emptyList<Person>(), PeopleCodec.decode(null))
        assertEquals(emptyList<Person>(), PeopleCodec.decode(""))
    }

    @Test
    fun `a broken record is skipped without losing the others`() {
        val mum = Person(id = "mum", name = "Mum", number = "1", place = 0)
        val text = PeopleCodec.encode(listOf(mum)) + ",not-a-person,x;y;z;w"
        assertEquals(listOf(mum), PeopleCodec.decode(text))
    }

    @Test
    fun `people saved before photos read back with no photo`() {
        // Exactly what the first version wrote: place;id;name;number, with no photo field.
        val saved = "0;bXVt;TXVt;OTgwMDAwMDAwMQ,3;cmFt;UmFt;OTgwMDAwMDAwMg"
        val people = PeopleCodec.decode(saved)
        assertEquals(
            listOf(
                Person(id = "mum", name = "Mum", number = "9800000001", place = 0),
                Person(id = "ram", name = "Ram", number = "9800000002", place = 3),
            ),
            people,
        )
        assertFalse(people.any { it.hasPhoto })
    }

    @Test
    fun `old and new records can sit side by side`() {
        val ram = Person(id = "r", name = "Ram", number = "2", place = 1, photoVersion = 2)
        val text = "0;bXVt;TXVt;OTgwMDAwMDAwMQ," + PeopleCodec.encode(listOf(ram))
        assertEquals(listOf(0, 2), PeopleCodec.decode(text).map { it.photoVersion })
    }

    @Test
    fun `a broken photo field skips only that person`() {
        val mum = Person(id = "mum", name = "Mum", number = "1", place = 0, photoVersion = 1)
        val text = PeopleCodec.encode(listOf(mum)) + ",1;cmFt;UmFt;Mg;x"
        assertEquals(listOf(mum), PeopleCodec.decode(text))
    }

    @Test
    fun `people saved before recorded names read back with no contact and no recording`() {
        // Exactly what the photo version wrote: place;id;name;number;photoVersion.
        val saved = "0;bXVt;TXVt;OTgwMDAwMDAwMQ;2"
        val mum = PeopleCodec.decode(saved).single()
        assertEquals(Person(id = "mum", name = "Mum", number = "9800000001", place = 0, photoVersion = 2), mum)
        assertFalse(mum.hasContact)
        assertFalse(mum.hasRecording)
        assertEquals("", mum.ringtoneUri)
    }

    @Test
    fun `4, 5 and 9 field records read side by side`() {
        val didi = Person(id = "d", name = "Didi", number = "3", place = 2, contactLookupKey = "k", contactId = 9, recordingVersion = 1)
        val text = "0;bXVt;TXVt;OTgwMDAwMDAwMQ,1;cmFt;UmFt;Mg;4," + PeopleCodec.encode(listOf(didi))
        val people = PeopleCodec.decode(text)
        assertEquals(listOf("mum", "ram", "d"), people.map { it.id })
        assertEquals(listOf(0, 0, 1), people.map { it.recordingVersion })
        assertEquals(listOf(0, 4, 0), people.map { it.photoVersion })
        assertTrue(people[2].hasContact)
    }

    @Test
    fun `a new record always has 9 fields`() {
        val text = PeopleCodec.encode(listOf(Person(id = "a", name = "A", number = "1", place = 0)))
        assertEquals(9, text.split(";").size)
    }

    @Test
    fun `a broken recording field skips only that person`() {
        val mum = Person(id = "mum", name = "Mum", number = "1", place = 0, recordingVersion = 1)
        val text = PeopleCodec.encode(listOf(mum)) + ",1;cmFt;UmFt;Mg;0;;x;1;"
        assertEquals(listOf(mum), PeopleCodec.decode(text))
    }

    @Test
    fun `fields from a later version are ignored, not the person`() {
        assertEquals(listOf("mum"), PeopleCodec.decode("0;bXVt;TXVt;Mg;0;;0;0;;extra").map { it.id })
    }
}
