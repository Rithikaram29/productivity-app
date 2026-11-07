package com.intentcoach.app.data

import android.content.Context

/**
 * LEGACY (v0.1) watch list, plus the curated catalog of well-known apps.
 *
 * Since DB v3 the user's choices live in the `monitored_app` table (see AppsRepository),
 * and the picker lists every launchable app via a launcher-intent `<queries>` entry — still
 * without the restricted QUERY_ALL_PACKAGES permission.
 *
 * What remains here and why:
 *  - [CATALOG] gives known apps a friendly name and a reliable category (AppCategorizer).
 *  - [get]/[hasExplicitSelection] let AppsRepository carry a v0.1 user's picks across once.
 *    The DEFAULTS are only ever used by that legacy reader: a fresh v3 install monitors
 *    nothing until the user chooses.
 */
object WatchedApps {
    const val PREFS = "watched_apps"
    private const val KEY = "packages"

    /** One entry in the curated catalog: a package we know by name, and what to call it. */
    data class KnownApp(val packageName: String, val displayName: String)

    val CATALOG = listOf(
        KnownApp("com.instagram.android", "Instagram"),
        KnownApp("com.zhiliaoapp.musically", "TikTok"),
        KnownApp("com.google.android.youtube", "YouTube"),
        KnownApp("com.twitter.android", "X (Twitter)"),
        KnownApp("com.reddit.frontpage", "Reddit"),
        KnownApp("com.facebook.katana", "Facebook"),
        KnownApp("com.snapchat.android", "Snapchat"),
        KnownApp("com.instagram.barcelona", "Threads"),
        KnownApp("com.whatsapp", "WhatsApp"),
        KnownApp("org.telegram.messenger", "Telegram"),
        KnownApp("com.discord", "Discord"),
        KnownApp("com.linkedin.android", "LinkedIn"),
        KnownApp("com.pinterest", "Pinterest"),
        KnownApp("com.quora.android", "Quora"),
        KnownApp("tv.twitch.android.app", "Twitch"),
        KnownApp("com.netflix.mediaclient", "Netflix"),
        KnownApp("com.amazon.avod.thirdpartyclient", "Prime Video"),
        KnownApp("in.startv.hotstar", "Hotstar"),
        KnownApp("com.spotify.music", "Spotify"),
        KnownApp("com.amazon.mShop.android.shopping", "Amazon"),
        KnownApp("com.flipkart.android", "Flipkart")
    )

    // v0.1 defaults. Only reachable through the legacy reader below.
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

    /**
     * True only if the user actually saved a selection in v0.1. Without this, migrating
     * would silently turn the five DEFAULTS into real monitored apps — exactly the
     * "monitor by default" behaviour the product rules out.
     */
    fun hasExplicitSelection(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    fun displayNameFor(pkg: String): String? = CATALOG.firstOrNull { it.packageName == pkg }?.displayName

    /**
     * The catalog entries that actually exist on this device.
     *
     * getApplicationInfo() throws NameNotFoundException for anything not installed —
     * that exception IS the answer, so the try/catch is the lookup, not error handling.
     */
    fun installed(context: Context): List<KnownApp> {
        val pm = context.packageManager
        return CATALOG.filter { app ->
            try {
                pm.getApplicationInfo(app.packageName, 0)
                true
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
