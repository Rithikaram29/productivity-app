package com.intentcoach.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Settings is SharedPreferences all the way down, so it genuinely needs a Context —
 * hence Robolectric. Robolectric gives each test a fresh in-memory preferences store,
 * so nothing leaks between tests.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `is off on a fresh install`() {
        assertFalse(Settings.isEnabled(context))
    }

    @Test
    fun `remembers being switched on`() {
        Settings.setEnabled(context, true)

        assertTrue(Settings.isEnabled(context))
    }

    @Test
    fun `remembers being switched off again`() {
        Settings.setEnabled(context, true)
        Settings.setEnabled(context, false)

        assertFalse(Settings.isEnabled(context))
    }

    @Test
    fun `battery card starts undismissed`() {
        assertFalse(Settings.isBatteryCardDismissed(context))
    }

    @Test
    fun `dismissing the battery card sticks`() {
        Settings.dismissBatteryCard(context)

        assertTrue(Settings.isBatteryCardDismissed(context))
    }

    @Test
    fun `the two flags do not interfere`() {
        Settings.setEnabled(context, true)
        Settings.dismissBatteryCard(context)

        assertTrue(Settings.isEnabled(context))
        assertTrue(Settings.isBatteryCardDismissed(context))
    }
}
