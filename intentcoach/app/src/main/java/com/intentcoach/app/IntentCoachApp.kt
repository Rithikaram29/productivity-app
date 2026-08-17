package com.intentcoach.app

import android.app.Application
import androidx.room.Room
import com.intentcoach.app.data.AppDatabase

class IntentCoachApp : Application() {
    lateinit var db: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "intentcoach.db").build()
    }
}
