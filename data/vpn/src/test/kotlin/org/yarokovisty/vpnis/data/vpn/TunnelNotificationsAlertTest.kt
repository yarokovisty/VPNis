package org.yarokovisty.vpnis.data.vpn

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import java.time.Instant

/**
 * Robolectric tests for the error-alert surface of [TunnelNotifications] (issue #129).
 *
 * Covers the Context-dependent pieces that the pure-JVM `TunnelNotificationsTest` cannot:
 * - [TunnelNotifications.createAlertChannel] importance (heads-up vs the silent FGS channel),
 * - [TunnelNotifications.buildAlert] channel / dismissibility / ongoing flags,
 * - the `Unknown.message` never leaking into the alert body,
 * - the invariant that [NotificationContent.Error] can never be rendered as the ongoing notification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TunnelNotificationsAlertTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        TunnelNotifications.createChannel(context)
        TunnelNotifications.createAlertChannel(context)
    }

    // -------------------------------------------------------------------------
    // Channel
    // -------------------------------------------------------------------------

    @Test
    fun `createAlertChannel EXPECT IMPORTANCE_DEFAULT so it can heads-up`() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(TunnelNotifications.ALERT_CHANNEL_ID)

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun `alert channel is distinct from the low-importance tunnel channel`() {
        val manager = context.getSystemService(NotificationManager::class.java)

        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            manager.getNotificationChannel(TunnelNotifications.CHANNEL_ID).importance,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            manager.getNotificationChannel(TunnelNotifications.ALERT_CHANNEL_ID).importance,
        )
    }

    // -------------------------------------------------------------------------
    // buildAlert
    // -------------------------------------------------------------------------

    @Test
    fun `buildAlert EXPECT posted on the alert channel, dismissible and not ongoing`() {
        val alert = TunnelNotifications.buildAlert(context, ConnectionError.TunnelSetupFailed)

        assertEquals(TunnelNotifications.ALERT_CHANNEL_ID, alert.channelId)
        // Dismissible: auto-cancel set, ongoing flag NOT set (unlike the FGS notification).
        assertTrue("alert must be auto-cancel", alert.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertFalse("alert must not be ongoing", alert.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `buildAlert with Unknown EXPECT raw message is never shown in the body`() {
        val secret = "raw-technical-detail-xyz"
        val alert = TunnelNotifications.buildAlert(context, ConnectionError.Unknown(secret))

        val text = alert.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val title = alert.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        assertFalse("Unknown.message must not leak into the alert text", text.contains(secret))
        assertFalse("Unknown.message must not leak into the alert title", title.contains(secret))
    }

    @Test
    fun `buildAlert covers every ConnectionError reason without throwing`() {
        val reasons = listOf(
            ConnectionError.ServerUnreachable,
            ConnectionError.TunnelSetupFailed,
            ConnectionError.Revoked,
            ConnectionError.PermissionDenied,
            ConnectionError.Unknown(null),
        )
        reasons.forEach { reason ->
            val alert = TunnelNotifications.buildAlert(context, reason)
            assertEquals(TunnelNotifications.ALERT_CHANNEL_ID, alert.channelId)
        }
    }

    // -------------------------------------------------------------------------
    // Invariant — Error is never rendered as the ongoing notification
    // -------------------------------------------------------------------------

    @Test
    fun `build with Error content EXPECT hard failure, never rendered as ongoing`() {
        assertThrows(IllegalStateException::class.java) {
            TunnelNotifications.build(context, NotificationContent.Error(ConnectionError.TunnelSetupFailed))
        }
    }

    @Test
    fun `build with Connected content still succeeds on the tunnel channel`() {
        val connected = NotificationContent.Connected(
            serverName = "Srv",
            since = Instant.ofEpochSecond(1_700_000_000L),
            traffic = null,
        )
        val notification = TunnelNotifications.build(context, connected)

        assertEquals(TunnelNotifications.CHANNEL_ID, notification.channelId)
    }
}
