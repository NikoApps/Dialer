package io.github.nikoapps.dialer.people

import io.github.nikoapps.dialer.people.PlacePosition.Bottom
import io.github.nikoapps.dialer.people.PlacePosition.BottomLeft
import io.github.nikoapps.dialer.people.PlacePosition.BottomRight
import io.github.nikoapps.dialer.people.PlacePosition.MiddleLeft
import io.github.nikoapps.dialer.people.PlacePosition.MiddleRight
import io.github.nikoapps.dialer.people.PlacePosition.Only
import io.github.nikoapps.dialer.people.PlacePosition.Top
import io.github.nikoapps.dialer.people.PlacePosition.TopLeft
import io.github.nikoapps.dialer.people.PlacePosition.TopRight
import java.util.UUID

// The people editor's rules, without Android, so they're tested on the JVM.

/** Where a place sits on the grid, in words a helper can match to the screen. */
enum class PlacePosition { Only, Top, Bottom, TopLeft, TopRight, MiddleLeft, MiddleRight, BottomLeft, BottomRight }

/** The position of [place] on a grid of [peopleOnScreen] places. */
fun placePosition(place: Int, peopleOnScreen: Int): PlacePosition {
    val shape = gridShape(peopleOnScreen)
    if (shape.places == 1) return Only
    val row = place / shape.columns
    val column = place % shape.columns
    if (shape.columns == 1) return if (row == 0) Top else Bottom
    val left = column == 0
    return when (row) {
        0 -> if (left) TopLeft else TopRight
        shape.rows - 1 -> if (left) BottomLeft else BottomRight
        else -> if (left) MiddleLeft else MiddleRight
    }
}

/**
 * The places a person can be put in: the empty ones on screen, plus their own place.
 * Places past the grid aren't offered, because nobody would see the face there.
 */
fun placeChoices(people: List<Person>, editingId: String?, peopleOnScreen: Int): List<Int> {
    val onScreen = gridShape(peopleOnScreen).places
    val own = people.firstOrNull { it.id == editingId }?.place
    val free = freePlaces(people.filterNot { it.id == editingId }).filter { it < onScreen }
    return (free + listOfNotNull(own)).distinct().sorted()
}

/** What stops a person being saved. */
enum class EditorProblem { NoName, NoNumber }

/** The problems with what was typed, empty when it can be saved. */
fun editorProblems(name: String, number: String): Set<EditorProblem> = buildSet {
    if (name.isBlank()) add(EditorProblem.NoName)
    if (number.none(Char::isDigit)) add(EditorProblem.NoNumber)
}

/**
 * Saves a new person ([id] null) or changes one, at [place]. Nobody else moves: if [place] is
 * taken by someone else, the list stays as it was. Name and number are trimmed.
 */
fun savePerson(
    people: List<Person>,
    id: String?,
    name: String,
    number: String,
    place: Int,
    newId: () -> String = { UUID.randomUUID().toString() },
): List<Person> {
    val cleanName = name.trim()
    val cleanNumber = number.trim()
    if (editorProblems(cleanName, cleanNumber).isNotEmpty()) return people
    if (id == null || people.none { it.id == id }) {
        return addPerson(people, Person(id = newId(), name = cleanName, number = cleanNumber, place = place))
    }
    val moved = movePerson(people, id, place)
    if (moved.first { it.id == id }.place != place) return people
    return moved.map { if (it.id == id) it.copy(name = cleanName, number = cleanNumber) else it }
}
