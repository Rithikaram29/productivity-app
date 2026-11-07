package com.intentcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intentcoach.app.domain.DayPoint
import com.intentcoach.app.domain.Format
import com.intentcoach.app.domain.Overview
import java.time.format.TextStyle
import java.util.Locale

/**
 * Insights: today or the last 7 days. Everything is computed from local records
 * (check-ins) and Android's own usage events (time). Nothing is uploaded or scored.
 * Unknown sessions are always shown on their own — never folded into "unplanned".
 */
@Composable
fun InsightsScreen(onOpenApp: (String) -> Unit, onOpenHistory: () -> Unit) {
    val vm = appViewModel { InsightsViewModel(it) }
    val state by vm.state.collectAsState()
    OnResume { vm.refresh() }
    val pm = LocalContext.current.packageManager

    LazyColumn(
        Modifier.fillMaxSize().background(Ink).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            ScreenTitle("Insights", "What you reached for, and why. Only you can see this.")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Today", state.range == InsightsViewModel.Range.TODAY, { vm.setRange(InsightsViewModel.Range.TODAY) })
                Pill("Last 7 days", state.range == InsightsViewModel.Range.WEEK, { vm.setRange(InsightsViewModel.Range.WEEK) })
            }
        }

        if (state.loaded && state.overview.isEmpty && state.apps.isEmpty()) {
            item {
                CoachCard {
                    Text("Nothing to show yet.", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "After a few check-ins, this is where you'll see which apps you reach for, " +
                            "and how often there was a plan behind it.",
                        color = Muted, fontSize = 14.sp, lineHeight = 20.sp
                    )
                }
            }
        } else {
            item { OverviewTiles(state.overview, state.monitoredUsageMs) }
            item { BreakdownCard(state.overview) }

            if (state.range == InsightsViewModel.Range.WEEK) {
                item { TrendCard(state.trend) }
            }

            if (state.commonIntents.isNotEmpty()) {
                item {
                    CoachCard {
                        Text("Common intentions", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        val top = state.commonIntents.first().second.coerceAtLeast(1)
                        state.commonIntents.forEach { (cat, n) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cat.label, color = Cream, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("$n", color = Muted, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Bar(n.toFloat() / top, Lavender)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            if (state.focusMs > 0) {
                item {
                    StatTile(Format.duration(state.focusMs), "Focus time in this period", Modifier.fillMaxWidth(), valueColor = Ember)
                }
            }

            if (state.apps.isNotEmpty()) {
                item { SectionLabel("By app — tap for its history") }
                val busiest = state.apps.maxOf { it.usageMs ?: 0L }.coerceAtLeast(1L)
                items(state.apps, key = { it.breakdown.packageName }) { row ->
                    AppRowCard(
                        label = appLabel(pm, row.breakdown.packageName, row.breakdown.appName),
                        row = row,
                        fraction = (row.usageMs ?: 0L).toFloat() / busiest,
                        onClick = { onOpenApp(row.breakdown.packageName) }
                    )
                }
            }
            item {
                SecondaryButton("All session history", onOpenHistory)
            }
        }

        item {
            InfoNote(
                "\"Intentional\" means you named a purpose before continuing. \"Unplanned\" means you chose " +
                    "\"Just checking\" or \"I'm bored\". \"Unknown\" means the check-in was closed without an " +
                    "answer. Time in apps comes from Android's own usage records."
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OverviewTiles(o: Overview, monitoredMs: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(monitoredMs?.let { Format.duration(it) } ?: "—", "Time in monitored apps", Modifier.weight(1f))
            StatTile(o.averageSessionMs?.let { Format.duration(it) } ?: "—", "Average session", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${o.intentional}", "Intentional", Modifier.weight(1f), valueColor = Lime)
            StatTile("${o.unplanned}", "Unplanned", Modifier.weight(1f), valueColor = Peach)
            StatTile("${o.unknown}", "Unknown", Modifier.weight(1f), valueColor = Muted)
            StatTile("${o.turnedBack}", "Turned back", Modifier.weight(1f), valueColor = Mint)
        }
        if (monitoredMs == null) {
            InfoNote("App time needs Usage Access (Settings). We'd rather show a dash than a guess.")
        }
        if (o.unmeasuredSessions > 0) {
            InfoNote("${o.unmeasuredSessions} sessions couldn't be measured and are left out of the average.")
        }
    }
}

@Composable
private fun BreakdownCard(o: Overview) {
    val total = o.intentional + o.unplanned + o.unknown
    CoachCard {
        Text("How sessions began", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        if (total == 0) {
            Text("No sessions in this period.", color = Muted, fontSize = 13.sp)
        } else {
            // A single stacked bar: three Boxes whose weights are the counts.
            Row(Modifier.fillMaxWidth().height(10.dp)) {
                Segment(o.intentional, Lime)
                Segment(o.unplanned, Peach)
                Segment(o.unknown, FieldBorder)
            }
            Spacer(Modifier.height(10.dp))
            Legend(Lime, "Intentional ${o.intentional}")
            Legend(Peach, "Unplanned ${o.unplanned}")
            Legend(FieldBorder, "Unknown ${o.unknown}")
        }
        o.intentionalRate?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                "$it% of the sessions you went ahead with had a purpose you named.",
                color = Cream, fontSize = 14.sp, lineHeight = 20.sp
            )
            Text(
                "Counts intentional ÷ (intentional + unplanned). Unknown and turned-back check-ins aren't included. " +
                    "A mirror, not a score.",
                color = Muted, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
        o.turnBackRate?.let {
            if (o.turnedBack > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "You turned back ${o.turnedBack} ${if (o.turnedBack == 1) "time" else "times"} ($it% of check-ins).",
                    color = Mint, fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun RowScope.Segment(count: Int, color: Color) {
    if (count > 0) Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun TrendCard(points: List<DayPoint>) {
    CoachCard {
        Text("Daily time in monitored apps", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        val max = points.maxOfOrNull { it.usageMs ?: 0L }?.coerceAtLeast(1L) ?: 1L
        Row(
            Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            points.forEach { p ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val ms = p.usageMs
                    if (ms == null) {
                        // No data for this day (no access, or Android no longer keeps it).
                        Text("–", color = Muted, fontSize = 12.sp)
                    } else {
                        Text(Format.duration(ms), color = Muted, fontSize = 9.sp, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((80f * ms / max).coerceAtLeast(2f).dp)
                                .background(Sky, RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        p.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
                        color = Muted, fontSize = 11.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Intentional / unplanned check-ins: " + points.joinToString("  ") { "${it.intentional}/${it.unplanned}" },
            color = Muted, fontSize = 11.sp, lineHeight = 15.sp
        )
    }
}

@Composable
private fun AppRowCard(label: String, row: InsightsViewModel.AppRow, fraction: Float, onClick: () -> Unit) {
    val b = row.breakdown
    CoachCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Cream, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(row.usageMs?.let { Format.duration(it) } ?: "—", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        Bar(fraction, Sky)
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("${b.sessions} ${if (b.sessions == 1) "session" else "sessions"}")
                b.averageMs?.let { append(" · avg ${Format.duration(it)}") }
                if (b.turnedBack > 0) append(" · turned back ${b.turnedBack}")
            },
            color = Muted, fontSize = 13.sp
        )
    }
}
