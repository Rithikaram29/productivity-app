package com.intentcoach.app.data

import android.content.Context

/**
 * Whether the user has actually turned the coach on.
 *
 * This flag is the single source of truth for "is Intent Coach running?" — not the
 * system. Android gives no reliable, non-deprecated way to ask "is my service alive
 * right now?" (ActivityManager.getRunningServices() is deprecated and lies to
 * third-party apps), and an OEM battery killer can silently stop the service without
 * the user ever changing their mind. So we store the *intent* and let the service
 * lifecycle follow it.
 *
 * Defaults to false: a fresh install is off until the user taps Start.
 */
object Settings {
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_BATTERY_CARD_DISMISSED = "battery_card_dismissed"

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

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
