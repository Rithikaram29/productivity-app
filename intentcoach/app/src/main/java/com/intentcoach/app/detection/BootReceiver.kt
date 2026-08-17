package com.intentcoach.app.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Only restart if the user had granted the needed permissions and enabled it.
            // (Add your own "enabled" flag check here before calling start.)
            AppWatchService.start(context)
        }
    }
}
