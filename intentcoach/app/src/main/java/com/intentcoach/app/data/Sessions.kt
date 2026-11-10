package com.intentcoach.app.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * The session tables. Everything here stays on the device — no network code touches it.
 *
 * Shape: one [AppSession] per time the user met a check-in (or started one by hand),
 * and at most one [IntentResponse] holding what they said. Splitting them keeps the
 * measured facts (times, durations) apart from the self-reported ones (intent,
 * reflection), and lets a DISMISSED session exist with no intent at all — which is how
 * we avoid recording a false intentional session.
 */

/** An app the user chose to be asked about. */
@Entity(
    tableName = "monitored_app",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class MonitoredApp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val category: AppCategory = AppCategory.OTHER,
    /** Per-app override; null means "use the global cooldown from Settings". */
    val cooldownMinutes: Int? = null
)

@Entity(
    tableName = "app_session",
    indices = [
        Index(value = ["packageName", "startTime"]),
        Index(value = ["startTime"]),
        Index(value = ["status"])
    ]
)
data class AppSession(
    /** A UUID, so ids never collide even if rows are ever merged from a backup. */
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    /** The label at the time of the session, so history survives an uninstall. */
    val appName: String,
    /** When the user made their decision (or dismissed the prompt). */
    val startTime: Long,
    /** When the app left the foreground for good. Null while ACTIVE or when unknown. */
    val endTime: Long? = null,
    /** Real foreground time inside the session. Null = not measured — never assume 0. */
    val actualDurationMs: Long? = null,
    val durationSource: DurationSource = DurationSource.UNKNOWN,
    val sessionType: SessionType,
    val status: SessionStatus,
    val source: CheckInSource = CheckInSource.AUTO
)

@Entity(
    tableName = "intent_response",
    foreignKeys = [
        ForeignKey(
            entity = AppSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            // Deleting a session takes its answers with it; no orphans.
            onDelete = ForeignKey.CASCADE
        )
    ],
    // unique: one answer per session — this is what stops duplicate records.
    indices = [Index(value = ["sessionId"], unique = true)]
)
data class IntentResponse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val intentCategory: IntentCategory,
    /** Optional free text, e.g. "reply to Priya". */
    val description: String = "",
    /** Minutes the user meant to spend. Null = "No limit" or skipped. */
    val plannedDurationMin: Int? = null,
    val decision: Decision,
    val reflection: Reflection? = null,
    val notes: String = "",
    /** The user waved the reflection card away. They can still reflect from history. */
    val reflectionDismissed: Boolean = false,
    val createdAt: Long,
    /**
     * How many times the user answered "Yes, keep going" when their planned time ran out.
     * A fact, not a failure count. Added in DB v4.
     */
    val timeUpExtensions: Int = 0
)

/** An ACTIVE session together with what the user planned for it. */
data class ActivePlan(val session: AppSession, val intent: IntentResponse)

/** A session with its answers, as the screens want it. [intent] is null for dismissals. */
data class SessionWithIntent(
    @Embedded val session: AppSession,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val intent: IntentResponse?
)

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_app ORDER BY appName COLLATE NOCASE")
    fun observeAll(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_app WHERE isEnabled = 1")
    fun observeEnabled(): Flow<List<MonitoredApp>>

    /** Returns the number of rows changed — 0 means "not in the table yet, insert it". */
    @Query("UPDATE monitored_app SET isEnabled = :enabled WHERE packageName = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean): Int

    // IGNORE rather than REPLACE: REPLACE would delete and re-insert, changing the id.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: MonitoredApp): Long
}

@Dao
interface AppSessionDao {
    @Insert
    suspend fun insertSession(session: AppSession)

    @Insert
    suspend fun insertIntent(response: IntentResponse)

    @Update
    suspend fun updateSession(session: AppSession)

    @Query("SELECT * FROM app_session WHERE status = 'ACTIVE'")
    suspend fun activeSessions(): List<AppSession>

    @Query("SELECT * FROM app_session WHERE packageName = :pkg AND status = 'ACTIVE' LIMIT 1")
    suspend fun activeFor(pkg: String): AppSession?

    @Query("SELECT * FROM app_session WHERE packageName = :pkg ORDER BY startTime DESC LIMIT 1")
    suspend fun latestFor(pkg: String): AppSession?

    // @Transaction: a @Relation is filled by a second query; the transaction makes both
    // reads see the same snapshot.
    @Transaction
    @Query("SELECT * FROM app_session WHERE startTime >= :since ORDER BY startTime DESC")
    fun observeSince(since: Long): Flow<List<SessionWithIntent>>

    @Transaction
    @Query("SELECT * FROM app_session ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionWithIntent>>

    @Transaction
    @Query("SELECT * FROM app_session WHERE packageName = :pkg ORDER BY startTime DESC LIMIT :limit")
    fun observeForApp(pkg: String, limit: Int): Flow<List<SessionWithIntent>>

    /** The newest finished session that is still waiting for an (optional) reflection. */
    @Transaction
    @Query(
        "SELECT s.* FROM app_session s JOIN intent_response i ON i.sessionId = s.id " +
            "WHERE s.status IN ('COMPLETED', 'INTERRUPTED') AND i.decision = 'CONTINUE' " +
            "AND i.reflection IS NULL AND i.reflectionDismissed = 0 AND s.startTime >= :since " +
            "ORDER BY s.startTime DESC LIMIT 1"
    )
    fun observePendingReflection(since: Long): Flow<SessionWithIntent?>

    // Enum passed as its name: unambiguous regardless of Room's converter rules for params.
    @Query("UPDATE intent_response SET reflection = :reflection, notes = :notes WHERE sessionId = :sessionId")
    suspend fun setReflection(sessionId: String, reflection: String, notes: String)

    @Query("UPDATE intent_response SET reflectionDismissed = 1 WHERE sessionId = :sessionId")
    suspend fun dismissReflection(sessionId: String)

    @Query("SELECT * FROM intent_response WHERE sessionId = :sessionId LIMIT 1")
    suspend fun intentFor(sessionId: String): IntentResponse?

    // Incremented in SQL, not read-modify-write, so two quick answers can't lose a count.
    @Query("UPDATE intent_response SET timeUpExtensions = timeUpExtensions + 1 WHERE sessionId = :sessionId")
    suspend fun addTimeUpExtension(sessionId: String)
}
