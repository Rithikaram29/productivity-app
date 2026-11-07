package com.intentcoach.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intentcoach.app.container
import com.intentcoach.app.data.Settings
import com.intentcoach.app.detection.AppWatchService
import com.intentcoach.app.util.Permissions
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onManageApps: () -> Unit, onOpenPrivacy: () -> Unit, onDataDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var usage by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(true) }
    var cooldown by remember { mutableIntStateOf(Settings.DEFAULT_COOLDOWN_MIN) }
    var defaultDuration by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // Re-read everything on resume: the user may have just granted or revoked a permission.
    OnResume {
        usage = Permissions.hasUsageAccess(context)
        overlay = Permissions.hasOverlay(context)
        notifications = PermissionIntents.hasNotifications(context)
        enabled = Settings.isEnabled(context)
        auto = Settings.isAutoIntervention(context)
        cooldown = Settings.cooldownMinutes(context)
        defaultDuration = Settings.defaultDurationMinutes(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifications = granted
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle("Settings")

        val canRun = usage && overlay
        ToggleRow(
            title = "IntentCoach is on",
            subtitle = when {
                enabled && !canRun -> "On, but a permission below is missing — it can't notice app launches."
                enabled -> "Watching for the apps you picked. Nothing leaves your device."
                !canRun -> "Grant Usage Access and Display over other apps to turn it on."
                else -> "Off. Manual check-ins from Home still work."
            },
            checked = enabled,
            // Turning off must always work, even if a permission was revoked meanwhile.
            enabledSwitch = enabled || canRun,
            onChange = { on ->
                if (on) {
                    AppWatchService.start(context)
                    if (!notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    AppWatchService.stop(context)
                }
                enabled = Settings.isEnabled(context)
            }
        )
        ToggleRow(
            title = "Automatic check-ins",
            subtitle = "Ask when a monitored app opens. Off = only the manual check-in on Home.",
            checked = auto,
            onChange = { auto = it; Settings.setAutoIntervention(context, it) }
        )

        CoachCard {
            Text("Ask again after", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                "After you leave an app or answer, it won't ask again for this long.",
                color = Muted, fontSize = 13.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            PillRow {
                Settings.COOLDOWN_CHOICES.forEach { m ->
                    Pill(if (m == 0) "Every time" else "$m min", cooldown == m, {
                        cooldown = m
                        Settings.setCooldownMinutes(context, m)
                    })
                }
            }
        }

        CoachCard {
            Text("Default duration", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("Pre-selected in the check-in. You can always change it there.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            PillRow {
                Pill("None", defaultDuration == null, { defaultDuration = null; Settings.setDefaultDurationMinutes(context, null) })
                Settings.DURATION_CHOICES.filterNotNull().forEach { m ->
                    Pill("$m min", defaultDuration == m, { defaultDuration = m; Settings.setDefaultDurationMinutes(context, m) })
                }
            }
        }

        CoachCard(onClick = onManageApps) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monitored apps", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("›", color = Muted, fontSize = 20.sp)
            }
        }

        SectionLabel("Permissions")
        PermissionRow(
            "Usage Access",
            "Lets IntentCoach see which app is on screen and for how long. Read on this phone only.",
            usage
        ) { PermissionIntents.openUsageAccess(context) }
        PermissionRow(
            "Display over other apps",
            "Lets the check-in appear on top of the app you just opened.",
            overlay
        ) { PermissionIntents.openOverlay(context) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                "Notifications",
                "Optional. Shows the small \"IntentCoach is on\" notice Android requires while it runs.",
                notifications
            ) { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        BatteryOptimisationCard()

        SectionLabel("Privacy")
        CoachCard(onClick = onOpenPrivacy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Privacy policy", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("›", color = Muted, fontSize = 20.sp)
            }
        }
        SecondaryButton("Delete all local data", onClick = { confirmDelete = true }, enabled = !deleting)
        Text("IntentCoach 0.2.0 · no account, no servers, no analytics", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Field,
            title = { Text("Delete everything?", color = Cream) },
            text = {
                Text(
                    "This erases every check-in, reflection, focus session, monitored app and setting " +
                        "on this phone, and turns IntentCoach off. It can't be undone.",
                    color = Muted, fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deleting = true
                    scope.launch {
                        context.container.deleteAllData()
                        deleting = false
                        onDataDeleted()
                    }
                }) { Text("Delete", color = Clay) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Muted) }
            }
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabledSwitch: Boolean = true
) {
    CoachCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabledSwitch,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink, checkedTrackColor = Lime,
                    uncheckedThumbColor = Muted, uncheckedTrackColor = FieldBorder, uncheckedBorderColor = FieldBorder
                )
            )
        }
    }
}

/** Plain-language privacy policy, shipped inside the app. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Back", color = Muted) }
        ScreenTitle("Privacy policy", "Short version: everything stays on your phone.")
        PolicySection(
            "What IntentCoach stores",
            "The apps you choose to monitor; each check-in (what you said, how long you planned, whether you " +
                "continued or turned back); how long the app was then on screen; optional reflections and notes; " +
                "focus sessions; and your settings."
        )
        PolicySection(
            "Where it goes",
            "Nowhere. The app has no internet permission, no account, no servers, no analytics and no ads. " +
                "Data is stored in the app's private storage on this phone and is excluded from cloud backup."
        )
        PolicySection(
            "Usage Access",
            "With your permission, IntentCoach reads Android's usage events to notice when a monitored app opens " +
                "and to measure how long it stays on screen. It only uses this for the apps you picked and for " +
                "totals shown to you. It does not read what you do inside apps."
        )
        PolicySection(
            "Display over other apps",
            "Used only to show the check-in on top of the app you opened."
        )
        PolicySection(
            "Your control",
            "Turn IntentCoach off any time in Settings. \"Delete all local data\" erases everything immediately. " +
                "Uninstalling the app also removes all of its data."
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    CoachCard {
        Text(title, color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
