package com.intentcoach.app.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.intentcoach.app.container
import com.intentcoach.app.data.Settings
import com.intentcoach.app.domain.InterventionPolicy
import com.intentcoach.app.domain.PromptContext
import com.intentcoach.app.domain.PromptDecision
import com.intentcoach.app.overlay.InterruptActivity
import com.intentcoach.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DETECTION STRATEGY (deliberately the lower-risk one):
 *
 * A foreground service polls UsageStatsManager every ~800 ms for the most recent
 * foreground app. When a *monitored* app comes to the front, InterventionPolicy decides
 * whether to ask, and if so we start InterruptActivity on top of it.
 *
 * What this can NOT do — and no supported API can: intercept a launch before the app
 * is visible. UsageStatsManager reports events after the fact, so the user may glimpse
 * the app for ~0.5–1 s before the check-in lands. Starting an activity from the
 * background is only allowed because the user granted "Display over other apps".
 *
 * Why not AccessibilityService? It is instant, but Play restricts it to genuine
 * accessibility use and this app category is a common rejection. Not used.
 *
 * The service also closes finished sessions (SessionRepository.reconcile) every
 * RECONCILE_EVERY_MS, so durations are measured while the usage events are still fresh.
 *
 * KNOWN LIMITATIONS on real devices: OEM battery killers (Xiaomi, Samsung, Oppo…) may stop
 * this service; polling lag; split-screen is not modelled.
 */
class AppWatchService : Service() {

    // Default dispatcher: the work is small CPU + Binder calls; DB calls hop to Room's own
    // executor. SupervisorJob so one failed tick never kills the loop.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastForegroundPackage: String? = null
    private var lastReconcileAt = 0L

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        scope.launch {
            while (isActive) {
                try {
                    tick()
                } catch (e: Exception) {
                    // A bad tick (e.g. permission revoked mid-query) must not stop detection.
                    Log.w(TAG, "tick failed", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Why this branch exists: minSdk is 26, but the "specialUse" foreground-service type —
     * and its permission FOREGROUND_SERVICE_SPECIAL_USE — only exist on API 34+. On 34+
     * the type MUST be passed (else MissingForegroundServiceTypeException); below 34 the
     * constant does not exist and the plain two-argument call is the only correct one.
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
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun tick() {
        val c = container
        val now = System.currentTimeMillis()

        if (now - lastReconcileAt > RECONCILE_EVERY_MS) {
            c.sessions.reconcile(now)
            lastReconcileAt = now
        }

        val current = c.usage.currentForeground(now, LOOKBACK_MS) ?: return

        // Our own screens (the check-in, the app) are transparent to this logic. Leaving
        // lastForegroundPackage untouched is what stops "answer the prompt -> the app
        // reappears -> prompt again" from looping.
        if (current == packageName) return

        val changed = current != lastForegroundPackage
        lastForegroundPackage = current
        if (!changed) return
        if (current !in c.apps.monitoredPackages.value) return

        // Close anything that has really ended before deciding, so returning to an app
        // after a long break is a new decision, not part of the old session.
        c.sessions.reconcile(now)
        lastReconcileAt = now

        val decision = InterventionPolicy.decide(
            PromptContext(
                enabled = Settings.isEnabled(this),
                autoIntervention = Settings.isAutoIntervention(this),
                isMonitored = true,
                promptShowing = InterruptActivity.isShowing,
                hasActiveSession = c.sessions.activeFor(current) != null,
                lastActivityAt = c.sessions.lastActivityAt(current),
                cooldownMs = Settings.cooldownMinutes(this) * 60_000L,
                now = now
            )
        )
        if (decision == PromptDecision.PROMPT) {
            // startActivity from the main thread, as the framework expects.
            withContext(Dispatchers.Main) { launchInterrupt(current) }
        }
    }

    private fun launchInterrupt(pkg: String) {
        startActivity(InterruptActivity.intent(this, pkg, InterruptActivity.Mode.AUTO))
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, "IntentCoach running",
            NotificationManager.IMPORTANCE_MIN
        )
        mgr.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IntentCoach is on")
            .setContentText("It will ask before your chosen apps open. Nothing leaves this phone.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AppWatchService"
        private const val POLL_INTERVAL_MS = 800L
        private const val LOOKBACK_MS = 3_000L
        private const val RECONCILE_EVERY_MS = 15_000L
        private const val CHANNEL_ID = "intent_coach_service"
        private const val NOTIF_ID = 1

        /**
         * Starting and stopping both record the user's choice in Settings, so the flag
         * and the service can never drift apart — BootReceiver reads it back after a reboot.
         */
        fun start(context: Context) {
            Settings.setEnabled(context, true)
            context.startForegroundService(Intent(context, AppWatchService::class.java))
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
