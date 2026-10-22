package io.github.nikoapps.dialer.screening

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class LastSilencedTest {

    private val zone = ZoneId.of("Asia/Kathmandu")
    private val now = LocalDateTime.of(2026, 10, 22, 18, 30).atZone(zone).toInstant()

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant()

    @Test
    fun `nothing silenced yet`() {
        assertEquals(LastSilenced.None, lastSilenced(null, now, zone, is24Hour = false, Locale.US))
    }

    @Test
    fun `today and yesterday use the phone's 12-hour time`() {
        assertEquals(LastSilenced.Today("2:00 PM"), lastSilenced(at(22, 14), now, zone, is24Hour = false, Locale.US))
        assertEquals(LastSilenced.Yesterday("9:05 AM"), lastSilenced(at(21, 9, 5), now, zone, is24Hour = false, Locale.US))
    }

    @Test
    fun `24-hour phones get 24-hour times`() {
        assertEquals(LastSilenced.Today("14:00"), lastSilenced(at(22, 14), now, zone, is24Hour = true, Locale.US))
        assertEquals(LastSilenced.Yesterday("0:15"), lastSilenced(at(21, 0, 15), now, zone, is24Hour = true, Locale.US))
    }

    @Test
    fun `older calls show the date`() {
        assertEquals(
            LastSilenced.Earlier("Oct 20, 2026", "11:45 PM"),
            lastSilenced(at(20, 23, 45), now, zone, is24Hour = false, Locale.US),
        )
    }

    @Test
    fun `today is the day in the phone's time zone`() {
        // 00:30 on the 22nd in Kathmandu is still the 21st in UTC.
        val justAfterMidnight = at(22, 0, 30)
        assertEquals(LastSilenced.Today("0:30"), lastSilenced(justAfterMidnight, now, zone, is24Hour = true, Locale.US))
        assertEquals(
            LastSilenced.Yesterday("18:45"),
            lastSilenced(justAfterMidnight, now, ZoneId.of("UTC"), is24Hour = true, Locale.US),
        )
    }
}
