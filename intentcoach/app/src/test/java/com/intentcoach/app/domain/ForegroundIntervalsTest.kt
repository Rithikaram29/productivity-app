package com.intentcoach.app.domain

import com.intentcoach.app.domain.UsageEvent.Kind.KEYGUARD_SHOWN
import com.intentcoach.app.domain.UsageEvent.Kind.PAUSED
import com.intentcoach.app.domain.UsageEvent.Kind.RESUMED
import com.intentcoach.app.domain.UsageEvent.Kind.SCREEN_OFF
import com.intentcoach.app.domain.UsageEvent.Kind.SHUTDOWN
import com.intentcoach.app.domain.UsageEvent.Kind.STARTUP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duration engine. Each test is one of the edge cases from the product brief:
 * background time, screen-off, app switching, duplicate events, restarts, overlaps,
 * missing events.
 */
class ForegroundIntervalsTest {

    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"
    private val launcher = "com.android.launcher"
    private val s = 1_000L // one second, to keep the numbers readable

    private fun ev(t: Long, kind: UsageEvent.Kind, pkg: String? = null) = UsageEvent(t * s, kind, pkg)

    @Test
    fun `resume then pause is one interval`() {
        val iv = ForegroundIntervals.compute(listOf(ev(10, RESUMED, ig), ev(70, PAUSED, ig)), 0, 1000 * s)
        assertEquals(listOf(Interval(ig, 10 * s, 70 * s)), iv)
    }

    @Test
    fun `background time is not counted`() {
        // Paused at 70, nothing until a resume at 500: the gap belongs to no-one.
        val totals = ForegroundIntervals.totals(
            listOf(ev(10, RESUMED, ig), ev(70, PAUSED, ig), ev(500, RESUMED, ig), ev(530, PAUSED, ig)),
            0, 1000 * s
        )
        assertEquals(90 * s, totals[ig])
    }

    @Test
    fun `screen off closes the interval even without a pause`() {
        val totals = ForegroundIntervals.totals(listOf(ev(10, RESUMED, ig), ev(40, SCREEN_OFF)), 0, 1000 * s)
        assertEquals(30 * s, totals[ig])
    }

    @Test
    fun `keyguard closes the interval`() {
        val totals = ForegroundIntervals.totals(listOf(ev(10, RESUMED, ig), ev(25, KEYGUARD_SHOWN)), 0, 1000 * s)
        assertEquals(15 * s, totals[ig])
    }

    @Test
    fun `switching apps closes the first and opens the second`() {
        val iv = ForegroundIntervals.compute(
            listOf(ev(10, RESUMED, ig), ev(40, RESUMED, yt), ev(100, PAUSED, yt)),
            0, 1000 * s
        )
        assertEquals(listOf(Interval(ig, 10 * s, 40 * s), Interval(yt, 40 * s, 100 * s)), iv)
    }

    @Test
    fun `intervals never overlap even when the pause arrives late`() {
        // ig's PAUSED is reported after yt already resumed — common on real devices.
        val iv = ForegroundIntervals.compute(
            listOf(ev(10, RESUMED, ig), ev(40, RESUMED, yt), ev(41, PAUSED, ig), ev(90, PAUSED, yt)),
            0, 1000 * s
        )
        assertEquals(listOf(Interval(ig, 10 * s, 40 * s), Interval(yt, 40 * s, 90 * s)), iv)
    }

    @Test
    fun `duplicate events are counted once`() {
        val events = listOf(ev(10, RESUMED, ig), ev(10, RESUMED, ig), ev(70, PAUSED, ig), ev(70, PAUSED, ig))
        assertEquals(60 * s, ForegroundIntervals.totals(events, 0, 1000 * s)[ig])
    }

    @Test
    fun `a second resume of the open app does not restart the clock`() {
        val events = listOf(ev(10, RESUMED, ig), ev(30, RESUMED, ig), ev(70, PAUSED, ig))
        assertEquals(60 * s, ForegroundIntervals.totals(events, 0, 1000 * s)[ig])
    }

    @Test
    fun `unsorted events are handled`() {
        val events = listOf(ev(70, PAUSED, ig), ev(10, RESUMED, ig))
        assertEquals(60 * s, ForegroundIntervals.totals(events, 0, 1000 * s)[ig])
    }

    @Test
    fun `a pause with no resume is ignored`() {
        assertTrue(ForegroundIntervals.compute(listOf(ev(70, PAUSED, ig)), 0, 1000 * s).isEmpty())
    }

    @Test
    fun `shutdown and startup close an open interval`() {
        val a = ForegroundIntervals.totals(listOf(ev(10, RESUMED, ig), ev(50, SHUTDOWN)), 0, 1000 * s)
        assertEquals(40 * s, a[ig])
        // Older devices report no shutdown; the next startup still closes it.
        val b = ForegroundIntervals.totals(listOf(ev(10, RESUMED, ig), ev(80, STARTUP)), 0, 1000 * s)
        assertEquals(70 * s, b[ig])
    }

