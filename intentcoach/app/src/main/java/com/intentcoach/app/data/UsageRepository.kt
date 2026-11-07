package com.intentcoach.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.intentcoach.app.domain.ForegroundIntervals
import com.intentcoach.app.domain.TimeMath
import com.intentcoach.app.domain.UsageEvent
import com.intentcoach.app.util.Permissions
import java.time.LocalDate
import java.time.ZoneId

/**
 * The only place that reads Android's usage events.
 *
 * What UsageStatsManager can and cannot do (worth knowing before changing anything here):
 *  - It REPORTS events after they happen. It cannot intercept or delay an app launch.
 *  - Events are kept by the OS for a limited time (varies by device; often days, not
 *    months). Older windows simply come back empty — which we report as "no data".
 *  - It needs the Usage Access special permission; without it every call here returns
 *    null, and callers must show "unavailable" rather than zero.
 */
class UsageRepository(private val context: Context) {

    fun hasAccess(): Boolean = Permissions.hasUsageAccess(context)

    /** All relevant events in [start, end), mapped to our pure model. Null = no access. */
    fun events(start: Long, end: Long): List<UsageEvent>? {
        if (!hasAccess()) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val raw: UsageEvents = try {
            usm.queryEvents(start, end) ?: return null
        } catch (e: SecurityException) {
            // Access can be revoked between the check above and this call.
            return null
        }
        val out = ArrayList<UsageEvent>()
        val e = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(e)
            val kind = when (e.eventType) {
                EV_RESUMED -> UsageEvent.Kind.RESUMED
                EV_PAUSED -> UsageEvent.Kind.PAUSED
                EV_SCREEN_NON_INTERACTIVE -> UsageEvent.Kind.SCREEN_OFF
                EV_KEYGUARD_SHOWN -> UsageEvent.Kind.KEYGUARD_SHOWN
                EV_DEVICE_SHUTDOWN -> UsageEvent.Kind.SHUTDOWN
                EV_DEVICE_STARTUP -> UsageEvent.Kind.STARTUP
                else -> null
            } ?: continue
            out += UsageEvent(e.timeStamp, kind, e.packageName)
        }
        return out
    }

    /**
     * The package most recently brought to the front within the last [lookbackMs], or null
     * if nothing changed in that time (or there is no access). Same rule the service has
     * always used; the lookback keeps the query cheap enough to run every poll.
     */
    fun currentForeground(now: Long, lookbackMs: Long): String? {
        val evs = events(now - lookbackMs, now) ?: return null
        var last: String? = null
        for (ev in evs) {
            when (ev.kind) {
                UsageEvent.Kind.RESUMED -> last = ev.packageName
                // Screen off after the last resume: nothing is really "in front".
                UsageEvent.Kind.SCREEN_OFF, UsageEvent.Kind.KEYGUARD_SHOWN -> last = null
                else -> Unit
            }
        }
        return last
    }

    /**
     * Android-measured foreground time for [packages], one total per day, for the [days]
     * days ending today. A day with no events at all before the oldest event we could read
     * is missing from the map — "no data", not "zero use". Null = no access.
     *
     * Heavy-ish (a week of events); call from a background dispatcher.
     */
    fun foregroundByDay(
        packages: Set<String>,
        now: Long,
        zone: ZoneId,
        days: Int
    ): Map<LocalDate, Long>? {
        val windowStart = TimeMath.startOfWindow(now, zone, days)
        // Start a little early so an app already open at midnight is seen as open.
        val evs = events(windowStart - LOOKBACK_FOR_OPEN_MS, now) ?: return null
        val earliest = evs.minOfOrNull { it.timestamp }
        val result = HashMap<LocalDate, Long>()
        val today = TimeMath.dayOf(now, zone)
        for (back in days - 1 downTo 0) {
            val day = today.minusDays(back.toLong())
            val dayStart = TimeMath.startOfDay(day, zone)
            val dayEnd = if (back == 0) now else TimeMath.startOfDay(day.plusDays(1), zone)
            if (earliest == null || earliest > dayEnd) continue // retention gap: unknown
            val totals = ForegroundIntervals.totals(evs, dayStart, dayEnd, stillForegroundAtEnd = back == 0)
            result[day] = packages.sumOf { totals[it] ?: 0L }
        }
        return result
    }

    /** Android-measured foreground time per package in [start, now]. Null = no access. */
    fun foregroundTotals(packages: Set<String>, start: Long, now: Long): Map<String, Long>? {
        val evs = events(start - LOOKBACK_FOR_OPEN_MS, now) ?: return null
        return ForegroundIntervals.totals(evs, start, now).filterKeys { it in packages }
    }

    companion object {
        // UsageEvents.Event constants, inlined by value. ACTIVITY_RESUMED/PAUSED (API 29)
        // share their values with the older MOVE_TO_FOREGROUND/BACKGROUND, so the same
        // numbers work from API 26 up. SCREEN_NON_INTERACTIVE/KEYGUARD_SHOWN are API 28,
        // DEVICE_SHUTDOWN/STARTUP API 29 — older devices just never report them.
        private const val EV_RESUMED = 1
        private const val EV_PAUSED = 2
        private const val EV_SCREEN_NON_INTERACTIVE = 16
        private const val EV_KEYGUARD_SHOWN = 17
        private const val EV_DEVICE_SHUTDOWN = 26
        private const val EV_DEVICE_STARTUP = 27

        private const val LOOKBACK_FOR_OPEN_MS = 30L * 60 * 1000
    }
}
