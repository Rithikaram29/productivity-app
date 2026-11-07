package com.intentcoach.app.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.intentcoach.app.data.Settings
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
        startForegroundCompat()
        handler.post(poller)
    }

    /**
     * Why this branch exists:
     *
     * minSdk is 26, but the "specialUse" foreground-service type — and its companion
     * permission FOREGROUND_SERVICE_SPECIAL_USE — only exist on API 34+.
     *
     *   - On API 34+ (targetSdk 35), the platform REQUIRES the service type to be passed
     *     to startForeground(). Omit it and you get MissingForegroundServiceTypeException,
     *     which kills the service the instant it starts.
     *   - Below API 34 that type constant does not exist on the device at all. The plain
     *     two-argument startForeground() is the only correct call, and the manifest's
     *     foregroundServiceType attribute is simply ignored by those platforms.
     *
     * So: same service, two different start contracts, chosen at runtime.
     */
    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkForeground() {
        val current = currentForegroundApp() ?: return

        // Our own interrupt screen must be fully transparent to this logic.
        // Previously the reset below ran while InterruptActivity was foreground,
        // clearing interruptShownFor — so proceeding into the watched app
        // re-triggered the interrupt in an endless loop. Bail out first, and
        // leave lastForegroundPackage/interruptShownFor untouched.
        if (current == packageName) return

        // Reset the "already interrupted" flag once the user leaves the watched app.
        if (current != interruptShownFor) {
            interruptShownFor = null
        }

        val changed = current != lastForegroundPackage
        lastForegroundPackage = current

        if (!changed) return
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

        /**
         * Starting and stopping both record the user's choice in Settings, so the flag
         * and the service can never drift apart — whoever calls start()/stop() does not
         * have to remember to write it, and BootReceiver reads it back after a reboot.
         */
        fun start(context: Context) {
            Settings.setEnabled(context, true)
            val i = Intent(context, AppWatchService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            Settings.setEnabled(context, false)
            // stopService() beats stopSelf() here: it also cancels the START_STICKY
            // contract, so the system will not resurrect the service after the user
            // has explicitly turned it off.
            context.stopService(Intent(context, AppWatchService::class.java))
        }
    }
}
