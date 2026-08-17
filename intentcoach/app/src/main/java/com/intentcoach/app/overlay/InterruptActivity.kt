package com.intentcoach.app.overlay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.intentcoach.app.IntentCoachApp
import com.intentcoach.app.data.IntentLog
import com.intentcoach.app.data.Outcome
import kotlinx.coroutines.launch

/**
 * This is the moment that matters. It renders on top of the distracting app.
 * It never says "no" — it just makes the user state intent first. Half the time
 * that alone breaks the reflex. The habit option is the honest escape hatch that
 * keeps people from lying to the screen or uninstalling.
 */
class InterruptActivity : ComponentActivity() {

    private lateinit var targetPackage: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: "the app"

        setContent {
            InterruptScreen(
                appLabel = prettyName(targetPackage),
                onRedirect = { intentText ->
                    log(intentText, Outcome.REDIRECTED)
                    goHome()
                },
                onProceed = { intentText ->
                    log(intentText, Outcome.PROCEEDED)
                    openTargetApp()
                },
                onHabit = {
                    log("(habit)", Outcome.HABIT)
                    openTargetApp()
                }
            )
        }
    }

    private fun log(intentText: String, outcome: Outcome) {
        val dao = (application as IntentCoachApp).db.intentLogDao()
        lifecycleScope.launch {
            dao.insert(IntentLog(packageName = targetPackage, statedIntent = intentText, outcome = outcome))
        }
    }

    private fun openTargetApp() {
        val launch = packageManager.getLaunchIntentForPackage(targetPackage)
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
    }
}

private val Ink = Color(0xFF12100E)
private val Cream = Color(0xFFF5F2EC)
private val Muted = Color(0xFF8A857D)
private val Field = Color(0xFF1E1B17)
private val FieldBorder = Color(0xFF38332C)
private val Accent = Color(0xFFE1306C)
private val Green = Color(0xFF1D9E75)

@Composable
private fun InterruptScreen(
    appLabel: String,
    onRedirect: (String) -> Unit,
    onProceed: (String) -> Unit,
    onHabit: () -> Unit
) {
    var stage by remember { mutableStateOf(Stage.ASK) }
    var intentText by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(Ink).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (stage) {
            Stage.ASK -> AskStage(
                appLabel = appLabel,
                intentText = intentText,
                onIntentChange = { intentText = it },
                onChip = { chip ->
                    if (chip == HABIT_CHIP) onHabit()
                    else { intentText = chip; stage = Stage.CONFIRM }
                },
                onContinue = { if (intentText.isNotBlank()) stage = Stage.CONFIRM }
            )
            Stage.CONFIRM -> ConfirmStage(
                intentText = intentText,
                onRedirect = { onRedirect(intentText) },
                onProceed = { onProceed(intentText) }
            )
        }
    }
}

private enum class Stage { ASK, CONFIRM }
private const val HABIT_CHIP = "I just opened it out of habit"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskStage(
    appLabel: String,
    intentText: String,
    onIntentChange: (String) -> Unit,
    onChip: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("You're opening $appLabel", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(32.dp))
        Text(
            "Before you go in — what did you pick up your phone to do?",
            color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = intentText,
            onValueChange = onIntentChange,
            placeholder = { Text("Call mum back", color = Muted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Field, unfocusedContainerColor = Field,
                focusedTextColor = Cream, unfocusedTextColor = Cream,
                focusedBorderColor = Accent, unfocusedBorderColor = FieldBorder
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        listOf("Call someone", "Reply to a message", HABIT_CHIP).forEach { chip ->
            Chip(chip) { onChip(chip) }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Continue anyway", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun ConfirmStage(intentText: String, onRedirect: () -> Unit, onProceed: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Right — you meant to ${intentText.lowercase()}.",
            color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 30.sp
        )
        Spacer(Modifier.height(8.dp))
        Text("It'll still be here later.", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRedirect,
            colors = ButtonDefaults.buttonColors(containerColor = Green),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Take me to what I meant to do", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onProceed) {
            Text("No, I did mean to open this", color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Field,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label, color = Cream, fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp)
        )
    }
}
