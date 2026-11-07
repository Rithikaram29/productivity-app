package com.intentcoach.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/** One permission, what it is for, and a way to grant it. Shared by onboarding and Settings. */
@Composable
fun PermissionRow(title: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    CoachCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(why, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Text("Granted", color = Lime, fontSize = 14.sp)
            } else {
                TextButton(onClick = onGrant) { Text("Grant", color = Clay, fontSize = 14.sp) }
            }
        }
    }
}

/**
 * Both are "special" permissions: there is no popup for them. The user flips a switch in
 * system Settings and comes back, which is why screens re-check on resume (OnResume).
 */
object PermissionIntents {
    fun openUsageAccess(context: Context) {
        safeStart(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun openOverlay(context: Context) {
        safeStart(
            context,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        )
    }

    fun openAppDetails(context: Context) {
        safeStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    /** POST_NOTIFICATIONS only exists on API 33+; below that notifications are always allowed. */
    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Some OEM builds lack a specific settings screen; fall back to the app's own page. */
    private fun safeStart(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        }
    }
}
