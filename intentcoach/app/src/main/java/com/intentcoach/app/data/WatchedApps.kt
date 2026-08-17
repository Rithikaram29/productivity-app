package com.intentcoach.app.data

import android.content.Context

/**
 * The short list of apps the user wants to be interrupted for.
 * Kept deliberately small — you do NOT need to enumerate every installed app
 * (which would trigger the restricted QUERY_ALL_PACKAGES permission).
 * The user picks a handful of known distracting apps and we watch only those.
 */
object WatchedApps {
    private const val PREFS = "watched_apps"
    private const val KEY = "packages"

    // Sensible defaults so the app does something on first run.
    private val DEFAULTS = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.google.android.youtube",
        "com.twitter.android",
        "com.reddit.frontpage"
    )

    fun get(context: Context): Set<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getStringSet(KEY, DEFAULTS) ?: DEFAULTS
    }

    fun set(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, packages).apply()
    }

    fun isWatched(context: Context, pkg: String): Boolean = get(context).contains(pkg)
}
