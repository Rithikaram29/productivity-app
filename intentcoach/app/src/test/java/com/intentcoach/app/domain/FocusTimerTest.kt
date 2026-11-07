package com.intentcoach.app.domain

import com.intentcoach.app.data.AppCategory
import com.intentcoach.app.data.CompanionMood
import com.intentcoach.app.data.FocusCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimerTest {

    private val t0 = 1_700_000_000_000L
    private val min = 60_000L
    private fun focus(minutes: Int = 25) = ActiveFocus(FocusCategory.WORK, "", minutes, startedAt = t0)

    @Test
    fun `counts elapsed and remaining from timestamps`() {
        val f = focus()
        assertEquals(10 * min, f.elapsedMs(t0 + 10 * min))
        assertEquals(15 * min, f.remainingMs(t0 + 10 * min))
        assertFalse(f.isFinished(t0 + 10 * min))
    }

    @Test
    fun `pause freezes the clock and resume continues it`() {
        val paused = focus().pause(t0 + 5 * min)
        assertTrue(paused.isPaused)
        assertEquals(5 * min, paused.elapsedMs(t0 + 50 * min)) // frozen while paused
        val resumed = paused.resume(t0 + 20 * min)
        assertEquals(15 * min, resumed.pausedAccumMs)
        assertEquals(10 * min, resumed.elapsedMs(t0 + 25 * min))
    }

    @Test
    fun `pausing twice or resuming when running changes nothing`() {
        val p = focus().pause(t0 + min)
        assertEquals(p, p.pause(t0 + 2 * min))
        val r = focus()
        assertEquals(r, r.resume(t0 + 2 * min))
    }

    @Test
    fun `finishes at planned time, even long after`() {
        val f = focus(15)
        assertTrue(f.isFinished(t0 + 15 * min))
        assertEquals(15 * min, f.elapsedMs(t0 + 300 * min)) // capped at planned
        assertEquals(t0 + 15 * min, f.finishedAt())
    }

    @Test
    fun `finish time accounts for pauses`() {
        val f = focus(15).pause(t0 + 5 * min).resume(t0 + 10 * min)
        assertEquals(t0 + 20 * min, f.finishedAt())
    }

    @Test
    fun `clock going backwards never yields negative elapsed time`() {
        assertEquals(0L, focus().elapsedMs(t0 - 10 * min))
    }

    // AppCategorizer is small enough to live here.

    @Test
    fun `known packages win over the declared category`() {
        assertEquals(AppCategory.COMMUNICATION, AppCategorizer.categorize("com.whatsapp", AppCategorizer.CATEGORY_SOCIAL, false))
        assertEquals(AppCategory.SOCIAL, AppCategorizer.categorize("com.instagram.android", AppCategorizer.CATEGORY_UNDEFINED, false))
    }

    @Test
    fun `browsers, declared categories, and the fallback`() {
        assertEquals(AppCategory.BROWSING, AppCategorizer.categorize("com.example.browser", AppCategorizer.CATEGORY_UNDEFINED, true))
        assertEquals(AppCategory.ENTERTAINMENT, AppCategorizer.categorize("com.example.game", AppCategorizer.CATEGORY_GAME, false))
        assertEquals(AppCategory.SOCIAL, AppCategorizer.categorize("com.example.social", AppCategorizer.CATEGORY_SOCIAL, false))
        assertEquals(AppCategory.OTHER, AppCategorizer.categorize("com.example.notes", 7, false))
    }

    // Messages: the tone rules are part of the product, so they get a test.

    @Test
    fun `gentle message acknowledges a turn-back first`() {
        val o = Overview(1, 0, 0, 1, 0, 0, 0, 0)
        assertEquals("You made a conscious choice today.", Messages.gentle(o, CompanionMood.SLEEPY, 3))
    }

    @Test
    fun `gentle message is stable for a given day`() {
        val empty = Insights.overview(emptyList())
        assertEquals(Messages.gentle(empty, CompanionMood.SLEEPY, 42), Messages.gentle(empty, CompanionMood.SLEEPY, 42))
    }
}
