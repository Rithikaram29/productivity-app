package com.intentcoach.app.data

import android.content.Context
import com.intentcoach.app.domain.ActiveFocus

/**
 * Every small preference the app keeps, in one place, backed by SharedPreferences.
 *
 * (The product brief mentions DataStore; we deliberately stay on SharedPreferences —
 * it is what the app already used, the values are tiny, and it avoids a new dependency
 * plus a data migration. Room holds everything that is a *record*.)
 *
 * [isEnabled] is the single source of truth for "is Intent Coach running?" — not the
 * system. Android gives no reliable, non-deprecated way to ask "is my service alive
 * right now?" (ActivityManager.getRunningServices() is deprecated and lies to
 * third-party apps), and an OEM battery killer can silently stop the service without
 * the user ever changing their mind. So we store the *intent* and let the service
 * lifecycle follow it. Defaults to false: a fresh install is off until the user starts it.
 */
object Settings {
    const val PREFS = "settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_BATTERY_CARD_DISMISSED = "battery_card_dismissed"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_AUTO_INTERVENTION = "auto_intervention"
    private const val KEY_COOLDOWN_MIN = "cooldown_minutes"
    private const val KEY_DEFAULT_DURATION_MIN = "default_duration_minutes"
    private const val KEY_LEGACY_APPS_MIGRATED = "legacy_apps_migrated"

    private const val KEY_FOCUS_ACTIVE = "focus_active"
    private const val KEY_FOCUS_CATEGORY = "focus_category"
    private const val KEY_FOCUS_LABEL = "focus_label"
    private const val KEY_FOCUS_PLANNED = "focus_planned_min"
    private const val KEY_FOCUS_STARTED = "focus_started_at"
    private const val KEY_FOCUS_PAUSED_ACCUM = "focus_paused_accum_ms"
    private const val KEY_FOCUS_PAUSED_AT = "focus_paused_at"

    const val DEFAULT_COOLDOWN_MIN = 5

    /** Choices offered in Settings. 0 means "ask every time". */
    val COOLDOWN_CHOICES = listOf(0, 1, 5, 15, 30)

    /** Choices offered in the check-in. null = "No limit". */
    val DURATION_CHOICES: List<Int?> = listOf(5, 10, 15, 30, null)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Whether the user has waved away the battery-optimisation card.
     *
     * Persisted rather than held in memory on purpose: "dismissible" has to mean gone
     * for good, not gone until the next launch. That is the difference between a prompt
     * and a nag.
     */
    fun isBatteryCardDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BATTERY_CARD_DISMISSED, false)

    fun dismissBatteryCard(context: Context) {
        prefs(context).edit().putBoolean(KEY_BATTERY_CARD_DISMISSED, true).apply()
    }

    fun isOnboardingDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    /**
     * Automatic check-ins (detection + prompt). Off means the user only checks in by hand
     * from Home — the manual fallback works with no special permissions at all.
     */
    fun isAutoIntervention(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_INTERVENTION, true)

    fun setAutoIntervention(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_INTERVENTION, on).apply()
    }

    /** Minutes after a session ends (or a prompt is answered) before the same app asks again. */
    fun cooldownMinutes(context: Context): Int =
        prefs(context).getInt(KEY_COOLDOWN_MIN, DEFAULT_COOLDOWN_MIN)

    fun setCooldownMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_COOLDOWN_MIN, minutes.coerceAtLeast(0)).apply()
    }

    /** The duration pre-selected in step 2 of a check-in. null = nothing pre-selected. */
    fun defaultDurationMinutes(context: Context): Int? =
        prefs(context).getInt(KEY_DEFAULT_DURATION_MIN, 0).takeIf { it > 0 }

    fun setDefaultDurationMinutes(context: Context, minutes: Int?) {
        prefs(context).edit().putInt(KEY_DEFAULT_DURATION_MIN, minutes ?: 0).apply()
    }

    fun isLegacyAppsMigrated(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LEGACY_APPS_MIGRATED, false)

    fun setLegacyAppsMigrated(context: Context) {
        prefs(context).edit().putBoolean(KEY_LEGACY_APPS_MIGRATED, true).apply()
    }

    /**
     * The running focus timer, stored as timestamps rather than a ticking counter: the
     * remaining time is recomputed from the clock, so it survives rotation, the app being
     * swiped away, and process death.
     */
    fun activeFocus(context: Context): ActiveFocus? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_FOCUS_ACTIVE, false)) return null
        val category = runCatching {
            FocusCategory.valueOf(p.getString(KEY_FOCUS_CATEGORY, FocusCategory.WORK.name)!!)
        }.getOrDefault(FocusCategory.WORK)
        val pausedAt = p.getLong(KEY_FOCUS_PAUSED_AT, 0L).takeIf { it > 0 }
        return ActiveFocus(
            category = category,
            label = p.getString(KEY_FOCUS_LABEL, "") ?: "",
            plannedMinutes = p.getInt(KEY_FOCUS_PLANNED, 25),
            startedAt = p.getLong(KEY_FOCUS_STARTED, 0L),
            pausedAccumMs = p.getLong(KEY_FOCUS_PAUSED_ACCUM, 0L),
            pausedAt = pausedAt
        )
    }

    fun setActiveFocus(context: Context, focus: ActiveFocus?) {
        val e = prefs(context).edit()
        if (focus == null) {
            e.putBoolean(KEY_FOCUS_ACTIVE, false)
        } else {
            e.putBoolean(KEY_FOCUS_ACTIVE, true)
                .putString(KEY_FOCUS_CATEGORY, focus.category.name)
                .putString(KEY_FOCUS_LABEL, focus.label)
                .putInt(KEY_FOCUS_PLANNED, focus.plannedMinutes)
                .putLong(KEY_FOCUS_STARTED, focus.startedAt)
                .putLong(KEY_FOCUS_PAUSED_ACCUM, focus.pausedAccumMs)
                .putLong(KEY_FOCUS_PAUSED_AT, focus.pausedAt ?: 0L)
        }
        e.apply()
    }

    /** Wipes every preference. Part of "Delete all local data". */
    fun clearAll(context: Context) {
        // commit() not apply(): the caller restarts into onboarding straight after, and
        // must not read a stale onboarding flag.
        prefs(context).edit().clear().commit()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
