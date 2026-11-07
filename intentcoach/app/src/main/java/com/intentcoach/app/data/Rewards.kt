package com.intentcoach.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The reward system: warmth, streak, and the companion's mood.
 *
 * Everything here is derived from the [FocusSession] history — there is no stored score.
 * That is the same choice [Stats] makes: one source of truth (the sessions table) folded
 * into what the screen draws, so nothing can drift. Pure functions, no Context, no
 * database — every rule below is a plain JVM test.
 *
 * The tone is "cutely strict": completing a session warms the room; cutting one costs a
 * little warmth, but only a little — one slip is a droop, never a wipe. Warmth never drops
 * below zero, so a rough day can't bury you in the red.
 */

/** How the companion and its room should feel right now. Drives the visual state. */
enum class CompanionMood {
    /** On a roll — a streak going and focus already today. Room bright, pet perky. */
    THRIVING,

    /** Fed and settled — at least one session done today. The default good state. */
    CONTENT,

    /** Idle — nothing yet today. The pet is napping; nudge, don't nag. */
    SLEEPY,

    /** The last thing that happened was a cut session, nothing has recovered it yet.
     *  Gentle disappointment — a slight wilt, never a scolding. */
    WILTED
}

/** Everything the reward UI needs, from one look at the history. */
data class RewardState(
    val warmth: Int,
    val streakDays: Int,
    val completedToday: Int,
    val mood: CompanionMood
)

object Rewards {

    /** Flat warmth for seeing a session through. */
    const val COMPLETE_BONUS = 5

    /** Extra warmth per planned minute of a completed session (25 min -> +25). */
    const val WARMTH_PER_MINUTE = 1

    /** What a cut session costs. Small on purpose — a nudge, not a punishment. */
    const val CUT_PENALTY = 3

    /** A streak of this many days, with focus today, tips the companion into THRIVING. */
    const val THRIVING_STREAK = 3

    /** The warmth delta for one session — positive when completed, negative when cut. */
    fun warmthFor(session: FocusSession): Int = when (session.outcome) {
        SessionOutcome.COMPLETED -> COMPLETE_BONUS + session.plannedMinutes * WARMTH_PER_MINUTE
        SessionOutcome.CUT -> -CUT_PENALTY
    }

    /** Total warmth across all [sessions], clamped at zero so it never goes negative. */
    fun totalWarmth(sessions: List<FocusSession>): Int =
        sessions.sumOf { warmthFor(it) }.coerceAtLeast(0)

    private fun dayOf(epochMs: Long, zone: ZoneId): LocalDate =
        LocalDate.ofInstant(Instant.ofEpochMilli(epochMs), zone)

    /**
     * Consecutive days, ending today, on which at least one session was COMPLETED.
     *
     * A day with no completed session breaks the run. If nothing is completed *yet today*
     * but yesterday counted, the streak still stands (today isn't over) — so today's
     * absence alone does not reset it.
     */
    fun streakDays(
        sessions: List<FocusSession>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val days = sessions
            .filter { it.outcome == SessionOutcome.COMPLETED }
            .map { dayOf(it.endedAt, zone) }
            .toHashSet()
        if (days.isEmpty()) return 0

        val today = dayOf(now, zone)
        // Start from today if it counts, otherwise yesterday (grace for a day still open).
        var cursor = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** How many sessions were COMPLETED today, in [zone]. */
    fun completedToday(
        sessions: List<FocusSession>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val today = dayOf(now, zone)
        return sessions.count {
            it.outcome == SessionOutcome.COMPLETED && dayOf(it.endedAt, zone) == today
        }
    }

    /**
     * The companion's mood, from the session history.
     *
     * [sessions] is expected newest-first (the DAO returns it that way); only the most
     * recent session is consulted for the "just cut it" wilt.
     */
    fun mood(
        sessions: List<FocusSession>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): CompanionMood {
        val doneToday = completedToday(sessions, now, zone)
        val lastWasCut = sessions.firstOrNull()?.outcome == SessionOutcome.CUT
        return when {
            doneToday > 0 && streakDays(sessions, now, zone) >= THRIVING_STREAK -> CompanionMood.THRIVING
            doneToday > 0 -> CompanionMood.CONTENT
            lastWasCut -> CompanionMood.WILTED
            else -> CompanionMood.SLEEPY
        }
    }

    /** Fold the whole history into the reward UI's state. */
    fun state(
        sessions: List<FocusSession>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): RewardState = RewardState(
        warmth = totalWarmth(sessions),
        streakDays = streakDays(sessions, now, zone),
        completedToday = completedToday(sessions, now, zone),
        mood = mood(sessions, now, zone)
    )
}