    @Test
    fun `intervals are clipped to the window`() {
        val events = listOf(ev(10, RESUMED, ig), ev(200, PAUSED, ig))
        assertEquals(50 * s, ForegroundIntervals.totals(events, 100 * s, 150 * s)[ig])
    }

    @Test
    fun `open interval counts to window end only when asked`() {
        val events = listOf(ev(10, RESUMED, ig))
        assertEquals(90 * s, ForegroundIntervals.totals(events, 0, 100 * s, stillForegroundAtEnd = true)[ig])
        assertNull(ForegroundIntervals.totals(events, 0, 100 * s, stillForegroundAtEnd = false)[ig])
    }

    @Test
    fun `empty or inverted window yields nothing`() {
        assertTrue(ForegroundIntervals.compute(listOf(ev(10, RESUMED, ig)), 100, 100).isEmpty())
        assertTrue(ForegroundIntervals.compute(listOf(ev(10, RESUMED, ig)), 200, 100).isEmpty())
    }

    // --- resolveSession ---

    private val grace = 60 * s

    @Test
    fun `session still open while the app is in front`() {
        val r = ForegroundIntervals.resolveSession(ig, 10 * s, listOf(ev(11, RESUMED, ig)), now = 300 * s, graceMs = grace)
        assertFalse(r.ended)
        assertTrue(r.hasEvidence)
        assertEquals(289 * s, r.foregroundMs)
    }

    @Test
    fun `session not ended until the grace period passes`() {
        val events = listOf(ev(11, RESUMED, ig), ev(100, RESUMED, launcher))
        val soon = ForegroundIntervals.resolveSession(ig, 10 * s, events, now = 130 * s, graceMs = grace)
        assertFalse(soon.ended)
        val later = ForegroundIntervals.resolveSession(ig, 10 * s, events, now = 200 * s, graceMs = grace)
        assertTrue(later.ended)
        assertEquals(100 * s, later.endTime)
        assertEquals(89 * s, later.foregroundMs)
    }

    @Test
    fun `a quick screen-off and return stays one session`() {
        val events = listOf(
            ev(11, RESUMED, ig), ev(50, SCREEN_OFF), ev(70, RESUMED, ig), ev(100, PAUSED, ig)
        )
        val r = ForegroundIntervals.resolveSession(ig, 10 * s, events, now = 400 * s, graceMs = grace)
        assertTrue(r.ended)
        assertEquals(100 * s, r.endTime)
        // 39 s + 30 s; the 20 s with the screen off is not counted.
        assertEquals(69 * s, r.foregroundMs)
    }

    @Test
    fun `a return after the grace period belongs to a later session`() {
        val events = listOf(ev(11, RESUMED, ig), ev(50, PAUSED, ig), ev(500, RESUMED, ig), ev(600, PAUSED, ig))
        val r = ForegroundIntervals.resolveSession(ig, 10 * s, events, now = 1000 * s, graceMs = grace)
        assertTrue(r.ended)
        assertEquals(50 * s, r.endTime)
        assertEquals(39 * s, r.foregroundMs)
    }

    @Test
    fun `no trace of the app means unknown, not zero`() {
        val r = ForegroundIntervals.resolveSession(ig, 10 * s, emptyList(), now = 500 * s, graceMs = grace)
        assertTrue(r.ended)
        assertFalse(r.hasEvidence)
        assertNull(r.endTime)
    }

    @Test
    fun `time on our own popup does not end the session or count as app time`() {
        val coach = "com.intentcoach.app"
        // 2 minutes deciding on the "time's up" popup, then back into the app.
        val events = listOf(
            ev(11, RESUMED, ig), ev(100, RESUMED, coach), ev(220, RESUMED, ig), ev(300, PAUSED, ig)
        )
        val bridged = ForegroundIntervals.resolveSession(ig, 10 * s, events, now = 1000 * s, graceMs = grace, bridgePackage = coach)
        assertEquals(300 * s, bridged.endTime)
        assertEquals((89 + 80) * s, bridged.foregroundMs)

        // Without the bridge, the same 2 minutes would have split it into two sessions.
        val plain = ForegroundIntervals.resolveSession(ig, 10 * s, events, now = 1000 * s, graceMs = grace)
        assertEquals(100 * s, plain.endTime)
    }

    @Test
    fun `still on our popup right now keeps the session open`() {
        val coach = "com.intentcoach.app"
        val events = listOf(ev(11, RESUMED, ig), ev(100, RESUMED, coach))
        val r = ForegroundIntervals.resolveSession(ig, 10 * s, events, now = 400 * s, graceMs = grace, bridgePackage = coach)
        assertFalse(r.ended)
    }

    @Test
    fun `a shutdown ending the session marks it interrupted`() {
        val events = listOf(ev(11, RESUMED, ig), ev(90, SHUTDOWN), ev(200, STARTUP))
        val r = ForegroundIntervals.resolveSession(ig, 10 * s, events, now = 400 * s, graceMs = grace)
        assertTrue(r.ended)
        assertTrue(r.interrupted)
        assertEquals(79 * s, r.foregroundMs)
    }
}
