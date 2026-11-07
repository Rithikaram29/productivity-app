package com.intentcoach.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intentcoach.app.AppContainer
import com.intentcoach.app.data.IntentCategory
import com.intentcoach.app.domain.AppBreakdown
import com.intentcoach.app.domain.DayPoint
import com.intentcoach.app.domain.Insights
import com.intentcoach.app.domain.Overview
import com.intentcoach.app.domain.TimeMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class InsightsViewModel(private val c: AppContainer) : ViewModel() {

    enum class Range { TODAY, WEEK }

    /** One row of the per-app table. [usageMs] is Android-measured; null = no usage access. */
    data class AppRow(val breakdown: AppBreakdown, val usageMs: Long?)

    data class State(
        val range: Range = Range.WEEK,
        val overview: Overview = Insights.overview(emptyList()),
        val apps: List<AppRow> = emptyList(),
        val monitoredUsageMs: Long? = null,
        val trend: List<DayPoint> = emptyList(),
        val commonIntents: List<Pair<IntentCategory, Int>> = emptyList(),
        val focusMs: Long = 0,
        val loaded: Boolean = false
    )

    /** Android-measured usage, refreshed on resume. Kept apart because it is not a Flow. */
    private data class UsageSnapshot(
        val byDay: Map<LocalDate, Long>? = null,
        val today: Map<String, Long>? = null,
        val week: Map<String, Long>? = null
    )

    private val zone: ZoneId = ZoneId.systemDefault()
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val range = MutableStateFlow(Range.WEEK)
    private val usage = MutableStateFlow(UsageSnapshot())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = now.flatMapLatest { t ->
        val dayStart = TimeMath.startOfToday(t, zone)
        val weekStart = TimeMath.startOfWindow(t, zone, 7)
        combine(c.sessions.observeSince(weekStart), c.focus.observeAll(), range, usage) { week, focus, r, u ->
            val records = if (r == Range.TODAY) week.filter { it.session.startTime >= dayStart } else week
            val totals = if (r == Range.TODAY) u.today else u.week
            val breakdown = Insights.perApp(records).associateBy { it.packageName }.toMutableMap()
            // Apps with usage but no check-ins in the window still deserve a row.
            totals?.forEach { (pkg, ms) ->
                if (ms > 0 && pkg !in breakdown) {
                    // The screen resolves the real label from the package name.
                    breakdown[pkg] = AppBreakdown(pkg, pkg, 0, 0, 0, 0)
                }
            }
            State(
                range = r,
                overview = Insights.overview(records),
                apps = breakdown.values
                    .map { AppRow(it, totals?.get(it.packageName)) }
                    .sortedWith(compareByDescending<AppRow> { it.usageMs ?: 0L }.thenByDescending { it.breakdown.sessions }),
                monitoredUsageMs = totals?.values?.sum(),
                trend = Insights.dailyTrend(week, u.byDay.orEmpty(), TimeMath.dayOf(t, zone), zone),
                commonIntents = Insights.commonIntents(records),
                focusMs = Insights.focusMs(focus, if (r == Range.TODAY) dayStart else weekStart),
                loaded = true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** True when Android-measured numbers are unavailable (no Usage Access). */
    val usageUnavailable: Boolean get() = usage.value.week == null

    fun setRange(r: Range) {
        range.value = r
    }

    fun refresh() {
        val t = System.currentTimeMillis()
        now.value = t
        viewModelScope.launch(Dispatchers.IO) {
            c.sessions.reconcile(t)
            val pkgs = c.apps.monitoredPackages.value
            usage.value = UsageSnapshot(
                byDay = c.usage.foregroundByDay(pkgs, t, zone, 7),
                today = c.usage.foregroundTotals(pkgs, TimeMath.startOfToday(t, zone), t),
                week = c.usage.foregroundTotals(pkgs, TimeMath.startOfWindow(t, zone, 7), t)
            )
        }
    }
}
