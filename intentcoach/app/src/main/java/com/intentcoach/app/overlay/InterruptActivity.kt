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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intentcoach.app.container
import com.intentcoach.app.data.CheckInSource
import com.intentcoach.app.data.Decision
import com.intentcoach.app.data.IntentCategory
import com.intentcoach.app.data.Settings
import kotlinx.coroutines.launch

/**
 * The check-in. It renders on top of the app the user just opened (AUTO), or is started
 * by hand from Home (MANUAL), or as a practice run from onboarding (PRACTICE, saves
 * nothing). It never blocks: every path ends with the user free to do what they want.
 *
 *  1. What did you pick up your phone to do?   (5 options + optional words)
 *  2. How long do you want to spend?           (optional)
 *  3. Continue, or go back.
 *
 * Closing it without answering (Back on step 1, Home, screen off) records a DISMISSED
 * session — so the cooldown applies and nothing loops — but never an intentional one.
 *
 * This file depends only on the data layer and the app container, so the check-in works
 * whether or not automatic detection is available.
 */
class InterruptActivity : ComponentActivity() {

    enum class Mode { AUTO, MANUAL, PRACTICE }

    private data class Target(
        val packageName: String?,
        val label: String,
        val mode: Mode,
        /** Changes per launch so a new request resets the Compose flow (see key() below). */
        val requestId: Long
    )

    private var target by mutableStateOf<Target?>(null)

    /** True once the user answered; anything that closes us before that is a dismissal. */
    private var decided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true
        // Keep the same requestId across rotation, or key() below would reset the answers.
        load(intent, savedInstanceState?.getLong(KEY_REQUEST_ID)?.takeIf { it != 0L })
        val defaultDuration = Settings.defaultDurationMinutes(this)

        setContent {
            val t = target ?: return@setContent
            // key(): a brand-new request gets brand-new remembered state (step, answers).
            key(t.requestId) {
                CheckInFlow(
                    appLabel = t.label,
                    mode = t.mode,
                    defaultDuration = defaultDuration,
                    onDecision = { category, description, planned, decision ->
                        onDecided(t, category, description, planned, decision)
                    },
                    onDismiss = { dismiss(t); finish() }
                )
            }
        }
    }

    /**
     * launchMode is singleInstance, so a second request arrives here rather than as a new
     * activity. The service never sends one while we are showing, but a manual check-in
     * could; treat the unanswered first request as dismissed and start over.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        target?.let { if (!decided) dismiss(it) }
        decided = false
        load(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        target?.let { outState.putLong(KEY_REQUEST_ID, it.requestId) }
    }

    override fun onStop() {
        super.onStop()
        // The user pressed Home, switched apps, or the screen went off mid-question.
        if (!decided && !isChangingConfigurations) {
            target?.let { dismiss(it) }
            finish()
        }
    }

    override fun onDestroy() {
        isShowing = false
        super.onDestroy()
    }

    private fun load(intent: Intent, requestId: Long? = null) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val mode = runCatching { Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.AUTO.name) }
            .getOrDefault(Mode.AUTO)
        target = Target(
            packageName = pkg,
            label = if (pkg == null) "an app" else prettyName(pkg),
            mode = if (pkg == null) Mode.PRACTICE else mode,
            requestId = requestId ?: System.nanoTime()
        )
    }

    private fun onDecided(t: Target, category: IntentCategory, description: String, planned: Int?, decision: Decision) {
        if (decided) return // double-tap guard: one decision, one record
        decided = true
        val pkg = t.packageName
        if (t.mode != Mode.PRACTICE && pkg != null) {
            val c = container
            // appScope, not lifecycleScope: we finish() immediately below, and onDestroy
            // would cancel a lifecycleScope write before it reached the database.
            c.appScope.launch {
                c.sessions.recordCheckIn(
                    packageName = pkg,
                    appName = t.label,
                    source = if (t.mode == Mode.AUTO) CheckInSource.AUTO else CheckInSource.MANUAL,
                    intent = category,
                    description = description,
                    plannedMinutes = planned,
                    decision = decision
                )
            }
        }
        when {
            t.mode == Mode.PRACTICE -> finish()
            decision == Decision.CONTINUE -> openTargetApp(pkg)
            else -> goHome()
        }
    }

    private fun dismiss(t: Target) {
        if (decided) return
        decided = true
        val pkg = t.packageName ?: return
        if (t.mode == Mode.PRACTICE) return
        val c = container
        c.appScope.launch {
            c.sessions.recordDismissed(
                packageName = pkg,
                appName = t.label,
                source = if (t.mode == Mode.AUTO) CheckInSource.AUTO else CheckInSource.MANUAL
            )
        }
    }

    /**
     * A launcher-style intent with NEW_TASK brings the app's existing task forward rather
     * than restarting it, so the user lands where they were.
     */
    private fun openTargetApp(pkg: String?) {
        val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
        finish()
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    private fun prettyName(pkg: String): String = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(ai).toString()
    } catch (e: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_MODE = "extra_mode"
        private const val KEY_REQUEST_ID = "request_id"

        /**
         * Whether a check-in is on screen right now. Read by the service to avoid stacking
         * a second prompt. @Volatile: written on the main thread, read on the service's.
         */
        @Volatile
        var isShowing: Boolean = false
            private set

        fun intent(context: Context, packageName: String?, mode: Mode): Intent =
            Intent(context, InterruptActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                packageName?.let { putExtra(EXTRA_PACKAGE, it) }
                putExtra(EXTRA_MODE, mode.name)
            }
    }
}

