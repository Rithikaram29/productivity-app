package com.intentcoach.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A single focus (pomodoro) session, recorded when it completes or is cut short.
 *
 * Like the app sessions, these rows never leave the device. The reward system, the streak,
 * and the companion's mood are all derived from this ONE table — there is no separately
 * stored "warmth balance" anywhere. A single source of truth, so nothing can drift.
 *
 * The session's "completion status" in the product spec is [outcome]. A running timer is
 * NOT a row here — it lives in [Settings] as timestamps (see domain/FocusTimer.kt), so a
 * row only ever describes a finished session.
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
    /** Optional one-line intent, or the custom category's name. */
    val label: String = "",
    val startedAt: Long,
    val endedAt: Long = System.currentTimeMillis(),
    /** Added in DB v3; older rows were migrated as WORK. */
    val category: FocusCategory = FocusCategory.WORK
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

    /** Live version for the screens: Room re-emits whenever focus_session changes. */
    @Query("SELECT * FROM focus_session ORDER BY endedAt DESC")
    fun observeAll(): Flow<List<FocusSession>>
}
