package com.intentcoach.app.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.intentcoach.app.data.WatchedApps
import com.intentcoach.app.overlay.InterruptActivity

/**
 * MVP DETECTION STRATEGY (deliberately the lower-risk one):
 *
 * We poll UsageStatsManager every ~800ms for the most recent foreground app.
 * When a *watched* app comes to the foreground AND it wasn't already foreground,
 * we launch the interrupt screen.
 *
 * Why polling and not AccessibilityService?
 *   - AccessibilityService is instant and cleaner, BUT it's the single most
 *     scrutinised permission on the Play Store for this app category and a
 *     common rejection reason. Ship the polling version first, prove the
 *     concept, then decide if the ~1s latency is worth the review risk later.
 *
 * KNOWN LIMITATIONS you will hit on a real device (this is expected — debug in
 * Android Studio against real phones, behaviour varies by OEM and OS version):
 *   - Some manufacturers (Xiaomi, Samsung, etc.) aggressively kill background
 *     services. You may need battery-optimisation exemptions.
 *   - Polling has ~0.5–1s lag; the user may see a flash of Instagram first.
 *   - USAGE_STATS bucketing granularity differs across Android versions.
 */
class AppWatchService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPackage: String? = null
    private var interruptShownFor: String? = null

    private val poller = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        handler.post(poller)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkForeground() {
        val current = currentForegroundApp() ?: return

        // Reset the "already interrupted" flag once the user leaves the watched app.
        if (current != interruptShownFor) {
            interruptShownFor = null
        }

        val changed = current != lastForegroundPackage
        lastForegroundPackage = current

        if (!changed) return
        if (current == packageName) return // ignore our own app
        if (!WatchedApps.isWatched(this, current)) return
        if (current == interruptShownFor) return // don't loop

        interruptShownFor = current
        launchInterrupt(current)
    }

    private fun currentForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - LOOKBACK_MS, now)
        val event = android.app.usage.UsageEvents.Event()
        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    private fun launchInterrupt(pkg: String) {
        val intent = Intent(this, InterruptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(InterruptActivity.EXTRA_PACKAGE, pkg)
        }
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, "Intent Coach running",
            NotificationManager.IMPORTANCE_MIN
        )
        mgr.createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Intent Coach is watching for distractions")
            .setContentText("Tap to open")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 800L
        private const val LOOKBACK_MS = 3_000L
        private const val CHANNEL_ID = "intent_coach_service"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            val i = Intent(context, AppWatchService::class.java)
            context.startForegroundService(i)
        }
    }
}
