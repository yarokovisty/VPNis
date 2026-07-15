package org.yarokovisty.vpnis.data.vpn

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId

/**
 * Unit tests for [ConnectionControllerImpl].
 *
 * A hand-written [FakeTunnelLauncher] records invocations so tests can assert
 * launcher interactions without a mocking framework (consistent with the rest of
 * :data:vpn's test style).
 *
 * [android.util.Log] stubs are silenced via
 * `testOptions { unitTests { isReturnDefaultValues = true } }` in build.gradle.kts.
 */
class ConnectionControllerImplTest {

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    // Valid VLESS/Reality URIs so XrayConfigBuilder.build succeeds in the happy path.
    private val server = Server(
        id = ServerId("srv-1"),
        name = "Test Server",
        config = "vless://00000000-0000-0000-0000-000000000001@example.com:443" +
            "?type=tcp&security=reality&pbk=pubkey1&fp=chrome&sni=example.com&sid=abcd1234",
    )
    private val otherServer = Server(
        id = ServerId("srv-2"),
        name = "Other Server",
        config = "vless://00000000-0000-0000-0000-000000000002@other.com:443" +
            "?type=tcp&security=reality&pbk=pubkey2&fp=firefox&sni=other.com&sid=ef012345",
    )

    private fun makeController(launcher: FakeTunnelLauncher = FakeTunnelLauncher()): ConnectionControllerImpl =
        ConnectionControllerImpl(launcher)

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial state EXPECT Disconnected`() = runTest {
        // Given
        val controller = makeController()

        // When
        val state = controller.state.first()

        // Then
        assertEquals(VpnConnectionState.Disconnected, state)
    }

    // -------------------------------------------------------------------------
    // connect — state transition
    // -------------------------------------------------------------------------

    @Test
    fun `connect from Disconnected EXPECT state is Connecting`() = runTest {
        // Given
        val controller = makeController()

        // When
        controller.connect(server)

        // Then
        val state = controller.state.first()
        assertTrue(state is VpnConnectionState.Connecting)
    }

    @Test
    fun `connect from Disconnected EXPECT Connecting carries the target server`() = runTest {
        // Given
        val controller = makeController()

        // When
        controller.connect(server)

        // Then
        val state = controller.state.first() as VpnConnectionState.Connecting
        assertEquals(server, state.server)
    }

    // -------------------------------------------------------------------------
    // connect — launcher interaction
    // -------------------------------------------------------------------------

    @Test
    fun `connect EXPECT launcher launch called exactly once`() = runTest {
        // Given
        val launcher = FakeTunnelLauncher()
        val controller = makeController(launcher)

        // When
        controller.connect(server)

        // Then
        assertEquals(1, launcher.launchCount)
    }

    @Test
    fun `connect EXPECT launcher launch called with the correct server`() = runTest {
        // Given
        val launcher = FakeTunnelLauncher()
        val controller = makeController(launcher)

        // When
        controller.connect(server)

        // Then
        assertEquals(server, launcher.lastLaunchedServer)
    }

    // -------------------------------------------------------------------------
    // disconnect — state transition
    // -------------------------------------------------------------------------

    @Test
    fun `disconnect after connect EXPECT state is Disconnected`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)

        // When
        controller.disconnect()

        // Then
        val state = controller.state.first()
        assertEquals(VpnConnectionState.Disconnected, state)
    }

    // -------------------------------------------------------------------------
    // disconnect — launcher interaction
    // -------------------------------------------------------------------------

    @Test
    fun `disconnect EXPECT launcher stop called exactly once`() = runTest {
        // Given
        val launcher = FakeTunnelLauncher()
        val controller = makeController(launcher)
        controller.connect(server)

        // When
        controller.disconnect()

        // Then
        assertEquals(1, launcher.stopCount)
    }

    // -------------------------------------------------------------------------
    // onTunnelEstablished — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `onTunnelEstablished after connect EXPECT state is Connected`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)

        // When
        controller.onTunnelEstablished()

        // Then
        val state = controller.state.first()
        assertTrue(state is VpnConnectionState.Connected)
    }

    @Test
    fun `onTunnelEstablished after connect EXPECT Connected carries the connected server`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)

        // When
        controller.onTunnelEstablished()

        // Then
        val state = controller.state.first() as VpnConnectionState.Connected
        assertEquals(server, state.server)
    }

    @Test
    fun `onTunnelEstablished after connect EXPECT Connected traffic is null`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)

        // When
        controller.onTunnelEstablished()

        // Then
        val state = controller.state.first() as VpnConnectionState.Connected
        assertNull(state.traffic)
    }

    @Test
    fun `onTunnelEstablished after connect EXPECT Connected since is not null`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)

        // When
        controller.onTunnelEstablished()

        // Then
        val state = controller.state.first() as VpnConnectionState.Connected
        assertNotNull(state.since)
    }

    // -------------------------------------------------------------------------
    // onTunnelStopped
    // -------------------------------------------------------------------------

    @Test
    fun `onTunnelStopped from Connecting EXPECT state is Disconnected`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)

        // When
        controller.onTunnelStopped()

        // Then
        val state = controller.state.first()
        assertEquals(VpnConnectionState.Disconnected, state)
    }

    @Test
    fun `onTunnelStopped from Connected EXPECT state is Disconnected`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)
        controller.onTunnelEstablished()

        // When
        controller.onTunnelStopped()

        // Then
        val state = controller.state.first()
        assertEquals(VpnConnectionState.Disconnected, state)
    }

    // -------------------------------------------------------------------------
    // onTunnelError
    // -------------------------------------------------------------------------

    @Test
    fun `onTunnelError from Connecting EXPECT state is Error`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)

        // When
        controller.onTunnelError(ConnectionError.ServerUnreachable)

        // Then
        val state = controller.state.first()
        assertTrue(state is VpnConnectionState.Error)
    }

    @Test
    fun `onTunnelError from Connecting EXPECT Error carries the given reason`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)

        // When
        controller.onTunnelError(ConnectionError.ServerUnreachable)

        // Then
        val state = controller.state.first() as VpnConnectionState.Error
        assertEquals(ConnectionError.ServerUnreachable, state.reason)
    }

    @Test
    fun `onTunnelError from Connected EXPECT state is Error`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)
        controller.onTunnelEstablished()

        // When
        controller.onTunnelError(ConnectionError.Revoked)

        // Then
        val state = controller.state.first()
        assertTrue(state is VpnConnectionState.Error)
    }

    @Test
    fun `onTunnelError from Connected EXPECT Error carries the given reason`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)
        controller.onTunnelEstablished()

        // When
        controller.onTunnelError(ConnectionError.Revoked)

        // Then
        val state = controller.state.first() as VpnConnectionState.Error
        assertEquals(ConnectionError.Revoked, state.reason)
    }

    // -------------------------------------------------------------------------
    // onPermissionRequired
    // -------------------------------------------------------------------------

    @Test
    fun `onPermissionRequired from Disconnected EXPECT state is PermissionRequired`() = runTest {
        // Given
        val controller = makeController()

        // When
        controller.onPermissionRequired()

        // Then
        val state = controller.state.first()
        assertEquals(VpnConnectionState.PermissionRequired, state)
    }

    @Test
    fun `connect after onPermissionRequired EXPECT state is Connecting`() = runTest {
        // Given
        val controller = makeController()
        controller.onPermissionRequired()

        // When
        controller.connect(server)

        // Then
        val state = controller.state.first()
        assertTrue(state is VpnConnectionState.Connecting)
    }

    @Test
    fun `connect after onPermissionRequired EXPECT Connecting carries the target server`() = runTest {
        // Given
        val controller = makeController()
        controller.onPermissionRequired()

        // When
        controller.connect(server)

        // Then
        val state = controller.state.first() as VpnConnectionState.Connecting
        assertEquals(server, state.server)
    }

    // -------------------------------------------------------------------------
    // Illegal-transition guard — late onTunnelEstablished after disconnect
    // -------------------------------------------------------------------------

    @Test
    fun `onTunnelEstablished after disconnect EXPECT state stays Disconnected`() = runTest {
        // Given — connect then immediately disconnect (simulates connect/disconnect race)
        val controller = makeController()
        controller.connect(server)
        controller.disconnect()

        // When — late callback from the service arrives after the disconnect
        controller.onTunnelEstablished()

        // Then — the illegal Disconnected → Connected transition is dropped
        val state = controller.state.first()
        assertEquals(VpnConnectionState.Disconnected, state)
    }

    // -------------------------------------------------------------------------
    // Illegal-transition guard — onTunnelEstablished when currentTarget is null
    // -------------------------------------------------------------------------

    @Test
    fun `onTunnelEstablished when never connected EXPECT state stays Disconnected`() = runTest {
        // Given — fresh controller; connect() was never called so currentTarget is null
        val controller = makeController()

        // When
        controller.onTunnelEstablished()

        // Then — early-return guard drops the transition; state is untouched
        val state = controller.state.first()
        assertEquals(VpnConnectionState.Disconnected, state)
    }

    // -------------------------------------------------------------------------
    // connect — reconnect replaces currentTarget
    // -------------------------------------------------------------------------

    @Test
    fun `connect to different server after first connect EXPECT Connecting carries the new server`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)
        controller.disconnect()

        // When
        controller.connect(otherServer)

        // Then
        val state = controller.state.first() as VpnConnectionState.Connecting
        assertEquals(otherServer, state.server)
    }

    @Test
    fun `onTunnelEstablished after reconnect to different server EXPECT Connected carries the new server`() = runTest {
        // Given
        val controller = makeController()
        controller.connect(server)
        controller.disconnect()
        controller.connect(otherServer)

        // When
        controller.onTunnelEstablished()

        // Then
        val state = controller.state.first() as VpnConnectionState.Connected
        assertEquals(otherServer, state.server)
    }

    // -------------------------------------------------------------------------
    // T-2 — config flow: valid URI → launcher receives non-blank configJson
    // -------------------------------------------------------------------------

    @Test
    fun `connect with valid VLESS URI EXPECT launcher launch called with non-null configJson`() = runTest {
        // Given
        val launcher = FakeTunnelLauncher()
        val controller = makeController(launcher)

        // When
        controller.connect(server)

        // Then
        assertNotNull(launcher.lastLaunchedConfigJson)
    }

    @Test
    fun `connect with valid VLESS URI EXPECT launcher launch called with non-blank configJson`() = runTest {
        // Given
        val launcher = FakeTunnelLauncher()
        val controller = makeController(launcher)

        // When
        controller.connect(server)

        // Then
        assertTrue(launcher.lastLaunchedConfigJson!!.isNotBlank())
    }

    @Test
    fun `connect with valid VLESS URI EXPECT state reaches Connecting`() = runTest {
        // Given
        val launcher = FakeTunnelLauncher()
        val controller = makeController(launcher)

        // When
        controller.connect(server)

        // Then
        val state = controller.state.first()
        assertTrue(state is VpnConnectionState.Connecting)
    }

    // -------------------------------------------------------------------------
    // T-2 — config flow: malformed URI → Error, launcher never called
    // -------------------------------------------------------------------------

    @Test
    fun `connect with malformed config URI EXPECT final state is Error`() = runTest {
        // Given
        val launcher = FakeTunnelLauncher()
        val malformedServer = server.copy(config = "not-a-vless-uri")
        val controller = makeController(launcher)

        // When
        controller.connect(malformedServer)

        // Then
        val state = controller.state.first()
        assertTrue(state is VpnConnectionState.Error)
    }

    @Test
    fun `connect with malformed config URI EXPECT Error reason is TunnelSetupFailed`() = runTest {
        // Given
        val launcher = FakeTunnelLauncher()
        val malformedServer = server.copy(config = "not-a-vless-uri")
        val controller = makeController(launcher)

        // When
        controller.connect(malformedServer)

        // Then
        val state = controller.state.first() as VpnConnectionState.Error
        assertEquals(org.yarokovisty.vpnis.core.domain.model.ConnectionError.TunnelSetupFailed, state.reason)
    }

    @Test
    fun `connect with malformed config URI EXPECT launcher launch is never called`() = runTest {
        // Given
        val launcher = FakeTunnelLauncher()
        val malformedServer = server.copy(config = "not-a-vless-uri")
        val controller = makeController(launcher)

        // When
        controller.connect(malformedServer)

        // Then
        assertEquals(0, launcher.launchCount)
    }
}

// -------------------------------------------------------------------------
// Fake collaborator
// -------------------------------------------------------------------------

/**
 * Recording fake [TunnelLauncher].
 *
 * Tracks every [launch] and [stop] call so tests can assert interaction counts,
 * the server argument, and the config JSON without a mocking framework.
 */
private class FakeTunnelLauncher : TunnelLauncher {

    var launchCount: Int = 0
        private set

    var stopCount: Int = 0
        private set

    var lastLaunchedServer: Server? = null
        private set

    var lastLaunchedConfigJson: String? = null
        private set

    override fun launch(server: Server, configJson: String) {
        launchCount++
        lastLaunchedServer = server
        lastLaunchedConfigJson = configJson
    }

    override fun stop() {
        stopCount++
    }
}
