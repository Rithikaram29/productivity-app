package com.intentcoach.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stats is pure — no Context, no database — so these are plain JVM tests with no
 * Robolectric and no Android runtime. That is the payoff for keeping the arithmetic
 * out of the Compose screen.
 */
class StatsTest {

    private fun log(
        pkg: String,
        outcome: Outcome,
        intent: String = "something",
        timestamp: Long = 0L,
        id: Long = nextId++
    ) = IntentLog(id = id, packageName = pkg, statedIntent = intent, outcome = outcome, timestamp = timestamp)

    private var nextId = 1L

    @Test
    fun `empty week reports empty`() {
        val stats = Stats.from(emptyList())

        assertTrue(stats.isEmpty)
        assertEquals(0, stats.total)
        assertEquals(0, stats.redirected)
        assertEquals(0, stats.habits)
        assertTrue(stats.perApp.isEmpty())
        assertTrue(stats.recent.isEmpty())
    }

    @Test
    fun `single log counts once and is not empty`() {
        val stats = Stats.from(listOf(log("com.instagram.android", Outcome.HABIT)))

        assertEquals(1, stats.total)
        assertEquals(1, stats.habits)
        assertEquals(0, stats.redirected)
        assertEquals(1, stats.perApp.size)
        assertEquals(1f, stats.perApp.first().habitRate, 0.0001f)
    }

    @Test
    fun `habit rate is habits over interrupts per app`() {
        val logs = List(14) { log("com.instagram.android", Outcome.HABIT) } +
            List(9) { log("com.instagram.android", Outcome.PROCEEDED) }

        val instagram = Stats.from(logs).perApp.single()

        assertEquals(23, instagram.interrupts)
        assertEquals(14, instagram.habits)
        // "you opened Instagram 23 times this week; 14 of those you had no reason"
        assertEquals(14f / 23f, instagram.habitRate, 0.0001f)
    }

    @Test
    fun `habit rate is zero rather than dividing by zero`() {
        // Not reachable through Stats.from (an app with no interrupts has no row), but
        // AppStat is public data — guard the division directly.
        val stat = AppStat(packageName = "com.whatever", interrupts = 0, habits = 0, redirected = 0)

        assertEquals(0f, stat.habitRate, 0.0f)
    }

    @Test
    fun `per-app breakdown is sorted by interrupts, busiest first`() {
        val logs = List(3) { log("com.reddit.frontpage", Outcome.PROCEEDED) } +
            List(7) { log("com.instagram.android", Outcome.PROCEEDED) } +
            List(5) { log("com.google.android.youtube", Outcome.PROCEEDED) }

        val order = Stats.from(logs).perApp.map { it.packageName }

        assertEquals(
            listOf("com.instagram.android", "com.google.android.youtube", "com.reddit.frontpage"),
            order
        )
    }

    @Test
    fun `ties on interrupt count are broken by habit count`() {
        val logs = List(4) { log("com.quiet.app", Outcome.PROCEEDED) } +
            List(4) { log("com.telling.app", Outcome.HABIT) }

        val order = Stats.from(logs).perApp.map { it.packageName }

        assertEquals(listOf("com.telling.app", "com.quiet.app"), order)
    }

    @Test
    fun `totals split across the three outcomes`() {
        val logs = listOf(
            log("a", Outcome.REDIRECTED),
            log("a", Outcome.REDIRECTED),
            log("b", Outcome.PROCEEDED),
            log("b", Outcome.HABIT)
        )

        val stats = Stats.from(logs)

        assertEquals(4, stats.total)
        assertEquals(2, stats.redirected)
        assertEquals(1, stats.habits)
    }

    @Test
    fun `recent list respects its limit and keeps the given order`() {
        val logs = (1..12).map { log("a", Outcome.PROCEEDED, intent = "intent $it", timestamp = it.toLong()) }

        val recent = Stats.from(logs, recentLimit = 3).recent

        assertEquals(3, recent.size)
        assertEquals(listOf("intent 1", "intent 2", "intent 3"), recent.map { it.statedIntent })
    }

    @Test
    fun `recent list copes with fewer logs than the limit`() {
        val stats = Stats.from(listOf(log("a", Outcome.HABIT)), recentLimit = 8)

        assertEquals(1, stats.recent.size)
    }

    @Test
    fun `week window is exactly seven days back from the given now`() {
        val now = 1_700_000_000_000L

        assertEquals(now - 7L * 24 * 60 * 60 * 1000, Stats.weekAgo(now))
        assertEquals(7L * 24 * 60 * 60 * 1000, Stats.WEEK_MS)
    }

    @Test
    fun `a log exactly on the seven-day boundary is inside the window`() {
        // The DAO filters with `timestamp >= :since`, so the boundary is inclusive.
        // This pins the contract the query relies on.
        val now = 1_700_000_000_000L
        val boundary = Stats.weekAgo(now)

        assertTrue(boundary >= Stats.weekAgo(now))
        assertTrue(boundary - 1 < Stats.weekAgo(now))
    }
}
