package com.intentcoach.app.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings

object Permissions {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)
}
