package com.intentcoach.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchedAppsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `a fresh install watches the defaults, not nothing`() {
        val watched = WatchedApps.get(context)

        assertTrue(watched.isNotEmpty())
        assertTrue(WatchedApps.isWatched(context, "com.instagram.android"))
    }

    @Test
    fun `set then get round-trips`() {
        WatchedApps.set(context, setOf("com.reddit.frontpage", "com.discord"))

        assertEquals(setOf("com.reddit.frontpage", "com.discord"), WatchedApps.get(context))
    }

    @Test
    fun `an app removed from the set is no longer watched`() {
        WatchedApps.set(context, setOf("com.instagram.android"))
        assertTrue(WatchedApps.isWatched(context, "com.instagram.android"))

        WatchedApps.set(context, emptySet())

        assertFalse(WatchedApps.isWatched(context, "com.instagram.android"))
    }

    @Test
    fun `watching nothing is allowed and means nothing is watched`() {
        WatchedApps.set(context, emptySet())

        // Empty is a legitimate user choice — it must not silently fall back to DEFAULTS.
        assertTrue(WatchedApps.get(context).isEmpty())
        assertFalse(WatchedApps.isWatched(context, "com.instagram.android"))
    }

    @Test
    fun `catalog has no duplicate packages`() {
        val packages = WatchedApps.CATALOG.map { it.packageName }

        assertEquals(packages.size, packages.distinct().size)
    }

    @Test
    fun `every catalog entry has a package and a display name`() {
        WatchedApps.CATALOG.forEach { app ->
            assertTrue("blank package for ${app.displayName}", app.packageName.isNotBlank())
            assertTrue("blank name for ${app.packageName}", app.displayName.isNotBlank())
            assertTrue("suspicious package: ${app.packageName}", app.packageName.contains('.'))
        }
    }

    @Test
    fun `every default is also in the catalog, so the picker can show it`() {
        val catalogPackages = WatchedApps.CATALOG.map { it.packageName }.toSet()

        // Defaults are private, so read them the way the app does: a fresh install.
        WatchedApps.get(context).forEach { pkg ->
            assertTrue("default $pkg is missing from CATALOG", pkg in catalogPackages)
        }
    }

    @Test
    fun `installed() returns only catalog entries`() {
        // Robolectric's PackageManager knows about no third-party apps, so this is
        // empty here. The value of the assertion is the contract: installed() must never
        // invent a package outside the curated catalog (that is what would need
        // QUERY_ALL_PACKAGES).
        val catalogPackages = WatchedApps.CATALOG.map { it.packageName }.toSet()

        WatchedApps.installed(context).forEach { app ->
            assertTrue(app.packageName in catalogPackages)
        }
    }
}
