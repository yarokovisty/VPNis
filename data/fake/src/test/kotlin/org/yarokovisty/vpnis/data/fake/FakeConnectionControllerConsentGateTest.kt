package org.yarokovisty.vpnis.data.fake

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId

/**
 * Unit tests for the VPN consent gate introduced in issue #57.
 *
 * Only the five consent-gate behaviours listed in the issue contract are covered here.
 * Exhaustive scenario coverage (HappyPath, HandshakeTimeout, etc.) is deferred to #58.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FakeConnectionControllerConsentGateTest {

    // -----------------------------------------------------------------------
    // Shared fixtures
    // -----------------------------------------------------------------------

    private val testServer = Server(id = ServerId("test-server"), name = "Test Server", config = "cfg-test")

    private lateinit var controller: FakeConnectionController

    @Before
    fun setUp() {
        controller = FakeConnectionController(
            scenario = FakeScenario.HappyPath,
            dispatcher = UnconfinedTestDispatcher(),
            handshakeDelayMs = 0L,
        )
    }

    @After
    fun tearDown() {
        controller.close()
    }

    // -----------------------------------------------------------------------
    // 1. First connect → PermissionRequired; no auto-progression to Connecting
    // -----------------------------------------------------------------------

    @Test
    fun `first connect with requirePermissionOnFirstConnect true EXPECT state is PermissionRequired`() = runTest {
        // Given
        // controller is freshly constructed with requirePermissionOnFirstConnect = true (default)

        // When
        controller.connect(testServer)
        advanceUntilIdle()

        // Then
        assertEquals(VpnConnectionState.PermissionRequired, controller.state.value())
    }

    @Test
    fun `first connect with gate enabled EXPECT state does not advance to Connecting on its own`() = runTest {
        // Given
        // controller is freshly constructed with requirePermissionOnFirstConnect = true (default)

        controller.state.test {
            skipItems(1) // initial Disconnected

            // When
            controller.connect(testServer)
            advanceUntilIdle()

            // Then — only PermissionRequired arrives; no further emission
            assertEquals(VpnConnectionState.PermissionRequired, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -----------------------------------------------------------------------
    // 2. Second connect while PermissionRequired → consent granted → Connecting → Connected
    // -----------------------------------------------------------------------

    @Test
    fun `second connect while PermissionRequired EXPECT state advances to Connecting`() = runTest {
        // Given
        controller.connect(testServer) // first connect → PermissionRequired
        advanceUntilIdle()
        assertEquals(VpnConnectionState.PermissionRequired, controller.state.value())

        // When
        controller.connect(testServer) // second connect → consent granted
        advanceUntilIdle()

        // Then — state has moved past PermissionRequired (at minimum to Connecting or Connected)
        val finalState = controller.state.value()
        assertTrue(
            "Expected Connecting or Connected, got $finalState",
            finalState is VpnConnectionState.Connecting || finalState is VpnConnectionState.Connected,
        )
    }

    @Test
    fun `second connect while PermissionRequired with HappyPath scenario EXPECT terminal state is Connected`() =
        runTest {
            // Given
            controller.connect(testServer) // first connect → PermissionRequired
            advanceUntilIdle()

            // When
            controller.connect(testServer) // second connect → consent granted, runs HappyPath
            advanceUntilIdle()

            // Then
            assertTrue(controller.state.value() is VpnConnectionState.Connected)
        }

    @Test
    fun `second connect while PermissionRequired EXPECT PermissionRequired Connecting Connected emitted in order`() =
        runTest {
            // Given
            controller.state.test {
                skipItems(1) // initial Disconnected

                controller.connect(testServer) // first connect -> PermissionRequired
                advanceUntilIdle()
                assertEquals(VpnConnectionState.PermissionRequired, awaitItem())

                // When
                controller.connect(testServer) // second connect -> consent granted
                advanceUntilIdle()

                // Then
                val connectingItem = awaitItem()
                assertTrue(connectingItem is VpnConnectionState.Connecting)

                val connectedItem = awaitItem()
                assertTrue(connectedItem is VpnConnectionState.Connected)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // -----------------------------------------------------------------------
    // 3. Disconnect while PermissionRequired → Disconnected; next connect asks again
    // -----------------------------------------------------------------------

    @Test
    fun `disconnect while PermissionRequired EXPECT state becomes Disconnected`() = runTest {
        // Given
        controller.connect(testServer) // first connect → PermissionRequired
        advanceUntilIdle()
        assertEquals(VpnConnectionState.PermissionRequired, controller.state.value())

        // When
        controller.disconnect() // refusal path
        advanceUntilIdle()

        // Then
        assertEquals(VpnConnectionState.Disconnected, controller.state.value())
    }

    @Test
    fun `connect after refusal EXPECT state becomes PermissionRequired again`() = runTest {
        // Given
        controller.connect(testServer) // first connect → PermissionRequired
        advanceUntilIdle()
        controller.disconnect() // refuse consent
        advanceUntilIdle()
        assertEquals(VpnConnectionState.Disconnected, controller.state.value())

        // When
        controller.connect(testServer) // subsequent connect — consent still not granted
        advanceUntilIdle()

        // Then — gate fires again because permissionGranted is still false
        assertEquals(VpnConnectionState.PermissionRequired, controller.state.value())
    }

    // -----------------------------------------------------------------------
    // 4. After consent granted once, disconnect + reconnect skips the gate
    // -----------------------------------------------------------------------

    @Test
    fun `reconnect after consent was already granted EXPECT state skips PermissionRequired and goes to Connecting`() =
        runTest {
            // Given — grant consent by completing the two-connect handshake
            controller.connect(testServer) // → PermissionRequired
            advanceUntilIdle()
            controller.connect(testServer) // → consent granted → Connected
            advanceUntilIdle()
            assertTrue(controller.state.value() is VpnConnectionState.Connected)

            controller.disconnect() // disconnect — consent flag NOT reset
            advanceUntilIdle()
            assertEquals(VpnConnectionState.Disconnected, controller.state.value())

            // When
            controller.state.test {
                skipItems(1) // current Disconnected

                controller.connect(testServer) // third connect — should bypass gate
                advanceUntilIdle()

                // Then — first new emission must be Connecting, not PermissionRequired
                val firstNewItem = awaitItem()
                assertTrue(
                    "Expected Connecting after consent persisted, got $firstNewItem",
                    firstNewItem is VpnConnectionState.Connecting,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -----------------------------------------------------------------------
    // 5. requirePermissionOnFirstConnect = false → gate bypassed
    // -----------------------------------------------------------------------

    @Test
    fun `first connect with requirePermissionOnFirstConnect false EXPECT state is Connecting immediately`() = runTest {
        // Given
        val controllerNoGate = FakeConnectionController(
            scenario = FakeScenario.HappyPath,
            dispatcher = UnconfinedTestDispatcher(),
            handshakeDelayMs = 0L,
            requirePermissionOnFirstConnect = false,
        )

        controllerNoGate.state.test {
            skipItems(1) // initial Disconnected

            // When
            controllerNoGate.connect(testServer)
            advanceUntilIdle()

            // Then — gate is bypassed; first new emission is Connecting
            val firstItem = awaitItem()
            assertTrue(
                "Expected Connecting, got $firstItem",
                firstItem is VpnConnectionState.Connecting,
            )
            cancelAndIgnoreRemainingEvents()
        }

        controllerNoGate.close()
    }

    @Test
    fun `first connect with requirePermissionOnFirstConnect false EXPECT final state is not PermissionRequired`() =
        runTest {
            // Given
            val controllerNoGate = FakeConnectionController(
                scenario = FakeScenario.HappyPath,
                dispatcher = UnconfinedTestDispatcher(),
                handshakeDelayMs = 0L,
                requirePermissionOnFirstConnect = false,
            )

            // When
            controllerNoGate.connect(testServer)
            advanceUntilIdle()

            // Then — gate bypassed; final state is Connected, never PermissionRequired
            val finalState = controllerNoGate.state.value()
            assertTrue(
                "Expected Connected (gate bypassed), got $finalState",
                finalState is VpnConnectionState.Connected,
            )

            controllerNoGate.close()
        }
}

// Extension to read a Flow<T>'s current value via StateFlow cast.
// FakeConnectionController.state is backed by a MutableStateFlow so the cast is safe.
@Suppress("UNCHECKED_CAST")
private fun <T> kotlinx.coroutines.flow.Flow<T>.value(): T = (this as kotlinx.coroutines.flow.StateFlow<T>).value
