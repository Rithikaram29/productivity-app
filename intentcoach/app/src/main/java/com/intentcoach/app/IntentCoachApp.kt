package com.intentcoach.app

import android.app.Application
import androidx.room.Room
import com.intentcoach.app.data.AppDatabase

class IntentCoachApp : Application() {
    lateinit var db: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "intentcoach.db")
            // v1 -> v2 adds focus_session. A real migration (not destructive) so any
            // intent_log history already on the device survives the upgrade.
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }
}
