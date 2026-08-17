package com.intentcoach.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

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
}

@Database(entities = [IntentLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun intentLogDao(): IntentLogDao
}
