package io.github.nikoapps.dialer.people

import io.github.nikoapps.dialer.settings.DEFAULT_PEOPLE_ON_SCREEN
import io.github.nikoapps.dialer.settings.MAX_PLACES
import io.github.nikoapps.dialer.settings.PEOPLE_ON_SCREEN_CHOICES

// Where people sit on the grid. Pure functions, so the "faces never move" rule is tested
// on the JVM: nothing here ever changes the place of anyone but the person asked about.

/** The grid's shape. Places fill row by row. */
data class GridShape(val columns: Int, val rows: Int) {
    val places: Int get() = columns * rows
}

/** 1: one tile, 2: two stacked, 4: two by two, 6: two across and three down. */
fun gridShape(peopleOnScreen: Int): GridShape = when (peopleOnScreen.takeIf { it in PEOPLE_ON_SCREEN_CHOICES } ?: DEFAULT_PEOPLE_ON_SCREEN) {
    1 -> GridShape(columns = 1, rows = 1)
    2 -> GridShape(columns = 1, rows = 2)
    4 -> GridShape(columns = 2, rows = 2)
    else -> GridShape(columns = 2, rows = 3)
}

/**
 * One entry per place on the grid, in place order: the person there, or null for an empty place.
 * People whose place is past the grid are left out. If two people somehow share a place, the
 * one stored first keeps it, so the screen is the same every time.
 */
fun placesOnScreen(people: List<Person>, peopleOnScreen: Int): List<Person?> {
    val shape = gridShape(peopleOnScreen)
    val byPlace = HashMap<Int, Person>()
    for (person in people) {
        if (person.place in 0 until shape.places) byPlace.putIfAbsent(person.place, person)
    }
    return List(shape.places) { byPlace[it] }
}

/**
 * People kept at a place past the grid, lowest place first, such as after the helper chose fewer
 * people on screen. They aren't shown or called, but a helper can still change or remove them.
 */
fun peopleOffScreen(people: List<Person>, peopleOnScreen: Int): List<Person> {
    val places = gridShape(peopleOnScreen).places
    return people.filter { it.place >= places }.sortedBy { it.place }
}

/** True when [place] is past the grid, so whoever is there isn't shown. */
fun isOffScreen(place: Int, peopleOnScreen: Int): Boolean = place >= gridShape(peopleOnScreen).places

/** Places nobody holds, lowest first, out of every place the biggest grid has. */
fun freePlaces(people: List<Person>): List<Int> {
    val taken = people.map { it.place }.toSet()
    return (0 until MAX_PLACES).filter { it !in taken }
}

/** Adds [person] at their place. Unchanged if that place is taken or doesn't exist, or the id is already used. */
fun addPerson(people: List<Person>, person: Person): List<Person> {
    if (person.place !in 0 until MAX_PLACES) return people
    if (people.any { it.place == person.place || it.id == person.id }) return people
    return people + person
}

/** Moves one person to an empty place. Nobody else moves: an occupied or missing place leaves everything as it was. */
fun movePerson(people: List<Person>, id: String, toPlace: Int): List<Person> {
    if (toPlace !in 0 until MAX_PLACES) return people
    if (people.any { it.place == toPlace && it.id != id }) return people
    return people.map { if (it.id == id) it.copy(place = toPlace) else it }
}

/** Removes one person. Their place becomes empty; nobody shifts into it. */
fun removePerson(people: List<Person>, id: String): List<Person> = people.filterNot { it.id == id }
