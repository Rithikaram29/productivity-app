package com.intentcoach.app.domain

import com.intentcoach.app.data.AppSession
import com.intentcoach.app.data.Decision
import com.intentcoach.app.data.DurationSource
import com.intentcoach.app.data.FocusSession
import com.intentcoach.app.data.IntentCategory
import com.intentcoach.app.data.IntentResponse
import com.intentcoach.app.data.SessionOutcome
import com.intentcoach.app.data.SessionStatus
import com.intentcoach.app.data.SessionType
import com.intentcoach.app.data.SessionWithIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InsightsTest {

    private val zone = ZoneId.of("UTC")
    private val now = 1_700_000_000_000L
    private var n = 0

    private fun rec(
        type: SessionType,
        status: SessionStatus,
        pkg: String = "a",
        ms: Long? = null,
        intent: IntentCategory? = IntentCategory.SOMETHING_SPECIFIC,
        start: Long = now
    ): SessionWithIntent {
        val id = "s${n++}"
        val session = AppSession(
            id = id, packageName = pkg, appName = pkg, startTime = start,
            actualDurationMs = ms,
            durationSource = if (ms != null) DurationSource.MEASURED else DurationSource.UNKNOWN,
            sessionType = type, status = status
        )
        val response = intent?.let {
            IntentResponse(
                sessionId = id, intentCategory = it,
                decision = if (status == SessionStatus.TURNED_BACK) Decision.GO_BACK else Decision.CONTINUE,
                createdAt = start
            )
        }
        return SessionWithIntent(session, response)
    }

    @Test
    fun `empty input is empty, with no rates`() {
        val o = Insights.overview(emptyList())
        assertTrue(o.isEmpty)
        assertNull(o.intentionalRate)
        assertNull(o.turnBackRate)
        assertNull(o.averageSessionMs)
    }

    @Test
    fun `unknown is counted on its own and never as unplanned`() {
        val o = Insights.overview(
            listOf(
                rec(SessionType.UNKNOWN, SessionStatus.DISMISSED, intent = null),
                rec(SessionType.UNKNOWN, SessionStatus.DISMISSED, intent = null),
                rec(SessionType.UNPLANNED, SessionStatus.COMPLETED, ms = 1000)
            )
        )
        assertEquals(2, o.unknown)
        assertEquals(2, o.dismissed)
        assertEquals(1, o.unplanned)
    }

    @Test
    fun `intentional rate excludes unknown and turned back`() {
        val o = Insights.overview(
            listOf(
                rec(SessionType.INTENTIONAL, SessionStatus.COMPLETED, ms = 60_000),
                rec(SessionType.INTENTIONAL, SessionStatus.COMPLETED, ms = 60_000),
                rec(SessionType.INTENTIONAL, SessionStatus.COMPLETED, ms = 60_000),
                rec(SessionType.UNPLANNED, SessionStatus.COMPLETED, ms = 60_000),
                rec(SessionType.INTENTIONAL, SessionStatus.TURNED_BACK),
                rec(SessionType.UNKNOWN, SessionStatus.DISMISSED, intent = null)
            )
        )
        assertEquals(75, o.intentionalRate) // 3 / (3 + 1)
        assertEquals(1, o.turnedBack)
        assertEquals(20, o.turnBackRate) // 1 / (4 + 1)
    }

    @Test
    fun `average uses measured sessions only`() {
        val o = Insights.overview(
            listOf(
                rec(SessionType.INTENTIONAL, SessionStatus.COMPLETED, ms = 2 * 60_000),
                rec(SessionType.INTENTIONAL, SessionStatus.COMPLETED, ms = 4 * 60_000),
                rec(SessionType.INTENTIONAL, SessionStatus.COMPLETED, ms = null)
            )
        )
        assertEquals(3 * 60_000L, o.averageSessionMs)
        assertEquals(1, o.unmeasuredSessions)
        assertEquals(6 * 60_000L, o.measuredSessionMs)
    }

    @Test
    fun `turned back sessions add no usage time`() {
        val o = Insights.overview(listOf(rec(SessionType.INTENTIONAL, SessionStatus.TURNED_BACK)))
        assertEquals(0L, o.measuredSessionMs)
        assertEquals(0, o.usedSessions)
    }

    @Test
    fun `per app breakdown sorts busiest first`() {
        val list = listOf(
            rec(SessionType.INTENTIONAL, SessionStatus.COMPLETED, pkg = "b", ms = 1000),
            rec(SessionType.INTENTIONAL, SessionStatus.COMPLETED, pkg = "a", ms = 1000),
            rec(SessionType.INTENTIONAL, SessionStatus.COMPLETED, pkg = "a", ms = 3000)
        )
        val apps = Insights.perApp(list)
        assertEquals(listOf("a", "b"), apps.map { it.packageName })
        assertEquals(2000L, apps.first().averageMs)
    }

    @Test
    fun `common intents are counted`() {
        val list = listOf(
            rec(SessionType.UNPLANNED, SessionStatus.COMPLETED, intent = IntentCategory.BORED),
            rec(SessionType.UNPLANNED, SessionStatus.COMPLETED, intent = IntentCategory.BORED),
            rec(SessionType.INTENTIONAL, SessionStatus.COMPLETED, intent = IntentCategory.TAKING_A_BREAK)
        )
        assertEquals(IntentCategory.BORED to 2, Insights.commonIntents(list).first())
    }

    @Test
    fun `daily trend has one point per day and keeps missing days null`() {
        val today = TimeMath.dayOf(now, zone)
        val pts = Insights.dailyTrend(
            listOf(rec(SessionType.INTENTIONAL, SessionStatus.COMPLETED, start = now)),
            mapOf(today to 5_000L),
            today, zone
        )
        assertEquals(7, pts.size)
        assertEquals(today, pts.last().day)
        assertEquals(5_000L, pts.last().usageMs)
        assertEquals(1, pts.last().intentional)
        assertNull(pts.first().usageMs)
        assertEquals(today.minusDays(6), pts.first().day)
    }

    @Test
    fun `focus time sums actual not planned`() {
        val f = listOf(
            FocusSession(plannedMinutes = 25, actualMs = 10 * 60_000L, outcome = SessionOutcome.CUT, startedAt = now - 1, endedAt = now),
            FocusSession(plannedMinutes = 25, actualMs = 25 * 60_000L, outcome = SessionOutcome.COMPLETED, startedAt = now - 2, endedAt = now - 10 * TimeMath.DAY_MS)
        )
        assertEquals(10 * 60_000L, Insights.focusMs(f, now - TimeMath.DAY_MS))
    }

    @Test
    fun `time windows start at local midnight`() {
        val start = TimeMath.startOfWindow(now, zone, 7)
        assertEquals(LocalDate.of(2023, 11, 8), TimeMath.dayOf(start, zone))
        assertEquals(0L, start % TimeMath.DAY_MS)
    }

    @Test
    fun `durations format plainly`() {
        assertEquals("<1m", Format.duration(59_000))
        assertEquals("12m", Format.duration(12 * 60_000L))
        assertEquals("1h 05m", Format.duration(65 * 60_000L))
        assertEquals("25:00", Format.clock(25 * 60_000L))
        assertEquals("0:01", Format.clock(1))
    }
}
