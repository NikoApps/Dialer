package io.github.nikoapps.dialer.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PlacesTest {
    private val mum = Person(id = "mum", name = "Mum", number = "1", place = 0)
    private val ram = Person(id = "ram", name = "Ram", number = "2", place = 1)
    private val sita = Person(id = "sita", name = "Sita", number = "3", place = 3)
    private val doctor = Person(id = "doctor", name = "Doctor", number = "4", place = 5)
    private val everyone = listOf(mum, ram, sita, doctor)

    private fun placesOf(people: List<Person>) = people.associate { it.id to it.place }

    @Test
    fun `grid shape depends only on the number of places`() {
        assertEquals(GridShape(columns = 1, rows = 1), gridShape(1))
        assertEquals(GridShape(columns = 1, rows = 2), gridShape(2))
        assertEquals(GridShape(columns = 2, rows = 2), gridShape(4))
        assertEquals(GridShape(columns = 2, rows = 3), gridShape(6))
    }

    @Test
    fun `an unknown grid size falls back to four`() {
        assertEquals(gridShape(4), gridShape(5))
        assertEquals(gridShape(4), gridShape(0))
    }

    @Test
    fun `places on screen has one entry per place, with empty places as null`() {
        val places = placesOnScreen(everyone, 6)
        assertEquals(listOf(mum, ram, null, sita, null, doctor), places)
    }

    @Test
    fun `the grid has the same places with nobody saved`() {
        assertEquals(List(4) { null }, placesOnScreen(emptyList(), 4))
    }

    @Test
    fun `people past the grid are kept but not shown, and come back when it grows`() {
        val small = placesOnScreen(everyone, 2)
        assertEquals(listOf(mum, ram), small)
        val four = placesOnScreen(everyone, 4)
        assertEquals(listOf(mum, ram, null, sita), four)
        assertEquals(doctor, placesOnScreen(everyone, 6)[5])
    }

    @Test
    fun `removing one person leaves everyone else where they were`() {
        val after = removePerson(everyone, ram.id)
        assertEquals(placesOf(everyone) - ram.id, placesOf(after))
        assertNull(placesOnScreen(after, 6)[1])
        assertEquals(sita, placesOnScreen(after, 6)[3])
    }

    @Test
    fun `moving one person to an empty place moves nobody else`() {
        val after = movePerson(everyone, mum.id, 2)
        assertEquals(placesOf(everyone) + (mum.id to 2), placesOf(after))
        assertNull(placesOnScreen(after, 6)[0])
    }

    @Test
    fun `moving onto someone else's place changes nothing`() {
        assertSame(everyone, movePerson(everyone, mum.id, sita.place))
    }

    @Test
    fun `moving to a place that doesn't exist changes nothing`() {
        assertSame(everyone, movePerson(everyone, mum.id, 6))
        assertSame(everyone, movePerson(everyone, mum.id, -1))
    }

    @Test
    fun `adding a person only fills a free place`() {
        val nani = Person(id = "nani", name = "Nani", number = "5", place = 2)
        val after = addPerson(everyone, nani)
        assertEquals(placesOf(everyone) + (nani.id to 2), placesOf(after))
        assertSame(everyone, addPerson(everyone, nani.copy(place = 0)))
        assertSame(everyone, addPerson(everyone, nani.copy(place = 6)))
        assertSame(everyone, addPerson(everyone, nani.copy(id = mum.id, place = 4)))
    }

    @Test
    fun `free places are the gaps, lowest first`() {
        assertEquals(listOf(2, 4), freePlaces(everyone))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), freePlaces(emptyList()))
    }

    @Test
    fun `if two people share a place, the one saved first keeps it every time`() {
        val clash = listOf(mum, ram.copy(place = 0))
        assertEquals(mum, placesOnScreen(clash, 4)[0])
        assertNull(placesOnScreen(clash, 4)[1])
    }

    @Test
    fun `people past a smaller grid are listed, lowest place first`() {
        assertEquals(listOf(sita, doctor), peopleOffScreen(everyone.reversed(), 2))
        assertEquals(listOf(doctor), peopleOffScreen(everyone, 4))
        assertEquals(emptyList<Person>(), peopleOffScreen(everyone, 6))
    }

    @Test
    fun `a place past the grid is off screen`() {
        assertEquals(false, isOffScreen(3, 4))
        assertEquals(true, isOffScreen(4, 4))
        assertEquals(true, isOffScreen(1, 1))
    }
}
