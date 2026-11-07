package com.intentcoach.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intentcoach.app.AppContainer
import com.intentcoach.app.data.FocusSession
import com.intentcoach.app.data.MonitoredApp
import com.intentcoach.app.data.Reflection
import com.intentcoach.app.data.RewardState
import com.intentcoach.app.data.Rewards
import com.intentcoach.app.data.SessionWithIntent
import com.intentcoach.app.data.TimeOfDay
import com.intentcoach.app.data.TimeOfDayClock
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
import java.time.ZoneId

/**
 * Everything Home shows, assembled from the repositories. The screen only draws.
 *
 * Today's numbers come from two independent sources and are labelled as such:
 *  - "monitored time" is Android's own measurement of foreground time for the monitored
 *    apps (complete, whether or not there was a check-in) — null without usage access;
 *  - session counts come from the check-in records.
 */
class HomeViewModel(private val c: AppContainer) : ViewModel() {

    data class State(
        val today: Overview = Insights.overview(emptyList()),
        val monitoredUsageMs: Long? = null,
        val focusTodayMs: Long = 0,
        val focusWeekMs: Long = 0,
        val reward: RewardState? = null,
        val pendingReflection: SessionWithIntent? = null,
        val monitoredApps: List<MonitoredApp> = emptyList(),
        val timeOfDay: TimeOfDay = TimeOfDayClock.now(),
        val daySeed: Int = 0,
        val lastFocus: FocusSession? = null
    )

    private val zone: ZoneId = ZoneId.systemDefault()
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val monitoredUsage = MutableStateFlow<Long?>(null)

    // flatMapLatest: when the day rolls over (refresh() moves `now`), switch to a query for
    // the new day and drop the old one.
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = now.flatMapLatest { t ->
        val dayStart = TimeMath.startOfToday(t, zone)
        val weekStart = TimeMath.startOfWindow(t, zone, 7)
        combine(
            c.sessions.observeSince(dayStart),
            c.focus.observeAll(),
            c.sessions.observePendingReflection(t),
            c.apps.observeAll(),
            monitoredUsage
        ) { records, focus, pending, apps, usageMs ->
            State(
                today = Insights.overview(records),
                monitoredUsageMs = usageMs,
                focusTodayMs = Insights.focusMs(focus, dayStart),
                focusWeekMs = Insights.focusMs(focus, weekStart),
                reward = Rewards.state(focus, t, zone),
                pendingReflection = pending,
                monitoredApps = apps.filter { it.isEnabled },
                timeOfDay = TimeOfDayClock.now(t, zone),
                daySeed = TimeMath.dayOf(t, zone).dayOfYear,
                lastFocus = focus.firstOrNull()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        // Re-measure whenever the set of monitored apps changes (including the first load).
        viewModelScope.launch { c.apps.monitoredPackages.collect { refresh() } }
    }

    /** Called on resume: close finished sessions, then re-measure today's usage. */
    fun refresh() {
        val t = System.currentTimeMillis()
        now.value = t
        viewModelScope.launch(Dispatchers.IO) {
            c.focus.finishIfDone(t)
            c.sessions.reconcile(t)
            monitoredUsage.value = c.usage
                .foregroundTotals(c.apps.monitoredPackages.value, TimeMath.startOfToday(t, zone), t)
                ?.values?.sum()
        }
    }

    fun saveReflection(sessionId: String, reflection: Reflection, notes: String) {
        c.appScope.launch { c.sessions.saveReflection(sessionId, reflection, notes) }
    }

    fun dismissReflection(sessionId: String) {
        c.appScope.launch { c.sessions.dismissReflection(sessionId) }
    }
}
