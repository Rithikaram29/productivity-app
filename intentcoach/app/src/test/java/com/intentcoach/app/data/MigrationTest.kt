package com.intentcoach.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Builds a real v2 database file by hand (the shape the v0.1 app shipped), then lets Room
 * open it at v3. Room validates the migrated schema against the entities on open, so this
 * test fails if MIGRATION_2_3's SQL drifts from what Room expects — and it checks the old
 * rows were converted honestly.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private lateinit var context: Context
    private val name = "migration-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE `intent_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`packageName` TEXT NOT NULL, `statedIntent` TEXT NOT NULL, `outcome` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE `focus_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`plannedMinutes` INTEGER NOT NULL, `actualMs` INTEGER NOT NULL, `outcome` TEXT NOT NULL, " +
                    "`label` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL)"
            )
            db.execSQL("INSERT INTO intent_log VALUES (1, 'com.instagram.android', 'Reply to Priya', 'PROCEEDED', 1000)")
            db.execSQL("INSERT INTO intent_log VALUES (2, 'com.instagram.android', '(habit)', 'HABIT', 2000)")
            db.execSQL("INSERT INTO intent_log VALUES (3, 'com.reddit.frontpage', 'Call mum', 'REDIRECTED', 3000)")
            db.execSQL("INSERT INTO focus_session VALUES (1, 25, 1500000, 'COMPLETED', '', 0, 1500000)")
            db.version = 2
        }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun `v2 data survives the move to v3`() = runBlocking {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        try {
            val sessions = db.appSessionDao().observeRecent(10).first().associateBy { it.session.id }
            assertEquals(3, sessions.size)

            val proceeded = sessions.getValue("legacy-1")
            assertEquals(SessionType.INTENTIONAL, proceeded.session.sessionType)
            assertEquals(SessionStatus.COMPLETED, proceeded.session.status)
            assertEquals(DurationSource.UNKNOWN, proceeded.session.durationSource)
            assertNull(proceeded.session.actualDurationMs) // never invented
            assertEquals("Reply to Priya", proceeded.intent?.description)
            assertEquals(Decision.CONTINUE, proceeded.intent?.decision)

            val habit = sessions.getValue("legacy-2")
            assertEquals(SessionType.UNPLANNED, habit.session.sessionType)
            assertEquals(IntentCategory.JUST_CHECKING, habit.intent?.intentCategory)
            assertEquals("", habit.intent?.description)

            val redirected = sessions.getValue("legacy-3")
            assertEquals(SessionStatus.TURNED_BACK, redirected.session.status)
            assertEquals(Decision.GO_BACK, redirected.intent?.decision)

            // Old rows must not trigger reflection prompts.
            assertNull(db.appSessionDao().observePendingReflection(0).first())

            val focus = db.focusSessionDao().recent(10).single()
            assertEquals(FocusCategory.WORK, focus.category)
            assertEquals(SessionOutcome.COMPLETED, focus.outcome)
        } finally {
            db.close()
        }
    }

    @Test
    fun `a check-in is stored once with its answer`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = SessionRepository(db, UsageRepository(context))
            val id = repo.recordCheckIn(
                packageName = "com.instagram.android", appName = "Instagram", source = CheckInSource.AUTO,
                intent = IntentCategory.BORED, description = "  ", plannedMinutes = 10,
                decision = Decision.CONTINUE, now = 5_000
            )
            val all = db.appSessionDao().observeRecent(10).first()
            assertEquals(1, all.size)
            val r = all.single()
            assertEquals(id, r.session.id)
            assertEquals(SessionStatus.ACTIVE, r.session.status)
            assertEquals(SessionType.UNPLANNED, r.session.sessionType)
            assertEquals(10, r.intent?.plannedDurationMin)
            assertEquals("", r.intent?.description)

            repo.saveReflection(id, Reflection.PARTIALLY, "ok")
            assertEquals(Reflection.PARTIALLY, db.appSessionDao().observeRecent(10).first().single().intent?.reflection)
        } finally {
            db.close()
        }
    }

    @Test
    fun `a dismissal is unknown, has no answer, and starts the cooldown`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = SessionRepository(db, UsageRepository(context))
            repo.recordDismissed("com.instagram.android", "Instagram", CheckInSource.AUTO, now = 9_000)
            val r = db.appSessionDao().observeRecent(10).first().single()
            assertEquals(SessionType.UNKNOWN, r.session.sessionType)
            assertEquals(SessionStatus.DISMISSED, r.session.status)
            assertNull(r.intent)
            assertEquals(9_000L, repo.lastActivityAt("com.instagram.android"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `with no usage evidence an old active session closes as unknown, not zero`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            // Robolectric has no real usage events: either access is denied or the event list
            // is empty. Both must end the same way — closed, duration unknown.
            val repo = SessionRepository(db, UsageRepository(context))
            val id = repo.recordCheckIn(
                "com.instagram.android", "Instagram", CheckInSource.MANUAL,
                IntentCategory.SOMETHING_SPECIFIC, "", null, Decision.CONTINUE, now = 1_000
            )
            repo.reconcile(now = 1_000 + 10 * 60_000L)
            val s = db.appSessionDao().observeRecent(10).first().single().session
            assertEquals(id, s.id)
            assertEquals(SessionStatus.COMPLETED, s.status)
            assertEquals(DurationSource.UNKNOWN, s.durationSource)
            assertNull(s.actualDurationMs)
        } finally {
            db.close()
        }
    }
}
