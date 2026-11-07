package com.intentcoach.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intentcoach.app.data.CompanionMood
import com.intentcoach.app.data.Settings
import com.intentcoach.app.data.TimeOfDayClock
import com.intentcoach.app.detection.AppWatchService
import com.intentcoach.app.overlay.InterruptActivity
import com.intentcoach.app.util.Permissions

private enum class OnboardingStep { WELCOME, PRIVACY, APPS, USAGE, OVERLAY, TRY, DONE }

/**
 * First run. Every permission is explained before it is requested, and every optional
 * step can be skipped — the user lands on Home either way and can finish setup from
 * Settings later. Nothing here requests a permission by itself: the user always taps.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var usage by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(false) }
    OnResume {
        usage = Permissions.hasUsageAccess(context)
        overlay = Permissions.hasOverlay(context)
    }

    fun next() {
        step = OnboardingStep.entries[(step.ordinal + 1).coerceAtMost(OnboardingStep.entries.lastIndex)]
    }

    fun finish() {
        Settings.setOnboardingDone(context, true)
        // Only start detection if it can actually work; otherwise Home explains what's off.
        if (Permissions.hasUsageAccess(context) && Permissions.hasOverlay(context)) {
            AppWatchService.start(context)
        }
        onFinished()
    }

    BackHandler(enabled = step != OnboardingStep.WELCOME) {
        step = OnboardingStep.entries[step.ordinal - 1]
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${step.ordinal + 1} of ${OnboardingStep.entries.size}", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (step != OnboardingStep.DONE) {
                TextButton(onClick = { finish() }) { Text("Skip setup", color = Muted, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))

        when (step) {
            OnboardingStep.APPS -> {
                Heading("Which apps should ask first?")
                Body("Pick the ones you tend to open on autopilot. Nothing is selected for you, and you can change this any time.")
                Spacer(Modifier.height(12.dp))
                AppPickerContent(Modifier.weight(1f))
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Next", onClick = { next() })
            }
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    OnboardingStep.WELCOME -> {
                        CompanionRoom(mood = CompanionMood.CONTENT, time = TimeOfDayClock.now())
                        Heading("Hi, I'm IntentCoach")
                        Body(
                            "When you open an app you chose, I'll ask one small question: what did you pick up " +
                                "your phone to do? Then you decide — continue, or go back. No blocking, no scores, no guilt."
                        )
                        Body("Over time you'll see your own patterns, in your own words.")
                    }
                    OnboardingStep.PRIVACY -> {
                        Heading("Everything stays on this phone")
                        Bullet("No account, no sign-in.")
                        Bullet("No internet permission at all — there is nowhere for your data to go.")
                        Bullet("No analytics, no ads, no tracking.")
                        Bullet("Delete everything any time from Settings.")
                    }
                    OnboardingStep.USAGE -> {
                        Heading("Usage Access")
                        Body(
                            "To notice when a chosen app opens, and to measure how long it stays on screen, Android " +
                                "requires a permission called Usage Access. It shows which app is in front — not what " +
                                "you do inside it."
                        )
                        Body("Android opens a settings list: find IntentCoach and switch it on, then come back here.")
                        PermissionRow("Usage Access", if (usage) "Thanks — it's on." else "Not granted yet.", usage) {
                            PermissionIntents.openUsageAccess(context)
                        }
                        if (!usage) InfoNote("You can skip this. Manual check-ins from Home still work, but times can't be measured.")
                    }
                    OnboardingStep.OVERLAY -> {
                        Heading("Display over other apps")
                        Body(
                            "This lets the check-in appear on top of the app you just opened. Android can't pause an " +
                                "app before it appears, so you may see it for a moment first — that's expected."
                        )
                        PermissionRow("Display over other apps", if (overlay) "Thanks — it's on." else "Not granted yet.", overlay) {
                            PermissionIntents.openOverlay(context)
                        }
                        if (!overlay) InfoNote("Without this, check-ins only happen when you start them yourself.")
                    }
                    OnboardingStep.TRY -> {
                        Heading("Try a check-in")
                        Body("Here's what it feels like. This practice run isn't saved.")
                        SecondaryButton("Try a practice check-in", onClick = {
                            context.startActivity(InterruptActivity.intent(context, null, InterruptActivity.Mode.PRACTICE))
                        })
                    }
                    OnboardingStep.DONE -> {
                        CompanionRoom(mood = CompanionMood.THRIVING, time = TimeOfDayClock.now(), celebrate = true)
                        Heading("You're set")
                        Body(
                            if (usage && overlay) "IntentCoach will start now and ask before your chosen apps open."
                            else "Automatic check-ins need both permissions. You can grant them later in Settings — " +
                                "manual check-ins work right away."
                        )
                    }
                    OnboardingStep.APPS -> Unit // handled above
                }
            }
        }

        if (step != OnboardingStep.APPS) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                when (step) {
                    OnboardingStep.DONE -> "Go to home"
                    OnboardingStep.USAGE -> if (usage) "Next" else "Skip for now"
                    OnboardingStep.OVERLAY -> if (overlay) "Next" else "Skip for now"
                    OnboardingStep.TRY -> "Next"
                    else -> "Next"
                },
                onClick = { if (step == OnboardingStep.DONE) finish() else next() }
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, color = Cream, fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, lineHeight = 32.sp)
}

@Composable
private fun Body(text: String) {
    Text(text, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).background(Lime, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Cream, fontSize = 15.sp, lineHeight = 22.sp)
    }
}
