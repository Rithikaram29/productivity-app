package com.intentcoach.app.domain

import com.intentcoach.app.data.AppCategory

/**
 * Suggests a picker group for an app. Only a suggestion — it just decides which heading
 * an app appears under.
 *
 * Order of trust: our own list of well-known packages, then "is it a browser", then the
 * category the developer declared in their manifest (ApplicationInfo.category, API 26+).
 * The integer constants are copied from ApplicationInfo so this stays pure Kotlin.
 */
object AppCategorizer {
    // android.content.pm.ApplicationInfo.CATEGORY_*
    const val CATEGORY_UNDEFINED = -1
    const val CATEGORY_GAME = 0
    const val CATEGORY_AUDIO = 1
    const val CATEGORY_VIDEO = 2
    const val CATEGORY_IMAGE = 3
    const val CATEGORY_SOCIAL = 4

    private val SOCIAL = setOf(
        "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.twitter.android", "com.reddit.frontpage", "com.facebook.katana",
        "com.snapchat.android", "com.instagram.barcelona", "com.linkedin.android",
        "com.pinterest", "com.quora.android", "com.tumblr", "com.bereal.ft"
    )
    private val ENTERTAINMENT = setOf(
        "com.google.android.youtube", "tv.twitch.android.app", "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient", "in.startv.hotstar", "com.spotify.music",
        "com.jio.media.ondemand", "com.disney.disneyplus"
    )
    private val COMMUNICATION = setOf(
        "com.whatsapp", "org.telegram.messenger", "com.discord", "com.facebook.orca",
        "com.google.android.apps.messaging", "org.thoughtcrime.securesms", "com.Slack",
        "com.microsoft.teams", "com.google.android.gm", "com.samsung.android.messaging"
    )
    private val BROWSERS = setOf(
        "com.android.chrome", "org.mozilla.firefox", "com.brave.browser",
        "com.sec.android.app.sbrowser", "com.microsoft.emmx", "com.opera.browser",
        "com.duckduckgo.mobile.android"
    )

    fun categorize(packageName: String, androidCategory: Int, isBrowser: Boolean): AppCategory = when {
        packageName in SOCIAL -> AppCategory.SOCIAL
        packageName in ENTERTAINMENT -> AppCategory.ENTERTAINMENT
        packageName in COMMUNICATION -> AppCategory.COMMUNICATION
        packageName in BROWSERS || isBrowser -> AppCategory.BROWSING
        androidCategory == CATEGORY_SOCIAL -> AppCategory.SOCIAL
        androidCategory == CATEGORY_GAME ||
            androidCategory == CATEGORY_AUDIO ||
            androidCategory == CATEGORY_VIDEO ||
            androidCategory == CATEGORY_IMAGE -> AppCategory.ENTERTAINMENT
        else -> AppCategory.OTHER
    }
}
