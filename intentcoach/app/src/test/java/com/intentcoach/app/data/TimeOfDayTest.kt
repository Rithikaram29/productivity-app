package com.intentcoach.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/** Pure hour-to-part-of-day mapping; boundaries pinned so theming is predictable. */
class TimeOfDayTest {

    @Test
    fun `morning runs 5 to 11`() {
        assertEquals(TimeOfDay.MORNING, TimeOfDayClock.at(5))
        assertEquals(TimeOfDay.MORNING, TimeOfDayClock.at(9))
        assertEquals(TimeOfDay.MORNING, TimeOfDayClock.at(11))
    }

    @Test
    fun `afternoon runs 12 to 16`() {
        assertEquals(TimeOfDay.AFTERNOON, TimeOfDayClock.at(12))
        assertEquals(TimeOfDay.AFTERNOON, TimeOfDayClock.at(16))
    }

    @Test
    fun `evening runs 17 to 20`() {
        assertEquals(TimeOfDay.EVENING, TimeOfDayClock.at(17))
        assertEquals(TimeOfDay.EVENING, TimeOfDayClock.at(20))
    }

    @Test
    fun `night wraps midnight`() {
        assertEquals(TimeOfDay.NIGHT, TimeOfDayClock.at(21))
        assertEquals(TimeOfDay.NIGHT, TimeOfDayClock.at(0))
        assertEquals(TimeOfDay.NIGHT, TimeOfDayClock.at(4))
        assertEquals(TimeOfDay.NIGHT, TimeOfDayClock.at(23))
    }

    @Test
    fun `hours outside 0-23 wrap cleanly`() {
        assertEquals(TimeOfDayClock.at(9), TimeOfDayClock.at(33))   // 33 % 24 = 9
        assertEquals(TimeOfDayClock.at(23), TimeOfDayClock.at(-1))  // -1 -> 23
    }

    @Test
    fun `now maps an instant in a zone`() {
        // 2023-11-14T22:13:20Z -> 22:00 is night in UTC.
        val now = 1_700_000_000_000L
        assertEquals(TimeOfDay.NIGHT, TimeOfDayClock.now(now, ZoneId.of("UTC")))
        // Same instant in Kolkata is 03:43 next day -> still night.
        assertEquals(TimeOfDay.NIGHT, TimeOfDayClock.now(now, ZoneId.of("Asia/Kolkata")))
    }
}
