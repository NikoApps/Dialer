package io.github.nikoapps.dialer.screening

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Patterns as `DateFormat.getBestDateTimePattern` gives them for US English. */
class LastSilencedTest {

    private val zone = ZoneId.of("Asia/Kathmandu")
    private val now = LocalDateTime.of(2026, 10, 22, 18, 30).atZone(zone).toInstant()
    private val twelveHour = "h:mm a"
    private val twentyFourHour = "HH:mm"
    private val date = "MMM d, y"

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant()

    private fun last(at: java.time.Instant?, time: String = twelveHour, zone: ZoneId = this.zone, locale: Locale = Locale.US) =
        lastSilenced(at, now, zone, time, date, locale)

    @Test
    fun `nothing silenced yet`() {
        assertEquals(LastSilenced.None, last(null))
    }

    @Test
    fun `today and yesterday use the phone's 12-hour time`() {
        assertEquals(LastSilenced.Today("2:00 PM"), last(at(22, 14)))
        assertEquals(LastSilenced.Yesterday("9:05 AM"), last(at(21, 9, 5)))
    }

    @Test
    fun `24-hour phones get 24-hour times`() {
        assertEquals(LastSilenced.Today("14:00"), last(at(22, 14), twentyFourHour))
        assertEquals(LastSilenced.Yesterday("00:15"), last(at(21, 0, 15), twentyFourHour))
    }

    @Test
    fun `older calls show the date`() {
        assertEquals(LastSilenced.Earlier("Oct 20, 2026", "11:45 PM"), last(at(20, 23, 45)))
    }

    @Test
    fun `the pattern's language gives the words`() {
        assertEquals(LastSilenced.Earlier("20 oct. 2026", "23:45"), lastSilenced(at(20, 23, 45), now, zone, "HH:mm", "d MMM y", Locale.FRENCH))
    }

    @Test
    fun `today is the day in the phone's time zone`() {
        // 00:30 on the 22nd in Kathmandu is still the 21st in UTC.
        val justAfterMidnight = at(22, 0, 30)
        assertEquals(LastSilenced.Today("00:30"), last(justAfterMidnight, twentyFourHour))
        assertEquals(LastSilenced.Yesterday("18:45"), last(justAfterMidnight, twentyFourHour, ZoneId.of("UTC")))
    }
}
