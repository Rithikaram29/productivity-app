package com.intentcoach.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intentcoach.app.data.DurationSource
import com.intentcoach.app.data.Reflection
import com.intentcoach.app.data.SessionWithIntent
import com.intentcoach.app.domain.Format

/**
 * "Did you accomplish what you intended?" — offered, never required. Shown once as a card
 * on Home after a session ends, and any time from history. Planned and actual time are
 * shown side by side as facts; going over is never called a failure.
 */
@Composable
fun ReflectionCard(
    record: SessionWithIntent,
    label: String,
    onSave: (Reflection, String) -> Unit,
    onDismiss: () -> Unit
) {
    CoachCard {
        Text("How did $label go?", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(plannedVsActual(record), color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(12.dp))
        ReflectionForm(onSave = onSave, onSkip = onDismiss, skipLabel = "Not now")
    }
}

@Composable
fun ReflectionDialog(
    record: SessionWithIntent,
    label: String,
    onSave: (Reflection, String) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Field,
        title = { Text("Did you accomplish what you intended?", color = Cream, fontSize = 18.sp) },
        text = {
            Column {
                Text("$label · ${plannedVsActual(record)}", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                ReflectionForm(onSave = { r, n -> onSave(r, n); onClose() }, onSkip = onClose, skipLabel = "Cancel",
                    initial = record.intent?.reflection, initialNote = record.intent?.notes.orEmpty())
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ReflectionForm(
    onSave: (Reflection, String) -> Unit,
    onSkip: () -> Unit,
    skipLabel: String,
    initial: Reflection? = null,
    initialNote: String = ""
) {
    var choice by remember { mutableStateOf(initial) }
    var note by remember { mutableStateOf(initialNote) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Reflection.entries.forEach { r ->
            Pill(r.label, selected = r == choice, onClick = { choice = r }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(280) },
            placeholder = { Text("A note for yourself (optional)", color = Muted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Ink, unfocusedContainerColor = Ink,
                focusedTextColor = Cream, unfocusedTextColor = Cream,
                focusedBorderColor = Lime, unfocusedBorderColor = FieldBorder
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSkip) { Text(skipLabel, color = Muted) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { choice?.let { onSave(it, note) } }, enabled = choice != null) {
                Text("Save", color = if (choice != null) Lime else Muted)
            }
        }
    }
}

/** "Planned 10m · spent 14m" — facts only. Unknown durations say so. */
fun plannedVsActual(record: SessionWithIntent): String {
    val planned = record.intent?.plannedDurationMin?.let { "Planned ${it}m" } ?: "No time planned"
    val s = record.session
    val actual = when {
        s.durationSource == DurationSource.MEASURED && s.actualDurationMs != null -> "spent ${Format.duration(s.actualDurationMs)}"
        s.durationSource == DurationSource.NOT_APPLICABLE -> null
        else -> "time not measured"
    }
    return if (actual == null) planned else "$planned · $actual"
}
