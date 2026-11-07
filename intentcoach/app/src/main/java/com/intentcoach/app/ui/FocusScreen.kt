package com.intentcoach.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intentcoach.app.AppContainer
import com.intentcoach.app.data.CompanionMood
import com.intentcoach.app.data.FocusCategory
import com.intentcoach.app.data.FocusSession
import com.intentcoach.app.data.SessionOutcome
import com.intentcoach.app.data.TimeOfDayClock
import com.intentcoach.app.domain.ActiveFocus
import com.intentcoach.app.domain.Format
import com.intentcoach.app.domain.Insights
import com.intentcoach.app.domain.TimeMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class FocusViewModel(private val c: AppContainer) : ViewModel() {
    val active: StateFlow<ActiveFocus?> = c.focus.active
    val history: StateFlow<List<FocusSession>> =
        c.focus.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _completed = MutableStateFlow<FocusSession?>(null)
    /** The session that just ended, for the completion screen. */
    val completed: StateFlow<FocusSession?> = _completed.asStateFlow()

    private var finishing = false

    init {
        // A timer that ran out while the app was closed: save it and show the completion.
        if (c.focus.active.value?.isFinished(System.currentTimeMillis()) == true) end()
    }

    fun start(category: FocusCategory, label: String, minutes: Int) = c.focus.start(category, label, minutes)
    fun pause() = c.focus.pause()
    fun resume() = c.focus.resume()

    /** Ends the timer (completed if it reached zero, otherwise ended early) and saves it. */
    fun end() {
        if (finishing) return // the ticker and the button can both ask; save once
        finishing = true
        viewModelScope.launch {
            _completed.value = c.focus.finish()
            finishing = false
        }
    }

    fun closeCompletion() {
        _completed.value = null
    }
}

@Composable
fun FocusScreen() {
    val vm = appViewModel { FocusViewModel(it) }
    val active by vm.active.collectAsState()
    val history by vm.history.collectAsState()
    val completed by vm.completed.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val done = completed
        val running = active
        when {
            done != null -> Completion(done, onClose = vm::closeCompletion)
            running != null -> Running(running, onPause = vm::pause, onResume = vm::resume, onEnd = vm::end)
            else -> {
                Setup(onStart = vm::start)
                FocusHistory(history)
            }
        }
    }
}

@Composable
private fun Setup(onStart: (FocusCategory, String, Int) -> Unit) {
    var category by remember { mutableStateOf(FocusCategory.WORK) }
    var label by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(25) }
    var custom by remember { mutableStateOf(false) }

    ScreenTitle("Focus", "A quiet timer. No blocking — just a little room to think.")
    SectionLabel("What are you focusing on?")
    PillRow {
        FocusCategory.entries.forEach { c -> Pill(c.label, c == category, { category = c }) }
    }
    OutlinedTextField(
        value = label,
        onValueChange = { label = it.take(60) },
        placeholder = {
            Text(if (category == FocusCategory.CUSTOM) "Name it (e.g. Guitar practice)" else "A few words (optional)", color = Muted)
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Field, unfocusedContainerColor = Field,
            focusedTextColor = Cream, unfocusedTextColor = Cream,
            focusedBorderColor = Lime, unfocusedBorderColor = FieldBorder
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
    SectionLabel("How long?")
    PillRow {
        ActiveFocus.PRESETS.forEach { m -> Pill("$m min", !custom && minutes == m, { custom = false; minutes = m }) }
        Pill("Custom", custom, { custom = true })
    }
    if (custom) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { minutes = (minutes - 5).coerceAtLeast(ActiveFocus.MIN_CUSTOM) }) {
                Text("−", color = Cream, fontSize = 22.sp)
            }
            Text("$minutes min", color = Cream, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 12.dp))
            TextButton(onClick = { minutes = (minutes + 5).coerceAtMost(ActiveFocus.MAX_CUSTOM) }) {
                Text("+", color = Cream, fontSize = 22.sp)
            }
        }
    }
    PrimaryButton(
        "Start $minutes minutes",
        onClick = {
            val name = if (category == FocusCategory.CUSTOM && label.isBlank()) "Custom" else label
            onStart(category, name, minutes)
        }
    )
}

