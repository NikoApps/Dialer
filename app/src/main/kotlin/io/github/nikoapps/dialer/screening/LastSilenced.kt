package io.github.nikoapps.dialer.screening

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** When the last call was silenced, in the words settings show: "None yet", "Today, 2:00 PM" and so on. */
sealed interface LastSilenced {
    data object None : LastSilenced
    data class Today(val time: String) : LastSilenced
    data class Yesterday(val time: String) : LastSilenced
    data class Earlier(val date: String, val time: String) : LastSilenced
}

/** [is24Hour] follows the phone's time format, as `DateFormat.is24HourFormat` reports it. */
fun lastSilenced(at: Instant?, now: Instant, zone: ZoneId, is24Hour: Boolean, locale: Locale): LastSilenced {
    if (at == null) return LastSilenced.None
    val local = at.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val time = clockTime(local.toLocalTime(), is24Hour, locale)
    return when (local.toLocalDate()) {
        today -> LastSilenced.Today(time)
        today.minusDays(1) -> LastSilenced.Yesterday(time)
        else -> LastSilenced.Earlier(shortDate(local.toLocalDate(), locale), time)
    }
}

/** "2:00 PM" or "14:00", as the phone is set. */
fun clockTime(time: LocalTime, is24Hour: Boolean, locale: Locale): String =
    DateTimeFormatter.ofPattern(if (is24Hour) "H:mm" else "h:mm a", locale).format(time)

private fun shortDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
