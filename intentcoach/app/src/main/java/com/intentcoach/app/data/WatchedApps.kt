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

    /** One entry in the curated catalog: a package we know by name, and what to call it. */
    data class KnownApp(val packageName: String, val displayName: String)

    /**
     * The curated catalog the picker offers.
     *
     * Why a hardcoded list instead of "every app on the phone": listing all installed
     * packages requires QUERY_ALL_PACKAGES, a restricted permission needing a Play
     * Console declaration. Asking about a *specific, known* package needs no permission
     * at all — so we ship the list of usual suspects and check each one individually.
     *
     * The cost is that an app missing from this list cannot be watched. That is the
     * trade we are making on purpose; widen the list rather than widen the permission.
     */
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

    /**
     * The catalog entries that actually exist on this device.
     *
     * getApplicationInfo() throws NameNotFoundException for anything not installed —
     * that exception IS the answer, so the try/catch is the lookup, not error handling.
     * One call per known package, which is what keeps us clear of QUERY_ALL_PACKAGES.
     *
     * Hits the PackageManager once per catalog entry, so call it off the composition
     * (inside remember/LaunchedEffect), not on every recomposition.
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
