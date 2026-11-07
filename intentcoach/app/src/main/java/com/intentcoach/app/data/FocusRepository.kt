package com.intentcoach.app.data

import android.content.Context
import com.intentcoach.app.domain.ActiveFocus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The focus timer and its history. The running timer lives in [Settings] (timestamps);
 * a finished one becomes a [FocusSession] row. The two never overlap, so a session can
 * not be saved twice.
 */
class FocusRepository(
    private val context: Context,
    private val dao: FocusSessionDao
) {
    private val _active = MutableStateFlow(Settings.activeFocus(context))
    val active: StateFlow<ActiveFocus?> = _active.asStateFlow()

    fun observeAll(): Flow<List<FocusSession>> = dao.observeAll()

    fun start(category: FocusCategory, label: String, minutes: Int, now: Long = System.currentTimeMillis()) {
        set(ActiveFocus(category = category, label = label.trim(), plannedMinutes = minutes, startedAt = now))
    }

    fun pause(now: Long = System.currentTimeMillis()) {
        _active.value?.let { set(it.pause(now)) }
    }

    fun resume(now: Long = System.currentTimeMillis()) {
        _active.value?.let { set(it.resume(now)) }
    }

    /**
     * Ends the running timer and saves it. COMPLETED if it reached zero, CUT if ended early.
     * Returns the saved session, or null if nothing was running.
     */
    suspend fun finish(now: Long = System.currentTimeMillis()): FocusSession? = finishLock.withLock {
        val a = _active.value ?: return@withLock null
        // Clear first: if the app dies between these two lines we lose one row rather than
        // risk saving the same session twice on the next launch.
        set(null)
        val done = a.isFinished(now)
        val session = FocusSession(
            plannedMinutes = a.plannedMinutes,
            actualMs = a.elapsedMs(now),
            outcome = if (done) SessionOutcome.COMPLETED else SessionOutcome.CUT,
            label = a.label,
            startedAt = a.startedAt,
            endedAt = if (done) minOf(a.finishedAt(), now) else now,
            category = a.category
        )
        session.copy(id = dao.insert(session))
    }

    // Home (on resume) and the Focus screen (ticker) can both try to finish the same timer;
    // the lock plus "clear first" guarantees exactly one saved row.
    private val finishLock = Mutex()

    /** Saves a timer that already ran out (e.g. while the app was closed). No-op otherwise. */
    suspend fun finishIfDone(now: Long = System.currentTimeMillis()): FocusSession? =
        if (_active.value?.isFinished(now) == true) finish(now) else null

    fun resetCache() {
        _active.value = null
    }

    private fun set(focus: ActiveFocus?) {
        Settings.setActiveFocus(context, focus)
        _active.value = focus
    }
}
