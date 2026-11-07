package com.intentcoach.app.util

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings

object Permissions {

    /**
     * Usage Access is an "app op", not a normal runtime permission, so it is checked through
     * AppOpsManager.
     *
     * Two compatibility details:
     *  - unsafeCheckOpNoThrow() only exists on API 29+. minSdk is 26, so older devices use
     *    the (since deprecated) checkOpNoThrow(), which does the same thing there. Calling
     *    the new method on API 26–28 would crash with NoSuchMethodError.
     *  - Some devices report MODE_DEFAULT, meaning "fall back to the manifest permission
     *    check" — so in that case ask the PackageManager-level permission too.
     */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)
}
