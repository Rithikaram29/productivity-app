package com.intentcoach.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A single focus (pomodoro) session, recorded when it completes or is cut short.
 *
 * Like [IntentLog], these rows never leave the device. The reward system, the streak, and
 * the companion's mood are all derived from this ONE table — there is no separately stored
 * "warmth balance" anywhere. That mirrors how the insights numbers are folded out of
 * intent_log: a single source of truth, so nothing can drift out of sync.
 */

enum class SessionOutcome {
    /** Ran to the end of its planned time. Earns warmth. */
    COMPLETED,

    /** Ended early — gave up, or was abandoned. A gentle decline, never a wipe. */
    CUT
}

@Entity(tableName = "focus_session")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** What the user signed up for, in minutes (e.g. 25). */
    val plannedMinutes: Int,
    /** How long they actually focused before it ended, in milliseconds. */
    val actualMs: Long,
    val outcome: SessionOutcome,
    /** Optional one-line intent, e.g. "Finishing the design review". */
    val label: String = "",
    val startedAt: Long,
    val endedAt: Long = System.currentTimeMillis()
)

@Dao
interface FocusSessionDao {
    @Insert
    suspend fun insert(session: FocusSession): Long

    /** Every session in a window, newest first — the reward system folds its numbers from this. */
    @Query("SELECT * FROM focus_session WHERE endedAt >= :since ORDER BY endedAt DESC")
    suspend fun since(since: Long): List<FocusSession>

    @Query("SELECT * FROM focus_session ORDER BY endedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<FocusSession>

    @Query("SELECT COUNT(*) FROM focus_session WHERE outcome = 'COMPLETED' AND endedAt >= :since")
    suspend fun completedSince(since: Long): Int
}
