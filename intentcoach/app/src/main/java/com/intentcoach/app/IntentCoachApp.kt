package com.intentcoach.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.intentcoach.app.data.AppDatabase
import com.intentcoach.app.data.AppsRepository
import com.intentcoach.app.data.FocusRepository
import com.intentcoach.app.data.SessionRepository
import com.intentcoach.app.data.Settings
import com.intentcoach.app.data.UsageRepository
import com.intentcoach.app.data.WatchedApps
import com.intentcoach.app.detection.AppWatchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

class IntentCoachApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Kept for older call sites; prefer [container]. */
    val db: AppDatabase get() = container.db

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Hand-wired dependencies — the whole app's object graph in one screen of code. A DI
 * framework (Hilt) would add a Gradle plugin, annotation processing and a learning curve
 * for a graph this small; this does the same job and is easy to read.
 */
class AppContainer(private val context: Context) {

    /**
     * Lives as long as the process. Writes that must outlive a screen go here — e.g. the
     * check-in screen saves its answer and finish()es at once; launching that save in the
     * activity's lifecycleScope would let onDestroy cancel it before it lands.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "intentcoach.db")
        // Real migrations, never destructive: existing history survives every upgrade.
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .build()

    val usage = UsageRepository(context)
    val sessions = SessionRepository(db, usage)
    val apps = AppsRepository(context, db.monitoredAppDao(), appScope)
    val focus = FocusRepository(context, db.focusSessionDao())

    /**
     * "Delete all local data": stops detection, empties every table and every preference.
     * After this the app is exactly a fresh install.
     */
    suspend fun deleteAllData() {
        AppWatchService.stop(context)
        withContext(Dispatchers.IO) { db.clearAllTables() }
        Settings.clearAll(context)
        context.getSharedPreferences(WatchedApps.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        // The old picker's prefs file still exists on upgraded installs; never re-import it.
        Settings.setLegacyAppsMigrated(context)
        apps.resetCache()
        focus.resetCache()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as IntentCoachApp).container
