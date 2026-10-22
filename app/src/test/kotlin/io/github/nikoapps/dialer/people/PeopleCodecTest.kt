package io.github.nikoapps.dialer.people

import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleCodecTest {

    @Test
    fun `people read back exactly as saved`() {
        val people = listOf(
            Person(id = "6f1c-4e", name = "आमा", number = "+977 980-000-0001", place = 0),
            Person(id = "b2", name = "Ram; Sita, \"Didi\"", number = "*123#", place = 5),
            Person(id = "c3", name = "", number = "", place = 3),
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
}
