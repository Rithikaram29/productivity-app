package com.intentcoach.app.data

/**
 * The arithmetic behind the insights screen.
 *
 * Deliberately pure: functions in, data out, no Context, no database, no Android. That
 * makes every number here testable as a plain JVM unit test, and keeps the screen's job
 * down to drawing what it is handed.
 */

/** What one app cost you this week. */
data class AppStat(
    val packageName: String,
    val interrupts: Int,
    val habits: Int,
    val redirected: Int
) {
    /**
     * The share of opens the user themselves marked as "no reason". The sharpest thing
     * the data holds — it is self-reported, not inferred, which is why it is worth
     * showing. 0f when there is nothing to divide by, never a crash.
     */
    val habitRate: Float get() = if (interrupts == 0) 0f else habits.toFloat() / interrupts
}

/** Everything the insights screen shows, for one time window. */
data class WeeklyStats(
    val total: Int,
    val redirected: Int,
    val habits: Int,
    val perApp: List<AppStat>,
    val recent: List<IntentLog>
) {
    val isEmpty: Boolean get() = total == 0
}

object Stats {

    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** Start of the 7-day window ending now. Pass `now` explicitly so tests can pin it. */
    fun weekAgo(now: Long = System.currentTimeMillis()): Long = now - WEEK_MS

    /**
     * Fold a window's worth of logs into the numbers the screen draws.
     *
     * Expects [logs] to already be the window (the DAO filters by timestamp) and to be
     * newest-first, which is the order `recent` is taken in.
     */
    fun from(logs: List<IntentLog>, recentLimit: Int = 8): WeeklyStats {
        val perApp = logs
            .groupBy { it.packageName }
            .map { (pkg, entries) ->
                AppStat(
                    packageName = pkg,
                    interrupts = entries.size,
                    habits = entries.count { it.outcome == Outcome.HABIT },
                    redirected = entries.count { it.outcome == Outcome.REDIRECTED }
                )
            }
            // Loudest app first; ties broken by habit count so the more telling one wins.
            .sortedWith(compareByDescending<AppStat> { it.interrupts }.thenByDescending { it.habits })

        return WeeklyStats(
            total = logs.size,
            redirected = logs.count { it.outcome == Outcome.REDIRECTED },
            habits = logs.count { it.outcome == Outcome.HABIT },
            perApp = perApp,
            recent = logs.take(recentLimit)
        )
    }
}
