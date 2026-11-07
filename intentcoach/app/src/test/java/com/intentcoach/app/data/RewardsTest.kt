package com.intentcoach.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * Rewards is pure — no Context, no database — so these are plain JVM tests. Time is pinned
 * with a fixed `now` and a fixed UTC zone so day-bucketing never depends on when or where
 * the test runs.
 */
class RewardsTest {

    private val zone = ZoneId.of("UTC")
    // 2023-11-14T22:13:20Z — comfortably mid-day-boundary, so `now - 1 day` stays on the 13th.
    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000
    private var nextId = 1L

    /** A session that ended [daysAgo] days before `now`, at the same clock time. */
    private fun session(
        daysAgo: Int,
        outcome: SessionOutcome,
        minutes: Int = 25,
        id: Long = nextId++
    ): FocusSession {
        val ended = now - daysAgo * dayMs
        return FocusSession(
            id = id,
            plannedMinutes = minutes,
            actualMs = minutes * 60_000L,
            outcome = outcome,
            startedAt = ended - minutes * 60_000L,
            endedAt = ended
        )
    }

    @Test
    fun `completed session earns bonus plus a minute of warmth each`() {
        assertEquals(5 + 25, Rewards.warmthFor(session(0, SessionOutcome.COMPLETED, minutes = 25)))
    }

    @Test
    fun `cut session costs the flat penalty`() {
        assertEquals(-3, Rewards.warmthFor(session(0, SessionOutcome.CUT)))
    }

    @Test
    fun `total warmth sums the sessions`() {
        val sessions = listOf(
            session(0, SessionOutcome.COMPLETED, minutes = 25), // +30
            session(1, SessionOutcome.COMPLETED, minutes = 10), // +15
            session(2, SessionOutcome.CUT)                      // -3
        )
        assertEquals(30 + 15 - 3, Rewards.totalWarmth(sessions))
    }

    @Test
    fun `total warmth never goes negative`() {
        val sessions = listOf(session(0, SessionOutcome.CUT), session(0, SessionOutcome.CUT))
        assertEquals(0, Rewards.totalWarmth(sessions))
    }

    @Test
    fun `empty history has no streak`() {
        assertEquals(0, Rewards.streakDays(emptyList(), now, zone))
    }

    @Test
    fun `streak counts consecutive days including today`() {
        val sessions = listOf(
            session(0, SessionOutcome.COMPLETED),
            session(1, SessionOutcome.COMPLETED),
            session(2, SessionOutcome.COMPLETED)
        )
        assertEquals(3, Rewards.streakDays(sessions, now, zone))
    }

    @Test
    fun `a missing day breaks the streak`() {
        // today and the day before yesterday, but NOT yesterday.
        val sessions = listOf(
            session(0, SessionOutcome.COMPLETED),
            session(2, SessionOutcome.COMPLETED)
        )
        assertEquals(1, Rewards.streakDays(sessions, now, zone))
    }

    @Test
    fun `nothing today but yesterday keeps the streak alive (grace)`() {
        val sessions = listOf(
            session(1, SessionOutcome.COMPLETED),
            session(2, SessionOutcome.COMPLETED)
        )
        assertEquals(2, Rewards.streakDays(sessions, now, zone))
    }

    @Test
    fun `cut sessions do not count toward the streak`() {
        val sessions = listOf(
            session(0, SessionOutcome.CUT),
            session(1, SessionOutcome.COMPLETED)
        )
        assertEquals(1, Rewards.streakDays(sessions, now, zone))
    }

    @Test
    fun `completedToday counts only today's completed sessions`() {
        val sessions = listOf(
            session(0, SessionOutcome.COMPLETED),
            session(0, SessionOutcome.CUT),
            session(1, SessionOutcome.COMPLETED)
        )
        assertEquals(1, Rewards.completedToday(sessions, now, zone))
    }

    @Test
    fun `mood is SLEEPY when nothing has happened`() {
        assertEquals(CompanionMood.SLEEPY, Rewards.mood(emptyList(), now, zone))
    }

    @Test
    fun `mood is CONTENT after one session today`() {
        val sessions = listOf(session(0, SessionOutcome.COMPLETED))
        assertEquals(CompanionMood.CONTENT, Rewards.mood(sessions, now, zone))
    }

    @Test
    fun `mood is THRIVING on a three-day streak with focus today`() {
        val sessions = listOf(
            session(0, SessionOutcome.COMPLETED),
            session(1, SessionOutcome.COMPLETED),
            session(2, SessionOutcome.COMPLETED)
        )
        assertEquals(CompanionMood.THRIVING, Rewards.mood(sessions, now, zone))
    }

    @Test
    fun `mood is WILTED when the most recent session was cut and none completed today`() {
        // newest-first: a cut is the latest thing, nothing completed today.
        val sessions = listOf(session(0, SessionOutcome.CUT))
        assertEquals(CompanionMood.WILTED, Rewards.mood(sessions, now, zone))
    }

    @Test
    fun `a completed session today outweighs an earlier cut`() {
        // newest-first: completed today is first, so no wilt.
        val sessions = listOf(
            session(0, SessionOutcome.COMPLETED),
            session(0, SessionOutcome.CUT)
        )
        assertEquals(CompanionMood.CONTENT, Rewards.mood(sessions, now, zone))
    }

    @Test
    fun `state folds warmth, streak, today and mood together`() {
        val sessions = listOf(
            session(0, SessionOutcome.COMPLETED, minutes = 25),
            session(1, SessionOutcome.COMPLETED, minutes = 25),
            session(2, SessionOutcome.COMPLETED, minutes = 25)
        )
        val state = Rewards.state(sessions, now, zone)
        assertEquals(90, state.warmth)          // 3 x (5 + 25)
        assertEquals(3, state.streakDays)
        assertEquals(1, state.completedToday)
        assertEquals(CompanionMood.THRIVING, state.mood)
    }
}
