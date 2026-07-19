package org.yarokovisty.vpnis.feature.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class NotificationSettingsIntentsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `channel intent carries action, package, channel id and NEW_TASK flag`() {
        val intent = NotificationSettingsIntents.channelNotificationSettings(context, "vpnis_tunnel")

        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals("vpnis_tunnel", intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `app intent carries action, package and NEW_TASK flag`() {
        val intent = NotificationSettingsIntents.appNotificationSettings(context)

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
