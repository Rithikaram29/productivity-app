package com.intentcoach.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.intentcoach.app.data.Settings as CoachSettings

/**
 * Xiaomi, Samsung, Oppo and friends kill background services aggressively. When that
 * happens Intent Coach silently stops noticing app launches — the worst kind of failure,
 * because nothing appears broken.
 *
 * This card explains that and points at the exemption setting. It shows up only when the
 * app is NOT already exempt, and once dismissed it stays dismissed (see
 * Settings.isBatteryCardDismissed). One ask, not a nag.
 */
@Composable
fun BatteryOptimisationCard() {
    val context = LocalContext.current

    var dismissed by remember { mutableStateOf(CoachSettings.isBatteryCardDismissed(context)) }
    var exempt by remember { mutableStateOf(isIgnoringBatteryOptimisations(context)) }

    // The user grants the exemption in Settings and comes back, so re-check on resume —
    // the card should disappear on its own once the problem is solved.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exempt = isIgnoringBatteryOptimisations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (dismissed || exempt) return

    Column {
        Surface(color = Field, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Android may stop this app in the background",
                    color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Some phones shut down background apps to save battery. If that happens " +
                        "to Intent Coach, it quietly stops noticing when you open things. " +
                        "Excluding it from battery optimisation keeps it working.",
                    color = Muted, fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { openBatterySettings(context) }) {
                        Text("Open settings", color = Accent, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        CoachSettings.dismissBatteryCard(context)
                        dismissed = true
                    }) {
                        Text("Not now", color = Muted, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun isIgnoringBatteryOptimisations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Opens the battery-optimisation list, where the user finds Intent Coach and excludes it.
 *
 * Note this is NOT ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which pops the one-tap
 * dialog: that intent needs the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission, which
 * Play treats as restricted and rejects for most app categories. Two extra taps for the
 * user is a better trade than a policy fight.
 *
 * Some OEM builds ship no such screen at all, so fall back to the app's own settings page.
 */
private fun openBatterySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
}