@Composable
private fun Running(focus: ActiveFocus, onPause: () -> Unit, onResume: () -> Unit, onEnd: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Re-keyed on focus: pausing/resuming hands us a new ActiveFocus and restarts the ticker.
    // The remaining time is recomputed from timestamps each second, never counted down,
    // so it is correct even after the app was closed.
    LaunchedEffect(focus) {
        while (true) {
            now = System.currentTimeMillis()
            if (focus.isFinished(now)) {
                onEnd()
                break
            }
            delay(1_000)
        }
    }
    val progress = focus.elapsedMs(now).toFloat() / focus.plannedMs.coerceAtLeast(1L)
    val title = if (focus.category == FocusCategory.CUSTOM) focus.label.ifBlank { "Custom" } else focus.category.label

    Spacer(Modifier.height(8.dp))
    Text(title, color = Muted, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
    if (focus.label.isNotBlank() && focus.category != FocusCategory.CUSTOM) {
        Text(focus.label, color = Cream, fontSize = 16.sp)
    }
    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(240.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(FieldBorder, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                Ember, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Format.clock(focus.remainingMs(now)), color = Cream, fontSize = 44.sp, fontFamily = FontFamily.Serif)
            Text(if (focus.isPaused) "Paused" else "of ${focus.plannedMinutes} min", color = Muted, fontSize = 13.sp)
        }
    }
    CompanionRoom(mood = CompanionMood.CONTENT, time = TimeOfDayClock.now(), height = 130.dp)
    if (focus.isPaused) PrimaryButton("Resume", onClick = onResume) else SecondaryButton("Pause", onClick = onPause)
    TextButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
        Text("End session now", color = Muted)
    }
    InfoNote("The timer keeps time even if you leave the app. Come back whenever.")
}

@Composable
private fun Completion(session: FocusSession, onClose: () -> Unit) {
    val completed = session.outcome == SessionOutcome.COMPLETED
    Spacer(Modifier.height(8.dp))
    ScreenTitle(
        if (completed) "Session complete" else "Session ended",
        if (completed) "You stayed with it for ${session.plannedMinutes} minutes. The room feels warmer."
        else "You focused for ${Format.duration(session.actualMs)}. Stopping is allowed — come back when you're ready."
    )
    CompanionRoom(
        mood = if (completed) CompanionMood.THRIVING else CompanionMood.WILTED,
        time = TimeOfDayClock.now(),
        celebrate = completed
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("${session.plannedMinutes}m", "Planned", Modifier.weight(1f))
        StatTile(Format.duration(session.actualMs), "Focused", Modifier.weight(1f), valueColor = Ember)
    }
    PrimaryButton("Done", onClick = onClose)
}

@Composable
private fun FocusHistory(history: List<FocusSession>) {
    val zone = remember { ZoneId.systemDefault() }
    val now = System.currentTimeMillis()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(Format.duration(Insights.focusMs(history, TimeMath.startOfToday(now, zone))), "Today", Modifier.weight(1f), valueColor = Ember)
        StatTile(Format.duration(Insights.focusMs(history, TimeMath.startOfWindow(now, zone, 7))), "Last 7 days", Modifier.weight(1f), valueColor = Ember)
    }
    if (history.isEmpty()) return
    SectionLabel("Recent sessions")
    val fmt = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    history.take(15).forEach { s ->
        CoachCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val name = if (s.category == FocusCategory.CUSTOM) s.label.ifBlank { "Custom" } else s.category.label
                    Text(name, color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(fmt.format(Instant.ofEpochMilli(s.endedAt).atZone(zone)), color = Muted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${Format.duration(s.actualMs)} / ${s.plannedMinutes}m", color = Cream, fontSize = 14.sp)
                    Text(
                        if (s.outcome == SessionOutcome.COMPLETED) "Completed" else "Ended early",
                        color = if (s.outcome == SessionOutcome.COMPLETED) Lime else Muted, fontSize = 12.sp
                    )
                }
            }
        }
    }
}
