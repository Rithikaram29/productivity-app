package com.intentcoach.app.data

import androidx.room.withTransaction
import com.intentcoach.app.domain.Classification
import com.intentcoach.app.domain.ForegroundIntervals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the life of an app session: created at the check-in, closed when the app leaves
 * the foreground, optionally reflected on later.
 *
 * Closing is done by [reconcile], which re-reads Android's usage events and decides from
 * evidence — not from in-memory state in the service. That is what makes it survive the
 * service being killed, the app being force-stopped, and reboots: whoever runs reconcile
 * next (service tick, app launch, boot) closes the session correctly from the record.
 */
class SessionRepository(
    private val db: AppDatabase,
    private val usage: UsageRepository
) {
    private val dao = db.appSessionDao()

    // The service and the UI can both call reconcile; serialise them so one session is
    // never closed twice with two different answers.
    private val reconcileLock = Mutex()

    /** Records the outcome of a completed check-in. Returns the new session's id. */
    suspend fun recordCheckIn(
        packageName: String,
        appName: String,
        source: CheckInSource,
        intent: IntentCategory,
        description: String,
        plannedMinutes: Int?,
        decision: Decision,
        now: Long = System.currentTimeMillis()
    ): String {
        val continuing = decision == Decision.CONTINUE
        val session = AppSession(
            packageName = packageName,
            appName = appName,
            startTime = now,
            endTime = if (continuing) null else now,
            actualDurationMs = if (continuing) null else 0L,
            durationSource = if (continuing) DurationSource.UNKNOWN else DurationSource.NOT_APPLICABLE,
            sessionType = Classification.classify(intent),
            status = if (continuing) SessionStatus.ACTIVE else SessionStatus.TURNED_BACK,
            source = source
        )
        val response = IntentResponse(
            sessionId = session.id,
            intentCategory = intent,
            description = description.trim(),
            plannedDurationMin = plannedMinutes,
            decision = decision,
            createdAt = now
        )
        // Both rows or neither: a session without its answers would look like a dismissal.
        db.withTransaction {
            dao.insertSession(session)
            dao.insertIntent(response)
        }
        return session.id
    }

    /**
     * The prompt closed without an answer. Recorded as UNKNOWN/DISMISSED so the cooldown
     * applies (no prompt loop) — but never as an intentional session.
     */
    suspend fun recordDismissed(
        packageName: String,
        appName: String,
        source: CheckInSource,
        now: Long = System.currentTimeMillis()
    ) {
        dao.insertSession(
            AppSession(
                packageName = packageName,
                appName = appName,
                startTime = now,
                endTime = now,
                actualDurationMs = null,
                durationSource = DurationSource.NOT_APPLICABLE,
                sessionType = SessionType.UNKNOWN,
                status = SessionStatus.DISMISSED,
                source = source
            )
        )
    }

    suspend fun activeFor(pkg: String): AppSession? = dao.activeFor(pkg)

    /** The last moment this app had anything happen: a session end, or a prompt answered. */
    suspend fun lastActivityAt(pkg: String): Long? =
        dao.latestFor(pkg)?.let { it.endTime ?: it.startTime }

    /**
     * Close every ACTIVE session whose app has left the foreground, using usage events as
     * the evidence. Safe to call often and from anywhere; it is idempotent.
     */
    suspend fun reconcile(now: Long = System.currentTimeMillis()) = reconcileLock.withLock {
        for (s in dao.activeSessions()) {
            val events = usage.events(s.startTime - CONTEXT_BEFORE_MS, now)
            if (events == null) {
                // No usage access: we cannot measure, so we never guess. Close it as
                // "duration unknown" once it is clearly over.
                if (now - s.startTime > SESSION_GRACE_MS) {
                    dao.updateSession(
                        s.copy(status = SessionStatus.COMPLETED, durationSource = DurationSource.UNKNOWN)
                    )
                }
                continue
            }
            val r = ForegroundIntervals.resolveSession(s.packageName, s.startTime, events, now, SESSION_GRACE_MS)
            when {
                r.ended -> dao.updateSession(
                    s.copy(
                        status = if (r.interrupted) SessionStatus.INTERRUPTED else SessionStatus.COMPLETED,
                        endTime = r.endTime,
                        actualDurationMs = if (r.hasEvidence) r.foregroundMs else null,
                        durationSource = if (r.hasEvidence) DurationSource.MEASURED else DurationSource.UNKNOWN
                    )
                )
                // Still going: keep the running total fresh so Home shows live numbers.
                r.hasEvidence && r.foregroundMs != s.actualDurationMs -> dao.updateSession(
                    s.copy(actualDurationMs = r.foregroundMs, durationSource = DurationSource.MEASURED)
                )
            }
        }
    }

    fun observeSince(since: Long): Flow<List<SessionWithIntent>> = dao.observeSince(since)
    fun observeRecent(limit: Int = 200): Flow<List<SessionWithIntent>> = dao.observeRecent(limit)
    fun observeForApp(pkg: String, limit: Int = 200): Flow<List<SessionWithIntent>> = dao.observeForApp(pkg, limit)

    /** Only sessions from the last day are offered for reflection — older ones live in history. */
    fun observePendingReflection(now: Long = System.currentTimeMillis()): Flow<SessionWithIntent?> =
        dao.observePendingReflection(now - REFLECTION_WINDOW_MS)

    suspend fun saveReflection(sessionId: String, reflection: Reflection, notes: String) =
        dao.setReflection(sessionId, reflection.name, notes.trim())

    suspend fun dismissReflection(sessionId: String) = dao.dismissReflection(sessionId)

    companion object {
        /** Leave for less than this and come back = same session (calls, quick screen-offs). */
        const val SESSION_GRACE_MS = 60_000L

        /** Events read from slightly before the check-in, to see what was already open. */
        private const val CONTEXT_BEFORE_MS = 10_000L

        private const val REFLECTION_WINDOW_MS = 24L * 60 * 60 * 1000
    }
}