// Palette v1.1 (pastel-on-dark). Duplicated from ui/Palette.kt on purpose: overlay/ is a
// separately launched surface and should not depend on ui/. Change both together.
private val Ink = Color(0xFF100B07)
private val Cream = Color(0xFFF5ECDE)
private val Muted = Color(0xFFAC9C8B)
private val Field = Color(0xFF221A13)
private val FieldBorder = Color(0xFF443A31)
private val Clay = Color(0xFFD9734E)
private val Lime = Color(0xFFC3E667)

private enum class Step { INTENT, DURATION, DECIDE }

@Composable
private fun CheckInFlow(
    appLabel: String,
    mode: InterruptActivity.Mode,
    defaultDuration: Int?,
    onDecision: (IntentCategory, String, Int?, Decision) -> Unit,
    onDismiss: () -> Unit
) {
    // rememberSaveable, not remember: rotating the phone mid-check-in recreates the
    // activity, and the user should not lose their answers. Enums, Int? and String all
    // fit in a Bundle.
    var step by rememberSaveable { mutableStateOf(Step.INTENT) }
    var category by rememberSaveable { mutableStateOf<IntentCategory?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var planned by rememberSaveable { mutableStateOf(defaultDuration) }

    BackHandler {
        when (step) {
            Step.INTENT -> onDismiss()
            Step.DURATION -> step = Step.INTENT
            Step.DECIDE -> step = Step.DURATION
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                when (mode) {
                    InterruptActivity.Mode.PRACTICE -> "Practice run — nothing is saved"
                    else -> "You're opening $appLabel"
                },
                color = Muted, fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            StepDots(step.ordinal)
            Spacer(Modifier.height(28.dp))

            when (step) {
                Step.INTENT -> IntentStep(
                    selected = category,
                    description = description,
                    onSelect = { category = it },
                    onDescription = { description = it.take(120) },
                    onNext = { step = Step.DURATION },
                    onDismiss = onDismiss
                )
                Step.DURATION -> DurationStep(
                    selected = planned,
                    onSelect = { planned = it },
                    onNext = { step = Step.DECIDE },
                    onSkip = { planned = null; step = Step.DECIDE }
                )
                Step.DECIDE -> DecideStep(
                    appLabel = appLabel,
                    category = category ?: IntentCategory.SOMETHING_ELSE,
                    description = description,
                    planned = planned,
                    onContinue = {
                        onDecision(category ?: IntentCategory.SOMETHING_ELSE, description, planned, Decision.CONTINUE)
                    },
                    onGoBack = {
                        onDecision(category ?: IntentCategory.SOMETHING_ELSE, description, planned, Decision.GO_BACK)
                    }
                )
            }
        }
    }
}

@Composable
private fun StepDots(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 22.dp else 8.dp, height = 8.dp)
                    .background(if (i <= current) Lime else FieldBorder, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun Question(text: String) {
    Text(
        text,
        color = Cream, fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 33.sp,
        fontFamily = FontFamily.Serif
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntentStep(
    selected: IntentCategory?,
    description: String,
    onSelect: (IntentCategory) -> Unit,
    onDescription: (String) -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    Question("What did you pick up your phone to do?")
    Spacer(Modifier.height(20.dp))
    IntentCategory.entries.forEach { c ->
        Option(c.label, selected = c == selected) { onSelect(c) }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = description,
        onValueChange = onDescription,
        placeholder = { Text("A few words, if you like (optional)", color = Muted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Field, unfocusedContainerColor = Field,
            focusedTextColor = Cream, unfocusedTextColor = Cream,
            focusedBorderColor = Lime, unfocusedBorderColor = FieldBorder
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(20.dp))
    PrimaryButton("Next", enabled = selected != null, onClick = onNext)
    if (selected == null) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick whichever feels closest — there's no wrong answer.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Text("Not now", color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun DurationStep(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Question("How long would you like to spend?")
    Spacer(Modifier.height(8.dp))
    Text("Just a guide for you. Going over isn't a failure.", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(20.dp))
    Settings.DURATION_CHOICES.forEach { m ->
        Option(if (m == null) "No limit" else "$m minutes", selected = m == selected) { onSelect(m) }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton("Next", enabled = true, onClick = onNext)
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text("Skip", color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun DecideStep(
    appLabel: String,
    category: IntentCategory,
    description: String,
    planned: Int?,
    onContinue: () -> Unit,
    onGoBack: () -> Unit
) {
    Question("Still want to open $appLabel?")
    Spacer(Modifier.height(16.dp))
    Surface(color = Field, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Your plan", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                category.label + if (description.isNotBlank()) " — ${description.trim()}" else "",
                color = Cream, fontSize = 16.sp, lineHeight = 22.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(if (planned == null) "No time limit" else "About $planned minutes", color = Muted, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onContinue,
        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text("Continue — I want to use it", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onGoBack,
        border = BorderStroke(1.dp, FieldBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text("Go back — I don't need it right now", color = Cream, fontSize = 15.sp) }
    Spacer(Modifier.height(12.dp))
    Text(
        "It'll still be here later.",
        color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
    )
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Clay, contentColor = Cream,
            disabledContainerColor = Field, disabledContentColor = Muted
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) Lime.copy(alpha = 0.16f) else Field,
        border = BorderStroke(1.dp, if (selected) Lime else Color.Transparent),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label, color = Cream, fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp)
        )
    }
}
