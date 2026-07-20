package org.yarokovisty.vpnis.data.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
import org.yarokovisty.vpnis.core.format.formatBitrate
import java.time.Instant

/**
 * Unit tests for the pure, Context-free [TunnelNotifications.contentFor] mapper (issue #127).
 *
 * `contentFor` is the only unit-testable part of [TunnelNotifications]; [TunnelNotifications.build]
 * needs the Android framework (device / Robolectric) and is covered by device QA (issue #133). These
 * tests lock the state→content contract: a total map over [VpnConnectionState] to data-carrying
 * [NotificationContent] with no Android Context and no formatted strings.
 */
class TunnelNotificationsTest {

    private val server = Server(id = ServerId("s"), name = "Srv", config = "cfg")

    @Test
    fun `contentFor Loading EXPECT Inactive`() {
        assertEquals(NotificationContent.Inactive, TunnelNotifications.contentFor(VpnConnectionState.Loading))
    }

    @Test
    fun `contentFor Disconnected EXPECT Inactive`() {
        assertEquals(NotificationContent.Inactive, TunnelNotifications.contentFor(VpnConnectionState.Disconnected))
    }

    @Test
    fun `contentFor PermissionRequired EXPECT Inactive`() {
        assertEquals(
            NotificationContent.Inactive,
            TunnelNotifications.contentFor(VpnConnectionState.PermissionRequired),
        )
    }

    @Test
    fun `contentFor Error EXPECT Error carrying the reason`() {
        // issue #129: Error now maps to NotificationContent.Error(reason), not Inactive, so the
        // presenter can route it to the out-of-band alert.
        val reasons = listOf(
            ConnectionError.TunnelSetupFailed,
            ConnectionError.ServerUnreachable,
            ConnectionError.Revoked,
            ConnectionError.PermissionDenied,
            ConnectionError.Unknown("boom"),
        )
        reasons.forEach { reason ->
            assertEquals(
                NotificationContent.Error(reason),
                TunnelNotifications.contentFor(VpnConnectionState.Error(reason)),
            )
        }
    }

    @Test
    fun `contentFor Connecting EXPECT Connecting with server name`() {
        assertEquals(
            NotificationContent.Connecting(serverName = "Srv"),
            TunnelNotifications.contentFor(VpnConnectionState.Connecting(server)),
        )
    }

    @Test
    fun `contentFor Connected EXPECT Connected carrying server name since and traffic`() {
        val since = Instant.ofEpochSecond(1_700_000_000L)
        val traffic = TrafficStats(rxBytes = 10L, txBytes = 20L, rxBps = 1L, txBps = 2L)

        assertEquals(
            NotificationContent.Connected(serverName = "Srv", since = since, traffic = traffic),
            TunnelNotifications.contentFor(VpnConnectionState.Connected(server, since, traffic)),
        )
    }

    @Test
    fun `contentFor Connected EXPECT null traffic passthrough`() {
        val since = Instant.ofEpochSecond(1_700_000_000L)

        assertEquals(
            NotificationContent.Connected(serverName = "Srv", since = since, traffic = null),
            TunnelNotifications.contentFor(VpnConnectionState.Connected(server, since, traffic = null)),
        )
    }
}

/**
 * Robolectric tests for [TunnelNotifications.build] — the traffic render (issue #130) and the
 * cached disconnect [android.app.PendingIntent]. Needs a real Context so `getString` and the
 * PendingIntent resolve; the pure `contentFor` mapper is covered by [TunnelNotificationsTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TunnelNotificationsBuildTest {

    private val context = RuntimeEnvironment.getApplication()
    private val since = Instant.ofEpochSecond(1_700_000_000L)

    @Before
    fun setUp() {
        // Reset the process-lifetime disconnect-PendingIntent cache so it can't leak a stale instance
        // (from another test class in the same JVM) into these build() assertions.
        TunnelNotifications.resetCachesForTest()
    }

    @Test
    fun `build Connected with traffic EXPECT body shows formatted down and up rates`() {
        val downBps = 1_500_000L // 1.5 MB/s
        val upBps = 200_000L // 200.0 KB/s
        val content = NotificationContent.Connected(
            serverName = "Srv",
            since = since,
            traffic = TrafficStats(rxBytes = 0, txBytes = 0, rxBps = downBps, txBps = upBps),
        )

        val text = shadowOf(TunnelNotifications.build(context, content)).contentText.toString()

        // Rates are formatted via the SAME shared formatter + this module's unit labels, so build the
        // expected substrings the same way (locale-consistent with the code under test).
        val expectedDown = rate(downBps, R.string.vpn_traffic_unit_mbps)
        val expectedUp = rate(upBps, R.string.vpn_traffic_unit_kbps)
        assertTrue("expected '$expectedDown' in \"$text\"", text.contains(expectedDown))
        assertTrue("expected '$expectedUp' in \"$text\"", text.contains(expectedUp))
        assertTrue("expected server name in \"$text\"", text.contains("Srv"))
    }

    @Test
    fun `build Connected with null traffic EXPECT plain connected copy (no crash)`() {
        val content = NotificationContent.Connected(serverName = "Srv", since = since, traffic = null)

        val text = shadowOf(TunnelNotifications.build(context, content)).contentText.toString()

        assertEquals(context.getString(R.string.vpn_notification_text_connected, "Srv"), text)
    }

    @Test
    fun `build EXPECT the disconnect PendingIntent is cached across calls`() {
        val content = NotificationContent.Connected(serverName = "Srv", since = since, traffic = null)

        val first = TunnelNotifications.build(context, content)
        val second = TunnelNotifications.build(context, content)

        // Same instance ⇒ getService() ran once, not per notify() (issue #130 hot-path fix).
        assertSame(first.actions[0].actionIntent, second.actions[0].actionIntent)
    }

    /** Formats [bps] exactly as [TunnelNotifications] does: shared formatter value + module label. */
    private fun rate(bps: Long, unitLabelRes: Int): String =
        "${formatBitrate(bps).value} ${context.getString(unitLabelRes)}"
}
