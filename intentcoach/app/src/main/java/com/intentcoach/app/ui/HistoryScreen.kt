package com.intentcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intentcoach.app.AppContainer
import com.intentcoach.app.data.Decision
import com.intentcoach.app.data.Reflection
import com.intentcoach.app.data.SessionStatus
import com.intentcoach.app.data.SessionWithIntent
import com.intentcoach.app.domain.TimeMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class HistoryViewModel(private val c: AppContainer, packageName: String?) : ViewModel() {
    /** null until the first emission, so the screen can tell "loading" from "empty". */
    val records: StateFlow<List<SessionWithIntent>?> =
        (if (packageName == null) c.sessions.observeRecent(300) else c.sessions.observeForApp(packageName, 300))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { c.sessions.reconcile() }
    }

    fun saveReflection(sessionId: String, reflection: Reflection, notes: String) {
        c.appScope.launch { c.sessions.saveReflection(sessionId, reflection, notes) }
    }
}

/** Every check-in, newest first. With [packageName], just that app's. Reflect from here any time. */
@Composable
fun HistoryScreen(packageName: String?, onBack: () -> Unit) {
    // The key makes the all-apps and per-app histories separate ViewModels.
    val vm = appViewModelKeyed(packageName ?: "all") { HistoryViewModel(it, packageName) }
    val records by vm.records.collectAsState()
    OnResume { vm.refresh() }
    val pm = LocalContext.current.packageManager
    val zone = remember { ZoneId.systemDefault() }
    var reflecting by remember { mutableStateOf<SessionWithIntent?>(null) }

    val title = packageName?.let { appLabel(pm, it, it) } ?: "Session history"

    Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = Muted) }
        }
        ScreenTitle(title, "Tap a session to add or change a reflection.")
        Spacer(Modifier.height(12.dp))

        val list = records
        when {
            list == null -> Unit
            list.isEmpty() -> Text("No sessions yet.", color = Muted, fontSize = 15.sp)
            else -> {
                val grouped = list.groupBy { TimeMath.dayOf(it.session.startTime, zone) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    grouped.forEach { (day, dayRecords) ->
                        item(key = "h-$day") {
                            SectionLabel(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                        }
                        items(dayRecords, key = { it.session.id }) { r ->
                            val canReflect = r.intent?.decision == Decision.CONTINUE && r.session.status != SessionStatus.ACTIVE
                            SessionRow(
                                record = r,
                                label = appLabel(pm, r.session.packageName, r.session.appName),
                                zone = zone,
                                onClick = if (canReflect) ({ reflecting = r }) else null
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    reflecting?.let { r ->
        ReflectionDialog(
            record = r,
            label = appLabel(pm, r.session.packageName, r.session.appName),
            onSave = { reflection, note -> vm.saveReflection(r.session.id, reflection, note) },
            onClose = { reflecting = null }
        )
    }
}

@Composable
private fun SessionRow(record: SessionWithIntent, label: String, zone: ZoneId, onClick: (() -> Unit)?) {
    val s = record.session
    val time = remember(s.startTime) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(s.startTime).atZone(zone))
    }
    val (statusText, statusColor) = statusLabel(s.status)
    CoachCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(time, color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        val intent = record.intent
        Text(
            if (intent == null) "No answer given"
            else intent.intentCategory.label + if (intent.description.isNotBlank()) " — ${intent.description}" else "",
            color = Cream, fontSize = 14.sp, lineHeight = 19.sp
        )
        Spacer(Modifier.height(4.dp))
        Row {
            Text(statusText, color = statusColor, fontSize = 12.sp)
            if (intent != null && s.status != SessionStatus.TURNED_BACK) {
                Text("  ·  " + plannedVsActual(record), color = Muted, fontSize = 12.sp)
            }
        }
        val reflection = intent?.reflection
        if (intent != null && reflection != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Reflection: ${reflection.label}" + if (intent.notes.isNotBlank()) " — ${intent.notes}" else "",
                color = Lavender, fontSize = 12.sp
            )
        }
    }
}

private fun statusLabel(status: SessionStatus): Pair<String, Color> = when (status) {
    SessionStatus.ACTIVE -> "In progress" to Lime
    SessionStatus.COMPLETED -> "Went in" to Muted
    SessionStatus.INTERRUPTED -> "Cut short by a restart" to Muted
    SessionStatus.DISMISSED -> "Closed without answering" to Muted
    SessionStatus.TURNED_BACK -> "Turned back" to Mint
}
