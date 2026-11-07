package com.intentcoach.app.domain

import com.intentcoach.app.data.Decision
import com.intentcoach.app.data.DurationSource
import com.intentcoach.app.data.FocusSession
import com.intentcoach.app.data.IntentCategory
import com.intentcoach.app.data.SessionStatus
import com.intentcoach.app.data.SessionType
import com.intentcoach.app.data.SessionWithIntent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The arithmetic behind Home and Insights. Pure: records in, numbers out.
 *
 * Definitions (shown to the user in plain words on the Insights screen):
 *  - A "used" session is one where the user chose Continue: status ACTIVE, COMPLETED or
 *    INTERRUPTED.
 *  - intentional / unplanned count used sessions by their classification.
 *  - unknown counts sessions classified UNKNOWN — mostly dismissed prompts. They are
 *    shown on their own and never folded into unplanned.
 *  - Intentional Usage Rate = intentional ÷ (intentional + unplanned) × 100. Unknown,
 *    dismissed and turned-back records are not eligible. Null when nothing is eligible.
 *    It is a reflection aid, not a validated psychological measure.
 *  - Average session length uses only sessions whose duration was MEASURED.
 */
data class Overview(
    val intentional: Int,
    val unplanned: Int,
    val unknown: Int,
    val turnedBack: Int,
    val dismissed: Int,
    /** Sum of measured durations of used sessions. */
    val measuredSessionMs: Long,
    val measuredSessions: Int,
    /** Used sessions whose duration we could not measure. */
    val unmeasuredSessions: Int
) {
    val usedSessions: Int get() = intentional + unplanned
    val checkIns: Int get() = usedSessions + turnedBack

    val intentionalRate: Int?
        get() = if (usedSessions == 0) null else (intentional * 100f / usedSessions).roundToInt()

    val turnBackRate: Int?
        get() = if (checkIns == 0) null else (turnedBack * 100f / checkIns).roundToInt()

    val averageSessionMs: Long?
        get() = if (measuredSessions == 0) null else measuredSessionMs / measuredSessions

    val isEmpty: Boolean get() = checkIns == 0 && unknown == 0
}

data class AppBreakdown(
    val packageName: String,
    val appName: String,
    val sessions: Int,
    val turnedBack: Int,
    val measuredMs: Long,
    val measuredSessions: Int
) {
    val averageMs: Long? get() = if (measuredSessions == 0) null else measuredMs / measuredSessions
}

data class DayPoint(val day: LocalDate, val usageMs: Long?, val intentional: Int, val unplanned: Int)

object Insights {

    private val USED = setOf(SessionStatus.ACTIVE, SessionStatus.COMPLETED, SessionStatus.INTERRUPTED)

    fun isUsed(r: SessionWithIntent): Boolean = r.session.status in USED

    fun overview(records: List<SessionWithIntent>): Overview {
        var intentional = 0
        var unplanned = 0
        var unknown = 0
        var turnedBack = 0
        var dismissed = 0
        var measuredMs = 0L
        var measured = 0
        var unmeasured = 0
        for (r in records) {
            val s = r.session
            when (s.status) {
                SessionStatus.TURNED_BACK -> turnedBack++
                SessionStatus.DISMISSED -> dismissed++
                else -> Unit
            }
            if (s.sessionType == SessionType.UNKNOWN) unknown++
            if (s.status in USED) {
                when (s.sessionType) {
                    SessionType.INTENTIONAL -> intentional++
                    SessionType.UNPLANNED -> unplanned++
                    SessionType.UNKNOWN -> Unit
                }
                val ms = s.actualDurationMs
                if (s.durationSource == DurationSource.MEASURED && ms != null) {
                    measuredMs += ms
                    measured++
                } else {
                    unmeasured++
                }
            }
        }
        return Overview(intentional, unplanned, unknown, turnedBack, dismissed, measuredMs, measured, unmeasured)
    }

    /** Per-app numbers, busiest (most check-ins) first. */
    fun perApp(records: List<SessionWithIntent>): List<AppBreakdown> =
        records.groupBy { it.session.packageName }
            .map { (pkg, list) ->
                val used = list.filter { isUsed(it) }
                val measured = used.filter {
                    it.session.durationSource == DurationSource.MEASURED && it.session.actualDurationMs != null
                }
                AppBreakdown(
                    packageName = pkg,
                    appName = list.first().session.appName,
                    sessions = used.size,
                    turnedBack = list.count { it.session.status == SessionStatus.TURNED_BACK },
                    measuredMs = measured.sumOf { it.session.actualDurationMs ?: 0L },
                    measuredSessions = measured.size
                )
            }
            .sortedWith(compareByDescending<AppBreakdown> { it.sessions + it.turnedBack }.thenByDescending { it.measuredMs })

    /** How often each intent was chosen, most common first. Includes turned-back check-ins. */
    fun commonIntents(records: List<SessionWithIntent>): List<Pair<IntentCategory, Int>> =
        records.mapNotNull { it.intent?.intentCategory }
            .groupingBy { it }.eachCount()
            .toList()
            .sortedByDescending { it.second }

    /** Turn-backs are stored with decision GO_BACK; this counts them from the answers too. */
    fun turnBacks(records: List<SessionWithIntent>): Int =
        records.count { it.intent?.decision == Decision.GO_BACK }

    /**
     * One point per day for the last [days] days ending today. [usageByDay] comes from
     * Android's usage events; a missing day means "no data", drawn as a gap, not as zero.
     */
    fun dailyTrend(
        records: List<SessionWithIntent>,
        usageByDay: Map<LocalDate, Long>,
        today: LocalDate,
        zone: ZoneId,
        days: Int = 7
    ): List<DayPoint> {
        val byDay = records.groupBy { TimeMath.dayOf(it.session.startTime, zone) }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val list = byDay[d].orEmpty().filter { isUsed(it) }
            DayPoint(
                day = d,
                usageMs = usageByDay[d],
                intentional = list.count { it.session.sessionType == SessionType.INTENTIONAL },
                unplanned = list.count { it.session.sessionType == SessionType.UNPLANNED }
            )
        }
    }

    /** Focus time (actual, not planned) for sessions that ended at or after [since]. */
    fun focusMs(sessions: List<FocusSession>, since: Long): Long =
        sessions.filter { it.endedAt >= since }.sumOf { it.actualMs }
}

object TimeMath {
    const val DAY_MS = 24L * 60 * 60 * 1000

    fun dayOf(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    fun startOfDay(day: LocalDate, zone: ZoneId): Long =
        day.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfToday(now: Long, zone: ZoneId): Long = startOfDay(dayOf(now, zone), zone)

    /** Midnight [days]-1 days ago — so "last 7 days" includes today. */
    fun startOfWindow(now: Long, zone: ZoneId, days: Int): Long =
        startOfDay(dayOf(now, zone).minusDays((days - 1).toLong()), zone)
}

object Format {
    /** "1h 05m", "12m", "<1m". Callers decide how to show a null (unknown) duration. */
    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        return when {
            ms < 60_000 -> "<1m"
            totalMin < 60 -> "${totalMin}m"
            else -> "${totalMin / 60}h ${(totalMin % 60).toString().padStart(2, '0')}m"
        }
    }

    /** "24:59" for timers. */
    fun clock(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0) + 999) / 1000
        return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
    }
}
