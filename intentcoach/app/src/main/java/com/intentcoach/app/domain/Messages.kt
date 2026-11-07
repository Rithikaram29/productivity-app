package com.intentcoach.app.domain

import com.intentcoach.app.data.CompanionMood
import com.intentcoach.app.data.TimeOfDay

/**
 * The companion's words. Warm, specific when there's something true to say, never a score
 * and never a guilt trip. Pure, so the tone is reviewable in one place and testable.
 */
object Messages {

    fun greeting(time: TimeOfDay): String = when (time) {
        TimeOfDay.MORNING -> "Good morning"
        TimeOfDay.AFTERNOON -> "Good afternoon"
        TimeOfDay.EVENING -> "Good evening"
        TimeOfDay.NIGHT -> "Hello, night owl"
    }

    private val GENERAL = listOf(
        "A little awareness goes a long way.",
        "There's no wrong answer when we ask. Just an honest one.",
        "Noticing is the whole trick.",
        "Your phone, your call. We're just here to ask."
    )

    /**
     * Picks one line. [daySeed] (e.g. the day of the year) keeps it stable through the day
     * instead of changing on every recomposition.
     */
    fun gentle(today: Overview, mood: CompanionMood, daySeed: Int): String = when {
        today.turnedBack > 0 -> "You made a conscious choice today."
        today.checkIns > 0 -> "You took a moment before opening an app."
        mood == CompanionMood.THRIVING -> "Look at that focus streak. The room feels warm."
        else -> GENERAL[Math.floorMod(daySeed, GENERAL.size)]
    }
}
