package com.intentcoach.app.overlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intentcoach.app.container
import com.intentcoach.app.domain.CONTINUE_COUNTDOWN_SECONDS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * "Your 10 minutes on Instagram are up. Do you want to continue?"
 *
 * Shown on top of the app by AppWatchService when the session's planned time has really
 * been spent on screen (see TimeUpPolicy).
 *  - Yes, keep going -> a 5-second countdown, then back into the app. Recorded as one
 *    extension; the next ask comes after another block of the planned length.
 *  - No, I'm done    -> home screen. The session then ends normally.
 *
 * It never blocks: Home always works, and the countdown can be cut short with "I'm done".
 * Back does nothing here — the user answers, or leaves with Home.
 */
class TimeUpActivity : ComponentActivity() {

    private var answered = false
    private lateinit var sessionId: String
    private lateinit var packageNameArg: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: run { finish(); return }
        packageNameArg = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }
        val planned = intent.getIntExtra(EXTRA_PLANNED_MIN, 0)
        val extensions = intent.getIntExtra(EXTRA_EXTENSIONS, 0)
        val intentText = intent.getStringExtra(EXTRA_INTENT_TEXT).orEmpty()
        val label = appLabel(packageNameArg)

        setContent {
            TimeUpScreen(
                appLabel = label,
                minutesSoFar = planned * (extensions + 1),
                intentText = intentText,
                onContinueConfirmed = { keepGoing() },
                onDone = { done() }
            )
        }
    }

    override fun onStop() {
        super.onStop()
        // Left with Home (or the screen went off) without answering. Don't re-ask the
        // moment they come back; give it a minute.
        if (!answered && !isChangingConfigurations) {
            snooze(sessionId, UNANSWERED_SNOOZE_MS)
            finish()
        }
    }

    override fun onDestroy() {
        isShowing = false
        super.onDestroy()
    }

    /** Called when the 5-second countdown finishes. */
    private fun keepGoing() {
        if (answered) return
        answered = true
        // Short snooze covers the moment before the database write lands.
        snooze(sessionId, ANSWERED_SNOOZE_MS)
        val c = container
        // appScope: we finish() right away; an activity-scoped write could be cancelled.
        c.appScope.launch { c.sessions.recordTimeUpExtension(sessionId) }
        val launch = packageManager.getLaunchIntentForPackage(packageNameArg)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
        finish()
    }

    private fun done() {
        if (answered) return
        answered = true
        // The session closes on its own once they're away for the grace period. If they
        // come straight back, ask again only after a minute.
        snooze(sessionId, UNANSWERED_SNOOZE_MS)
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_PLANNED_MIN = "planned_min"
        private const val EXTRA_EXTENSIONS = "extensions"
        private const val EXTRA_INTENT_TEXT = "intent_text"

        private const val UNANSWERED_SNOOZE_MS = 60_000L
        private const val ANSWERED_SNOOZE_MS = 30_000L

        /** Read by the service so this and the check-in never stack. */
        @Volatile
        var isShowing: Boolean = false
            private set

        // sessionId -> "don't ask before". In memory only: after a process restart the
        // worst case is one early re-ask, which is acceptable.
        private val snoozes = ConcurrentHashMap<String, Long>()

        fun snoozedUntil(sessionId: String): Long = snoozes[sessionId] ?: 0L

        private fun snooze(sessionId: String, forMs: Long) {
            snoozes[sessionId] = System.currentTimeMillis() + forMs
        }

        fun intent(
            context: Context,
            sessionId: String,
            packageName: String,
            plannedMinutes: Int,
            extensions: Int,
            intentText: String
        ): Intent = Intent(context, TimeUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_PACKAGE, packageName)
            putExtra(EXTRA_PLANNED_MIN, plannedMinutes)
            putExtra(EXTRA_EXTENSIONS, extensions)
            putExtra(EXTRA_INTENT_TEXT, intentText)
        }
    }
}

// Palette v1.1 — same values as InterruptActivity / ui/Palette.kt.
private val Ink = Color(0xFF100B07)
private val Cream = Color(0xFFF5ECDE)
private val Muted = Color(0xFFAC9C8B)
private val Field = Color(0xFF221A13)
private val FieldBorder = Color(0xFF443A31)
private val Lime = Color(0xFFC3E667)
private val Ember = Color(0xFFEBAF5A)

@Composable
private fun TimeUpScreen(
    appLabel: String,
    minutesSoFar: Int,
    intentText: String,
    onContinueConfirmed: () -> Unit,
    onDone: () -> Unit
) {
    // rememberSaveable so rotating mid-countdown doesn't send the user back to the question.
    var countingDown by rememberSaveable { mutableStateOf(false) }
    BackHandler { /* answer, or leave with Home — see class doc */ }

    Box(
        Modifier.fillMaxSize().background(Ink).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!countingDown) {
            Column(Modifier.fillMaxWidth()) {
                Text("Time check", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(24.dp))
                Text(
                    "Your $minutesSoFar minutes on $appLabel are up.",
                    color = Cream, fontSize = 26.sp, lineHeight = 33.sp,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium
                )
                if (intentText.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(color = Field, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("You came here for", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(intentText, color = Cream, fontSize = 16.sp, lineHeight = 22.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Do you want to continue?", color = Cream, fontSize = 18.sp)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { countingDown = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Yes, keep going", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDone,
                    border = BorderStroke(1.dp, FieldBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("No, I'm done", color = Cream, fontSize = 15.sp) }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Either is fine. Going over isn't a failure.",
                    color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            }
        } else {
            Countdown(appLabel = appLabel, onFinished = onContinueConfirmed, onDone = onDone)
        }
    }
}

@Composable
private fun Countdown(appLabel: String, onFinished: () -> Unit, onDone: () -> Unit) {
    var remaining by rememberSaveable { mutableIntStateOf(CONTINUE_COUNTDOWN_SECONDS) }
    // Keyed on Unit: runs once; `remaining` is saveable, so after a rotation the loop picks
    // up from where it was rather than starting at 5 again.
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
        onFinished()
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Back to $appLabel in", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "${remaining.coerceAtLeast(1)}",
            color = Ember, fontSize = 96.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(16.dp))
        Text("Take a breath.", color = Cream, fontSize = 18.sp)
        Spacer(Modifier.height(40.dp))
        TextButton(onClick = onDone) { Text("Actually, I'm done", color = Muted, fontSize = 14.sp) }
    }
}
