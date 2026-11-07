package com.intentcoach.app.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.intentcoach.app.data.Settings
import com.intentcoach.app.util.Permissions

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Three things must all still be true for a restart to be right:
        //   1. the user had it switched on before the reboot, and
        //   2–3. both special permissions survived. They can be revoked in Settings
        //   while the phone is off, and starting without them gives you a foreground
        //   service that burns battery polling an API it is not allowed to read.
        if (!Settings.isEnabled(context)) return
        if (!Permissions.hasUsageAccess(context)) return
        if (!Permissions.hasOverlay(context)) return

        // Safe to call startForegroundService() from here: BOOT_COMPLETED is one of the
        // documented exemptions from the API 26+ background-start restrictions.
        AppWatchService.start(context)
    }
}
