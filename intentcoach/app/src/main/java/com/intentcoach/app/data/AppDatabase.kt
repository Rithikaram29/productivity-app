package com.intentcoach.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The one local database. Nothing in it ever leaves the device.
 *
 * History:
 *  - v1: intent_log (one row per prompt)
 *  - v2: + focus_session
 *  - v3: intent_log is replaced by app_session + intent_response, + monitored_app,
 *        focus_session gains `category`. Old intent_log rows are converted, not dropped.
 *
 * Schemas are exported to app/schemas/ (see the ksp block in app/build.gradle.kts), so
 * future migrations can be written and tested against the real generated schema.
 */
@Database(
    entities = [
        FocusSession::class,
        MonitoredApp::class,
        AppSession::class,
        IntentResponse::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun appSessionDao(): AppSessionDao

    companion object {
        /**
         * v1 -> v2 adds the focus_session table. Unchanged from before; still needed so a
         * phone on v1 can walk 1 -> 2 -> 3.
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

        /**
         * v2 -> v3. The CREATE statements must match what Room generates for the
         * entities in Sessions.kt exactly (columns, nullability, index names, foreign key),
         * or Room refuses to open the database. If Room reports a mismatch, the error
         * prints the expected schema — copy it from there. MigrationTest covers this.
         *
         * Converting old rows, honestly:
         *  - REDIRECTED -> TURNED_BACK, nothing to measure.
         *  - PROCEEDED  -> INTENTIONAL (they typed a reason), duration UNKNOWN — v2 never
         *    measured time, and we will not invent a number.
         *  - HABIT      -> JUST_CHECKING / UNPLANNED, duration UNKNOWN.
         * Converted rows are marked reflection-dismissed so the app does not ask the user
         * to reflect on sessions from weeks ago.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `monitored_app` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`appName` TEXT NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`cooldownMinutes` INTEGER)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_monitored_app_packageName` " +
                        "ON `monitored_app` (`packageName`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_session` (" +
                        "`id` TEXT NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`appName` TEXT NOT NULL, " +
                        "`startTime` INTEGER NOT NULL, " +
                        "`endTime` INTEGER, " +
                        "`actualDurationMs` INTEGER, " +
                        "`durationSource` TEXT NOT NULL, " +
                        "`sessionType` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_session_packageName_startTime` " +
                        "ON `app_session` (`packageName`, `startTime`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_session_startTime` ON `app_session` (`startTime`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_session_status` ON `app_session` (`status`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `intent_response` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` TEXT NOT NULL, " +
                        "`intentCategory` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`plannedDurationMin` INTEGER, " +
                        "`decision` TEXT NOT NULL, " +
                        "`reflection` TEXT, " +
                        "`notes` TEXT NOT NULL, " +
                        "`reflectionDismissed` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `app_session`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_intent_response_sessionId` " +
                        "ON `intent_response` (`sessionId`)"
                )

                // Carry the v2 history across.
                db.execSQL(
                    "INSERT INTO `app_session` (id, packageName, appName, startTime, endTime, " +
                        "actualDurationMs, durationSource, sessionType, status, source) " +
                        "SELECT 'legacy-' || id, packageName, packageName, timestamp, " +
                        "CASE WHEN outcome = 'REDIRECTED' THEN timestamp ELSE NULL END, " +
                        "CASE WHEN outcome = 'REDIRECTED' THEN 0 ELSE NULL END, " +
                        "CASE WHEN outcome = 'REDIRECTED' THEN 'NOT_APPLICABLE' ELSE 'UNKNOWN' END, " +
                        "CASE WHEN outcome = 'HABIT' THEN 'UNPLANNED' ELSE 'INTENTIONAL' END, " +
                        "CASE WHEN outcome = 'REDIRECTED' THEN 'TURNED_BACK' ELSE 'COMPLETED' END, " +
                        "'AUTO' FROM `intent_log`"
                )
                db.execSQL(
                    "INSERT INTO `intent_response` (sessionId, intentCategory, description, " +
                        "plannedDurationMin, decision, reflection, notes, reflectionDismissed, createdAt) " +
                        "SELECT 'legacy-' || id, " +
                        "CASE WHEN outcome = 'HABIT' THEN 'JUST_CHECKING' ELSE 'SOMETHING_SPECIFIC' END, " +
                        "CASE WHEN outcome = 'HABIT' THEN '' ELSE statedIntent END, " +
                        "NULL, " +
                        "CASE WHEN outcome = 'REDIRECTED' THEN 'GO_BACK' ELSE 'CONTINUE' END, " +
                        "NULL, '', 1, timestamp FROM `intent_log`"
                )
                db.execSQL("DROP TABLE IF EXISTS `intent_log`")

                // Existing focus sessions predate categories; WORK is the neutral default.
                db.execSQL(
                    "ALTER TABLE `focus_session` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'WORK'"
                )
            }
        }
    }
}
