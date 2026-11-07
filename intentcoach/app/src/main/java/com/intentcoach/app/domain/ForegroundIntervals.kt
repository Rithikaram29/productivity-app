package com.intentcoach.app.domain

/**
 * Turning Android's raw usage events into "how long was this app really on screen".
 *
 * Pure Kotlin — no Android types — so every edge case is a plain JVM unit test. The
 * detection layer maps `UsageEvents.Event` into [UsageEvent] and hands the list here.
 *
 * The model: at most one app is in the foreground at a time. An interval opens when an
 * app is RESUMED and closes when that app is PAUSED, another app is RESUMED, the screen
 * goes off / locks, or the device shuts down or starts up. Split-screen and
 * picture-in-picture are not modelled (documented limitation).
 *
 * Robustness rules, each covered by a test:
 *  - events may arrive unsorted or duplicated — we sort and de-duplicate;
 *  - a RESUMED for the app that is already open is ignored (no double counting);
 *  - a PAUSED with nothing open is ignored (missing RESUMED);
 *  - intervals are clipped to the query window, so windows never overlap each other;
 *  - an interval still open at the end of the window is either counted up to the end
 *    ([stillForegroundAtEnd] = true, the "right now" case) or dropped.
 */
data class UsageEvent(val timestamp: Long, val kind: Kind, val packageName: String? = null) {
    enum class Kind { RESUMED, PAUSED, SCREEN_OFF, KEYGUARD_SHOWN, SHUTDOWN, STARTUP }
}

data class Interval(val packageName: String, val start: Long, val end: Long) {
    val durationMs: Long get() = end - start
}

/** What we can say about one ACTIVE session right now. */
data class SessionResolution(
    /** The user has left the app and not come back within the grace period. */
    val ended: Boolean,
    /** When they left (end of the last foreground interval), if known. */
    val endTime: Long?,
    /** Foreground time inside the session so far. Only meaningful if [hasEvidence]. */
    val foregroundMs: Long,
    /** False when the events contain no trace of the app at all — duration is unknown. */
    val hasEvidence: Boolean,
    /** The session ended because the device shut down or restarted. */
    val interrupted: Boolean
)

object ForegroundIntervals {

    fun compute(
        events: List<UsageEvent>,
        windowStart: Long,
        windowEnd: Long,
        stillForegroundAtEnd: Boolean = true
    ): List<Interval> {
        if (windowEnd <= windowStart) return emptyList()
        // distinct() removes exact duplicates; sortedBy is stable, so equal timestamps keep
        // the order Android reported them in.
        val sorted = events.distinct().sortedBy { it.timestamp }
        val out = mutableListOf<Interval>()
        var openPkg: String? = null
        var openStart = 0L

        fun close(at: Long) {
            val pkg = openPkg ?: return
            val s = maxOf(openStart, windowStart)
            val e = minOf(at, windowEnd)
            if (e > s) out += Interval(pkg, s, e)
            openPkg = null
        }

        for (ev in sorted) {
            if (ev.timestamp > windowEnd) break
            when (ev.kind) {
                UsageEvent.Kind.RESUMED -> {
                    val pkg = ev.packageName ?: continue
                    if (pkg == openPkg) continue // duplicate / activity switch inside the app
                    close(ev.timestamp)
                    openPkg = pkg
                    openStart = ev.timestamp
                }
                UsageEvent.Kind.PAUSED -> if (openPkg != null && ev.packageName == openPkg) close(ev.timestamp)
                UsageEvent.Kind.SCREEN_OFF,
                UsageEvent.Kind.KEYGUARD_SHOWN,
                UsageEvent.Kind.SHUTDOWN,
                UsageEvent.Kind.STARTUP -> close(ev.timestamp)
            }
        }
        if (openPkg != null) {
            if (stillForegroundAtEnd) close(windowEnd) else openPkg = null
        }
        return out
    }

    /** Total foreground milliseconds per package inside the window. */
    fun totals(
        events: List<UsageEvent>,
        windowStart: Long,
        windowEnd: Long,
        stillForegroundAtEnd: Boolean = true
    ): Map<String, Long> =
        compute(events, windowStart, windowEnd, stillForegroundAtEnd)
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { it.durationMs } }

    /**
     * Decide whether an ACTIVE session for [pkg] that began at [sessionStart] is over.
     *
     * The session keeps going across short gaps (an incoming call, a quick screen-off,
     * switching activities inside the app) as long as the user returns within [graceMs].
     * The first gap longer than that ends it; anything after belongs to a later session.
     */
    fun resolveSession(
        pkg: String,
        sessionStart: Long,
        events: List<UsageEvent>,
        now: Long,
        graceMs: Long
    ): SessionResolution {
        val intervals = compute(events, sessionStart, now, stillForegroundAtEnd = true)
            .filter { it.packageName == pkg }

        if (intervals.isEmpty()) {
            // No trace of the app since the check-in. Either it never opened, or the events
            // are gone. After the grace period we stop waiting, and report "unknown".
            return SessionResolution(
                ended = now - sessionStart > graceMs,
                endTime = null,
                foregroundMs = 0,
                hasEvidence = false,
                interrupted = false
            )
        }

        var end = intervals.first().end
        var total = intervals.first().durationMs
        for (i in 1 until intervals.size) {
            val iv = intervals[i]
            if (iv.start - end > graceMs) break
            total += iv.durationMs
            end = iv.end
        }

        val stillOpen = end >= now
        val ended = !stillOpen && now - end > graceMs
        val interrupted = ended && events.any {
            (it.kind == UsageEvent.Kind.SHUTDOWN || it.kind == UsageEvent.Kind.STARTUP) &&
                it.timestamp >= end && it.timestamp <= end + graceMs
        }
        return SessionResolution(
            ended = ended,
            endTime = if (ended) end else null,
            foregroundMs = total,
            hasEvidence = true,
            interrupted = interrupted
        )
    }
}
