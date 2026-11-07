package com.intentcoach.app.data

import java.time.Instant
import java.time.ZoneId

/**
 * Which part of the day it is, so the companion's room can match the real clock.
 *
 * Pure and boundary-tested: the mapping from an hour to a [TimeOfDay] is the whole point,
 * and it must never depend on a live clock in a test. minSdk is 26, so java.time is
 * available natively — no desugaring required.
 */
enum class TimeOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

object TimeOfDayClock {

    /**
     * Map a 24-hour clock hour to a part of day.
     *
     *  - 05:00–11:59  Morning   (wake up, set the day's intention)
     *  - 12:00–16:59  Afternoon (feed the pet, settle in)
     *  - 17:00–20:59  Evening   (lamp on, tea, the calm stretch)
     *  - 21:00–04:59  Night     (by the fire, winding down)
     */
    fun at(hour: Int): TimeOfDay = when (((hour % 24) + 24) % 24) {
        in 5..11 -> TimeOfDay.MORNING
        in 12..16 -> TimeOfDay.AFTERNOON
        in 17..20 -> TimeOfDay.EVENING
        else -> TimeOfDay.NIGHT
    }

    /** The part of day at [now], in [zone] (defaults to the device's zone). */
    fun now(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): TimeOfDay =
        at(Instant.ofEpochMilli(now).atZone(zone).hour)
}
