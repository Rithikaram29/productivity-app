package com.intentcoach.app.ui

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intentcoach.app.data.CompanionMood
import com.intentcoach.app.data.Settings
import com.intentcoach.app.domain.Format
import com.intentcoach.app.domain.Messages
import com.intentcoach.app.overlay.InterruptActivity
import com.intentcoach.app.util.Permissions

@Composable
fun HomeScreen(
    onStartFocus: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenHistory: () -> Unit,
    onManageApps: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val vm = appViewModel { HomeViewModel(it) }
    // collectAsState: the flows are WhileSubscribed, so upstream work stops 5 s after the screen leaves.
    val state by vm.state.collectAsState()

    var autoReady by remember { mutableStateOf(false) }
    OnResume {
        vm.refresh()
        autoReady = Settings.isEnabled(context) && Settings.isAutoIntervention(context) &&
            Permissions.hasUsageAccess(context) && Permissions.hasOverlay(context)
    }
    var pickingApp by remember { mutableStateOf(false) }

    val mood = state.reward?.mood ?: CompanionMood.SLEEPY
    val pm = context.packageManager

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            Messages.greeting(state.timeOfDay),
            color = Cream, fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium
        )
        CompanionRoom(mood = mood, time = state.timeOfDay)
        Text(Messages.gentle(state.today, mood, state.daySeed), color = Muted, fontSize = 15.sp, lineHeight = 21.sp)
        state.reward?.let { r ->
            if (r.warmth > 0 || r.streakDays > 0) {
                Text(
                    "Warmth ${r.warmth}" + if (r.streakDays > 0) " · ${r.streakDays}-day focus streak" else "",
                    color = Ember, fontSize = 13.sp
                )
            }
        }

        if (!autoReady) {
            CoachCard(onClick = onOpenSettings) {
                Text("Automatic check-ins are off", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "You can still check in by hand below. Turn them on in Settings.",
                    color = Muted, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
        }

        state.pendingReflection?.let { p ->
            ReflectionCard(
                record = p,
                label = appLabel(pm, p.session.packageName, p.session.appName),
                onSave = { r, n -> vm.saveReflection(p.session.id, r, n) },
                onDismiss = { vm.dismissReflection(p.session.id) }
            )
        }

        PrimaryButton("Check in before opening an app", onClick = { pickingApp = true })

        SectionLabel("Today")
        TodayGrid(state)

        SectionLabel("Quick actions")
        QuickAction("Start a focus session", "Pick a length and settle in", onStartFocus)
        QuickAction("View insights", "Patterns across today and this week", onOpenInsights)
        QuickAction("Session history", "Everything you've checked in for", onOpenHistory)
        QuickAction(
            "Manage monitored apps",
            if (state.monitoredApps.size == 1) "1 app asks first" else "${state.monitoredApps.size} apps ask first",
            onManageApps
        )
        Spacer(Modifier.height(8.dp))
    }

    if (pickingApp) {
        ManualCheckInDialog(
            apps = state.monitoredApps.map { it.packageName to appLabel(pm, it.packageName, it.appName) },
            onPick = { pkg ->
                pickingApp = false
                context.startActivity(InterruptActivity.intent(context, pkg, InterruptActivity.Mode.MANUAL))
            },
            onManageApps = { pickingApp = false; onManageApps() },
            onClose = { pickingApp = false }
        )
    }
}

@Composable
private fun TodayGrid(state: HomeViewModel.State) {
    val t = state.today
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                state.monitoredUsageMs?.let { Format.duration(it) } ?: "—",
                "Time in monitored apps", Modifier.weight(1f)
            )
            StatTile(Format.duration(state.focusTodayMs).takeIf { state.focusTodayMs > 0 } ?: "0m",
                "Focus time", Modifier.weight(1f), valueColor = Ember)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${t.intentional}", "Intentional", Modifier.weight(1f), valueColor = Lime)
            StatTile("${t.unplanned}", "Unplanned", Modifier.weight(1f))
            StatTile("${t.turnedBack}", "Turned back", Modifier.weight(1f), valueColor = Mint)
        }
        if (state.monitoredUsageMs == null) {
            InfoNote("Time in monitored apps needs Usage Access. Without it, we show nothing rather than guess.")
        }
        if (t.unmeasuredSessions > 0) {
            InfoNote(
                "${t.unmeasuredSessions} ${if (t.unmeasuredSessions == 1) "session" else "sessions"} " +
                    "today couldn't be measured, so their time isn't counted."
            )
        }
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, onClick: () -> Unit) {
    CoachCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Muted, fontSize = 13.sp)
            }
            Text("›", color = Muted, fontSize = 20.sp)
        }
    }
}

/**
 * The manual fallback: works without Usage Access or overlay permission. The user picks
 * the app they were about to open and gets the same three-step check-in.
 */
@Composable
private fun ManualCheckInDialog(
    apps: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onManageApps: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Field,
        title = { Text("Which app were you about to open?", color = Cream, fontSize = 18.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (apps.isEmpty()) {
                    Text("Choose some apps to monitor first.", color = Muted, fontSize = 14.sp)
                }
                apps.forEach { (pkg, label) ->
                    Surface(
                        onClick = { onPick(pkg) },
                        color = Ink,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(label, color = Cream, fontSize = 15.sp, modifier = Modifier.padding(14.dp)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onManageApps) { Text("Choose apps", color = Lime) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel", color = Muted) }
        }
    )
}
