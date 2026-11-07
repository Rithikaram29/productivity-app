package com.intentcoach.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Everything here stays on the device. No network code touches these tables.
 * That is the entire privacy promise: there is nowhere for this data to go.
 */

enum class Outcome { REDIRECTED, PROCEEDED, HABIT }

@Entity(tableName = "intent_log")
data class IntentLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val statedIntent: String,
    val outcome: Outcome,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface IntentLogDao {
    @Insert
    suspend fun insert(log: IntentLog)

    @Query("SELECT COUNT(*) FROM intent_log WHERE packageName = :pkg AND timestamp >= :since")
    suspend fun countForAppSince(pkg: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM intent_log WHERE outcome = 'REDIRECTED' AND timestamp >= :since")
    suspend fun redirectsSince(since: Long): Int

    @Query("SELECT * FROM intent_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<IntentLog>

    /**
     * Every log in a time window, newest first.
     *
     * The insights screen computes all of its numbers from this ONE query rather than
     * running several aggregate queries. Two reasons: the volume is tiny (a handful of
     * interrupts a day), and a single read is a single consistent snapshot — separate
     * COUNT queries could disagree with each other if a log lands between them. The
     * arithmetic then lives in Stats.kt, where it is pure and unit-testable.
     */
    @Query("SELECT * FROM intent_log WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun since(since: Long): List<IntentLog>
}

@Database(entities = [IntentLog::class, FocusSession::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun intentLogDao(): IntentLogDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        /**
         * v1 -> v2 adds the focus_session table (pomodoro sessions + the reward system).
         *
         * The CREATE TABLE below must match Room's own generated schema for [FocusSession]
         * exactly, or Room rejects the migration on open with a schema-mismatch. If you
         * ever change that entity, regenerate this statement to match (turn on
         * exportSchema and read the generated .json, or copy Room's expected SQL from the
         * mismatch error).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `focus_session` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`plannedMinutes` INTEGER NOT NULL, " +
                        "`actualMs` INTEGER NOT NULL, " +
                        "`outcome` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`endedAt` INTEGER NOT NULL)"
                )
            }
        }
    }
}
