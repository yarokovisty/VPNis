package org.yarokovisty.vpnis.data.vpn

import org.junit.Assert.assertEquals
import org.junit.Test
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
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
    fun `contentFor Error EXPECT Inactive`() {
        assertEquals(
            NotificationContent.Inactive,
            TunnelNotifications.contentFor(VpnConnectionState.Error(ConnectionError.TunnelSetupFailed)),
        )
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
