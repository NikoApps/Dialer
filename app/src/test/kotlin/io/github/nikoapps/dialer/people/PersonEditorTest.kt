package io.github.nikoapps.dialer.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonEditorTest {
    private val mum = Person(id = "m", name = "Mum", number = "9800000001", place = 0)
    private val ram = Person(id = "r", name = "Ram", number = "9800000002", place = 3)
    private val far = Person(id = "f", name = "Far", number = "9800000003", place = 5)

    @Test
    fun `place positions follow the grid`() {
        assertEquals(PlacePosition.Only, placePosition(0, 1))
        assertEquals(listOf(PlacePosition.Top, PlacePosition.Bottom), (0..1).map { placePosition(it, 2) })
        assertEquals(
            listOf(PlacePosition.TopLeft, PlacePosition.TopRight, PlacePosition.BottomLeft, PlacePosition.BottomRight),
            (0..3).map { placePosition(it, 4) },
        )
        assertEquals(
            listOf(
                PlacePosition.TopLeft, PlacePosition.TopRight,
                PlacePosition.MiddleLeft, PlacePosition.MiddleRight,
                PlacePosition.BottomLeft, PlacePosition.BottomRight,
            ),
            (0..5).map { placePosition(it, 6) },
        )
    }

    @Test
    fun `a new person can go in any empty place on screen`() {
        assertEquals(listOf(1, 2), placeChoices(listOf(mum, ram), editingId = null, peopleOnScreen = 4))
    }

    @Test
    fun `someone being changed can stay in their own place`() {
        assertEquals(listOf(1, 2, 3), placeChoices(listOf(mum, ram), editingId = "r", peopleOnScreen = 4))
    }

    @Test
    fun `places past the grid aren't offered, except the person's own`() {
        assertEquals(listOf(1, 2, 3), placeChoices(listOf(mum, far), editingId = null, peopleOnScreen = 4))
        assertEquals(listOf(1, 2, 3, 5), placeChoices(listOf(mum, far), editingId = "f", peopleOnScreen = 4))
    }

    @Test
    fun `a full grid offers nothing new`() {
        val full = (0 until 4).map { Person(id = "$it", name = "P$it", number = "98$it", place = it) }
        assertEquals(emptyList<Int>(), placeChoices(full, editingId = null, peopleOnScreen = 4))
    }

    @Test
    fun `name and number are both needed`() {
        assertEquals(setOf(EditorProblem.NoName, EditorProblem.NoNumber), editorProblems("  ", ""))
        assertEquals(setOf(EditorProblem.NoNumber), editorProblems("Mum", "+-() "))
        assertEquals(setOf(EditorProblem.NoName), editorProblems("", "112"))
        assertTrue(editorProblems("Mum", "+977 980-000").isEmpty())
    }

    @Test
    fun `saving a new person adds them at the chosen place, trimmed`() {
        val saved = savePerson(listOf(mum), id = null, name = " Didi ", number = " 9800000009 ", place = 2, newId = { "d" })
        assertEquals(listOf(mum, Person(id = "d", name = "Didi", number = "9800000009", place = 2)), saved)
    }

    @Test
    fun `changing a person keeps everyone else where they are`() {
        val saved = savePerson(listOf(mum, ram), id = "m", name = "Ama", number = "9800000010", place = 1)
        assertEquals(listOf(Person(id = "m", name = "Ama", number = "9800000010", place = 1), ram), saved)
    }

    @Test
    fun `a taken place or a missing name leaves the list as it was`() {
        val people = listOf(mum, ram)
        assertSame(people, savePerson(people, id = "m", name = "Mum", number = "98", place = 3))
        assertSame(people, savePerson(people, id = null, name = "New", number = "98", place = 0))
        assertSame(people, savePerson(people, id = null, name = " ", number = "98", place = 1))
        assertSame(people, savePerson(people, id = "m", name = "Mum", number = "", place = 0))
    }
}
