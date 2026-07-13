package org.yarokovisty.vpnis.core.domain.connection

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
import java.time.Instant

/**
 * Exhaustive 6x6 matrix test for [isLegalTransition].
 *
 * Each row is: (from, to, expectedLegal).
 * The matrix is derived entirely from the KDoc transition table in [VpnConnectionState];
 * no logic is used inside the test body itself.
 */
@RunWith(Parameterized::class)
class VpnConnectionTransitionsTest(
    private val description: String,
    private val from: VpnConnectionState,
    private val to: VpnConnectionState,
    private val expectedLegal: Boolean,
) {

    @Test
    fun `isLegalTransition EXPECT result matching legal transition table`() {
        // Given — supplied by the Parameterized runner (from, to, expectedLegal).

        // When
        val result = isLegalTransition(from, to)

        // Then
        assertEquals("$description: isLegalTransition($from, $to)", expectedLegal, result)
    }

    companion object {

        // Representative instances — one per variant, with distinct payloads where the
        // variant carries data, so test descriptions remain unambiguous.
        private val server1 = Server(id = ServerId("s1"), name = "Server 1", config = "cfg1")
        private val server2 = Server(id = ServerId("s2"), name = "Server 2", config = "cfg2")
        private val instant1 = Instant.ofEpochSecond(1_000_000L)
        private val instant2 = Instant.ofEpochSecond(2_000_000L)

        private val stLoading = VpnConnectionState.Loading
        private val stDisconnected = VpnConnectionState.Disconnected
        private val stPermissionRequired = VpnConnectionState.PermissionRequired
        private val stConnecting = VpnConnectionState.Connecting(server1)
        private val stConnected1 = VpnConnectionState.Connected(server1, instant1, TrafficStats.EMPTY)
        private val stConnected2 = VpnConnectionState.Connected(server2, instant2, null)
        private val stError = VpnConnectionState.Error(ConnectionError.ServerUnreachable)
        private val stError2 = VpnConnectionState.Error(ConnectionError.Unknown("retry"))

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            // ---- From: Loading ----
            row("Loading -> Loading", stLoading, stLoading, false),
            row("Loading -> Disconnected", stLoading, stDisconnected, true),
            row("Loading -> PermissionRequired", stLoading, stPermissionRequired, true),
            row("Loading -> Connecting", stLoading, stConnecting, false),
            row("Loading -> Connected", stLoading, stConnected1, true),
            row("Loading -> Error", stLoading, stError, true),

            // ---- From: Disconnected ----
            row("Disconnected -> Loading", stDisconnected, stLoading, false),
            row("Disconnected -> Disconnected", stDisconnected, stDisconnected, false),
            row("Disconnected -> PermissionRequired", stDisconnected, stPermissionRequired, true),
            row("Disconnected -> Connecting", stDisconnected, stConnecting, true),
            row("Disconnected -> Connected", stDisconnected, stConnected1, false),
            row("Disconnected -> Error", stDisconnected, stError, false),

            // ---- From: PermissionRequired ----
            row("PermissionRequired -> Loading", stPermissionRequired, stLoading, false),
            row("PermissionRequired -> Disconnected", stPermissionRequired, stDisconnected, true),
            row("PermissionRequired -> PermissionRequired", stPermissionRequired, stPermissionRequired, false),
            row("PermissionRequired -> Connecting", stPermissionRequired, stConnecting, true),
            row("PermissionRequired -> Connected", stPermissionRequired, stConnected1, false),
            row("PermissionRequired -> Error", stPermissionRequired, stError, false),

            // ---- From: Connecting ----
            row("Connecting -> Loading", stConnecting, stLoading, false),
            row("Connecting -> Disconnected", stConnecting, stDisconnected, true),
            row("Connecting -> PermissionRequired", stConnecting, stPermissionRequired, false),
            row("Connecting -> Connecting", stConnecting, stConnecting, false),
            row("Connecting -> Connected", stConnecting, stConnected1, true),
            row("Connecting -> Error", stConnecting, stError, true),

            // ---- From: Connected ----
            row("Connected -> Loading", stConnected1, stLoading, false),
            row("Connected -> Disconnected", stConnected1, stDisconnected, true),
            row("Connected -> PermissionRequired", stConnected1, stPermissionRequired, false),
            row("Connected -> Connecting", stConnected1, stConnecting, false),
            // Self-transition with a *different* Connected instance (different server + traffic)
            row("Connected -> Connected (self, different payload)", stConnected1, stConnected2, true),
            row("Connected -> Error", stConnected1, stError, true),

            // ---- From: Error ----
            row("Error -> Loading", stError, stLoading, false),
            row("Error -> Disconnected", stError, stDisconnected, true),
            row("Error -> PermissionRequired", stError, stPermissionRequired, true),
            row("Error -> Connecting", stError, stConnecting, true),
            row("Error -> Connected", stError, stConnected1, false),
            // Self-transition with a *different* Error instance (different reason)
            row("Error -> Error (self, different reason)", stError, stError2, true),
        )

        private fun row(
            description: String,
            from: VpnConnectionState,
            to: VpnConnectionState,
            expectedLegal: Boolean,
        ): Array<Any> = arrayOf(description, from, to, expectedLegal)
    }
}
