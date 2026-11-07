package com.intentcoach.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
// Aliased because android.provider.Settings (used for the permission deep-links below)
// already owns the name `Settings` in this file. Kotlin cannot import both unaliased.
import com.intentcoach.app.data.Settings as CoachSettings
import com.intentcoach.app.data.WatchedApps
import com.intentcoach.app.detection.AppWatchService
import com.intentcoach.app.util.Permissions

/**
 * The app's only real Activity. It hosts three destinations: setup (home), the app
 * picker, and insights.
 *
 * InterruptActivity is deliberately NOT in this graph. It is launched by the service on
 * top of whatever app the user just opened — a separate surface with its own task, not
 * somewhere you navigate to from here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { IntentCoachNav() } }
    }
}

private object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val INSIGHTS = "insights"
}

@Composable
private fun IntentCoachNav() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            SetupScreen(
                onOpenAppPicker = { navController.navigate(Routes.APPS) },
                onOpenInsights = { navController.navigate(Routes.INSIGHTS) }
            )
        }
        // popBackStack() rather than navigate(HOME): going "back" should unwind the
        // stack, not push a second copy of home on top of it.
        composable(Routes.APPS) { AppPickerScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.INSIGHTS) { InsightsScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun SetupScreen(onOpenAppPicker: () -> Unit, onOpenInsights: () -> Unit) {
    val context = LocalContext.current
    var usageGranted by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }
    var overlayGranted by remember { mutableStateOf(Permissions.hasOverlay(context)) }
    // Mirrors the persisted flag rather than the live service — see Settings.kt for why
    // asking the system "is my service running?" is not a question Android answers well.
    var running by remember { mutableStateOf(CoachSettings.isEnabled(context)) }
    // Bumped on resume to force the watched-app count to be re-read after the picker.
    var refreshTick by remember { mutableIntStateOf(0) }

    // LaunchedEffect(Unit) fires only on first composition, so returning from
    // Settings left these stale. Observe ON_RESUME instead.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = Permissions.hasUsageAccess(context)
                overlayGranted = Permissions.hasOverlay(context)
                running = CoachSettings.isEnabled(context)
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Intent Coach", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "When you open a distracting app, we'll ask one question: what did you pick " +
                "up your phone to do? Nothing leaves your device.",
            color = Muted, fontSize = 15.sp, lineHeight = 22.sp
        )
        Spacer(Modifier.height(24.dp))

        BatteryOptimisationCard()

        PermissionRow(
            title = "Usage access",
            why = "Lets us notice when a distracting app opens.",
            granted = usageGranted
        ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        Spacer(Modifier.height(12.dp))

        PermissionRow(
            title = "Draw over other apps",
            why = "Lets us show the question on top of that app.",
            granted = overlayGranted
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }

        Spacer(Modifier.height(24.dp))

        // refreshTick is read here purely so that bumping it on resume re-runs this
        // SharedPreferences read — that is what keeps the count honest after the picker.
        val watchedCount = remember(refreshTick) { WatchedApps.get(context).size }
        NavRow(
            title = "Apps that ask",
            subtitle = if (watchedCount == 1) "1 app selected" else "$watchedCount apps selected",
            onClick = onOpenAppPicker
        )
        Spacer(Modifier.height(12.dp))
        NavRow(
            title = "This week",
            subtitle = "What you reached for, and why",
            onClick = onOpenInsights
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                if (running) AppWatchService.stop(context) else AppWatchService.start(context)
                // start()/stop() write the flag synchronously, so reading it straight
                // back keeps the button honest without waiting for the next resume.
                running = CoachSettings.isEnabled(context)
            },
            // Stopping stays available even if a permission was revoked while running —
            // otherwise the user would be stuck with a service they cannot switch off.
            enabled = running || (usageGranted && overlayGranted),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (running) Field else Accent,
                contentColor = Cream,
                disabledContainerColor = Field,
                disabledContentColor = Muted
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (running) "Stop" else "Start", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            when {
                running -> "Watching for the apps you picked. Nothing leaves your device."
                !usageGranted || !overlayGranted -> "Grant both permissions above to start."
                else -> "Ready when you are."
            },
            color = Muted, fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionRow(title: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    Surface(color = Field, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(why, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Text("Granted", color = Green, fontSize = 14.sp)
            } else {
                TextButton(onClick = onGrant) { Text("Grant", color = Accent, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Field,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Muted, fontSize = 13.sp)
            }
            Text("›", color = Muted, fontSize = 20.sp)
        }
    }
}
