package com.intentcoach.app.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intentcoach.app.IntentCoachApp
import com.intentcoach.app.data.AppStat
import com.intentcoach.app.data.IntentLog
import com.intentcoach.app.data.Outcome
import com.intentcoach.app.data.Stats
import com.intentcoach.app.data.WeeklyStats
import kotlin.math.roundToInt

/**
 * The week in review — the part of this app that is insight rather than blocking.
 *
 * Everything on screen comes from the local intent_log table. Nothing here is uploaded,
 * scored, or streaked: the point is to show the user something true about themselves and
 * let them draw the conclusion.
 */
@Composable
fun InsightsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // null means "still loading" — distinct from a loaded-but-empty week, which gets its
    // own copy below. Without the distinction a new user sees the empty state flash
    // before their real data arrives.
    var stats by remember { mutableStateOf<WeeklyStats?>(null) }

    LaunchedEffect(Unit) {
        val dao = (context.applicationContext as IntentCoachApp).db.intentLogDao()
        // Room's suspend DAO methods dispatch to their own background executor, so this
        // never blocks the main thread even though we launch it from the composition.
        stats = Stats.from(dao.since(Stats.weekAgo()))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        Text("This week", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))

        val current = stats
        when {
            current == null -> Box(Modifier.weight(1f).fillMaxWidth()) { /* brief, silent */ }
            current.isEmpty -> EmptyWeek(Modifier.weight(1f))
            else -> LoadedWeek(current, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Field),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Back", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyWeek(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column {
            Text(
                "Nothing to show yet.",
                color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Once you've been asked a few times, this is where you'll see which apps " +
                    "you reach for, and how often there was a reason.",
                color = Muted, fontSize = 15.sp, lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun LoadedWeek(stats: WeeklyStats, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Package name → the label the user recognises. Resolved once per screen; the map
    // survives recomposition so we are not hitting PackageManager while scrolling.
    val labels = remember(stats) {
        (stats.perApp.map { it.packageName } + stats.recent.map { it.packageName })
            .distinct()
            .associateWith { prettyName(context.packageManager, it) }
    }
    // Widest bar in the breakdown is the busiest app, so the shape is readable at a glance.
    val busiest = stats.perApp.maxOfOrNull { it.interrupts } ?: 1

    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(color = Field, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "${stats.total} ${if (stats.total == 1) "interrupt" else "interrupts"}",
                        color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        // Warm, never a score: the redirect count is framed as something
                        // the user did, not something the app measured.
                        if (stats.redirected > 0)
                            "You turned back ${stats.redirected} ${if (stats.redirected == 1) "time" else "times"}."
                        else
                            "No turnarounds this week — that's fine, noticing comes first.",
                        color = if (stats.redirected > 0) Green else Muted,
                        fontSize = 15.sp, lineHeight = 22.sp
                    )
                }
            }
        }

        item { SectionLabel("Where it went") }

        items(stats.perApp, key = { it.packageName }) { app ->
            AppBreakdownRow(
                label = labels[app.packageName] ?: app.packageName,
                stat = app,
                widthFraction = app.interrupts.toFloat() / busiest
            )
        }

        if (stats.recent.isNotEmpty()) {
            item { SectionLabel("What you said") }
            items(stats.recent, key = { it.id }) { log ->
                RecentIntentRow(log = log, label = labels[log.packageName] ?: log.packageName)
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AppBreakdownRow(label: String, stat: AppStat, widthFraction: Float) {
    Surface(color = Field, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Cream, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("${stat.interrupts}", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))

            // The "chart": one Box the full width, one Box on top at a fraction of it.
            // No charting library needed for a single bar, and this one theme-matches.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(FieldBorder, RoundedCornerShape(3.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(widthFraction.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(if (stat.habitRate >= 0.5f) Accent else Green, RoundedCornerShape(3.dp))
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (stat.habits > 0)
                    "${stat.habits} of those you had no reason (${(stat.habitRate * 100).roundToInt()}%)"
                else
                    "Every one of those had a reason behind it.",
                color = Muted, fontSize = 13.sp, lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun RecentIntentRow(log: IntentLog, label: String) {
    Surface(color = Field, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(log.statedIntent, color = Cream, fontSize = 15.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text(label, color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            val (word, colour) = when (log.outcome) {
                Outcome.REDIRECTED -> "turned back" to Green
                Outcome.PROCEEDED -> "went in" to Muted
                Outcome.HABIT -> "no reason" to Accent
            }
            Text(word, color = colour, fontSize = 12.sp)
        }
    }
}

/** Same fallback idea as the interrupt screen: a real label if we can get one. */
private fun prettyName(pm: PackageManager, pkg: String): String = try {
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: PackageManager.NameNotFoundException) {
    pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
