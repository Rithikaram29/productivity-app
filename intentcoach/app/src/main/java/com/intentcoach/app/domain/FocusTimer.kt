package com.intentcoach.app.domain

import com.intentcoach.app.data.FocusCategory

/**
 * A running focus timer, as plain timestamps.
 *
 * Nothing ticks in storage: "how much is left" is always recomputed from the clock. That
 * is why the timer survives the app being closed — reopening it just recomputes.
 * Pausing records when the pause began; resuming adds the pause's length to
 * [pausedAccumMs].
 */
data class ActiveFocus(
    val category: FocusCategory,
    val label: String,
    val plannedMinutes: Int,
    val startedAt: Long,
    val pausedAccumMs: Long = 0,
    val pausedAt: Long? = null
) {
    val plannedMs: Long get() = plannedMinutes * 60_000L
    val isPaused: Boolean get() = pausedAt != null

    fun elapsedMs(now: Long): Long =
        ((pausedAt ?: now) - startedAt - pausedAccumMs).coerceIn(0L, plannedMs)

    fun remainingMs(now: Long): Long = plannedMs - elapsedMs(now)

    fun isFinished(now: Long): Boolean = remainingMs(now) <= 0L

    /**
     * When the timer reached zero. Used as the session's end time, so a session that
     * finished while the app was closed is not stamped with the time the user came back.
     */
    fun finishedAt(): Long = startedAt + pausedAccumMs + plannedMs

    fun pause(now: Long): ActiveFocus = if (isPaused) this else copy(pausedAt = now)

    fun resume(now: Long): ActiveFocus {
        val p = pausedAt ?: return this
        return copy(pausedAccumMs = pausedAccumMs + (now - p).coerceAtLeast(0), pausedAt = null)
    }

    companion object {
        val PRESETS = listOf(15, 25, 45, 60)
        const val MIN_CUSTOM = 5
        const val MAX_CUSTOM = 180
    }
}
