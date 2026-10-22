package io.github.nikoapps.dialer.screening

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** When the last call was silenced, in the words settings show: "None yet", "Today, 2:00 PM" and so on. */
sealed interface LastSilenced {
    data object None : LastSilenced
    data class Today(val time: String) : LastSilenced
    data class Yesterday(val time: String) : LastSilenced
    data class Earlier(val date: String, val time: String) : LastSilenced
}

/**
 * The time and date patterns come from the phone, as `DateFormat.getBestDateTimePattern` gives them
 * for [TIME_SKELETON_12]/[TIME_SKELETON_24] and [DATE_SKELETON], so each language gets its own order
 * and words. Kept as plain inputs so this stays testable without Android.
 */
fun lastSilenced(at: Instant?, now: Instant, zone: ZoneId, timePattern: String, datePattern: String, locale: Locale): LastSilenced {
    if (at == null) return LastSilenced.None
    val local = at.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val time = DateTimeFormatter.ofPattern(timePattern, locale).format(local)
    return when (local.toLocalDate()) {
        today -> LastSilenced.Today(time)
        today.minusDays(1) -> LastSilenced.Yesterday(time)
        else -> LastSilenced.Earlier(DateTimeFormatter.ofPattern(datePattern, locale).format(local), time)
    }
}

/** "2:00 PM" on phones set to 12-hour time. */
const val TIME_SKELETON_12 = "hm"

/** "14:00" on phones set to 24-hour time. */
const val TIME_SKELETON_24 = "Hm"

/** "Oct 20, 2026". */
const val DATE_SKELETON = "yMMMd"
