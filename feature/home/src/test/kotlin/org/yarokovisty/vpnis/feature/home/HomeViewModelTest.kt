package org.yarokovisty.vpnis.feature.home

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeViewModelTest {

    // -----------------------------------------------------------------------
    // Shared test fixtures
    // -----------------------------------------------------------------------

    private val serverA = Server(id = ServerId("a"), name = "Server A", config = "cfg-a")
    private val serverB = Server(id = ServerId("b"), name = "Server B", config = "cfg-b")
    private val instant = Instant.ofEpochSecond(1_700_000_000L)
    private val traffic = TrafficStats(rxBytes = 1024L, txBytes = 512L, rxBps = 100L, txBps = 50L)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // Helper: creates a ViewModel with freshly constructed fakes
    // -----------------------------------------------------------------------

    private fun buildViewModel(
        controller: FakeConnectionController = FakeConnectionController(),
        servers: FakeServerRepository = FakeServerRepository(),
        notificationPermission: FakeNotificationPermissionState = FakeNotificationPermissionState(),
    ): Triple<HomeViewModel, FakeConnectionController, FakeServerRepository> {
        val vm = HomeViewModel(controller, servers, notificationPermission)
        return Triple(vm, controller, servers)
    }

    // =======================================================================
    // State mapping
    // =======================================================================

    @Test
    fun `cold start with no upstream emissions EXPECT Loading state`() = runTest {
        // Given
        val (viewModel, _, _) = buildViewModel()

        // When
        viewModel.uiState.test {
            // Then
            assertEquals(HomeUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `controller Disconnected and selected server present EXPECT Disconnected state with that server`() = runTest {
        // Given
        val controller = FakeConnectionController(initialState = VpnConnectionState.Loading)
        val servers = FakeServerRepository(initialSelected = serverA)
        val viewModel = HomeViewModel(controller, servers, FakeNotificationPermissionState())

        // Then — subscriber must be active before emit so WhileSubscribed keeps the upstream alive
        viewModel.uiState.test {
            skipItems(1) // initial Loading

            // When
            controller.emit(VpnConnectionState.Disconnected)
            advanceUntilIdle()

            assertEquals(HomeUiState.Disconnected(serverA), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `controller Disconnected and no selected server EXPECT Disconnected state with null server`() = runTest {
        // Given
        val controller = FakeConnectionController(initialState = VpnConnectionState.Loading)
        val servers = FakeServerRepository(initialSelected = null)
        val viewModel = HomeViewModel(controller, servers, FakeNotificationPermissionState())

        // Then — subscriber must be active before emit so WhileSubscribed keeps the upstream alive
        viewModel.uiState.test {
            skipItems(1) // initial Loading

            // When
            controller.emit(VpnConnectionState.Disconnected)
            advanceUntilIdle()

            assertEquals(HomeUiState.Disconnected(null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `controller PermissionRequired with selected server EXPECT Disconnected state not a distinct state`() =
        runTest {
            // Given
            val controller = FakeConnectionController(initialState = VpnConnectionState.Loading)
            val servers = FakeServerRepository(initialSelected = serverA)
            val viewModel = HomeViewModel(controller, servers, FakeNotificationPermissionState())

            // Then — subscriber must be active before emit so WhileSubscribed keeps the upstream alive
            viewModel.uiState.test {
                skipItems(1) // initial Loading

                // When
                controller.emit(VpnConnectionState.PermissionRequired)
                advanceUntilIdle()

                assertEquals(HomeUiState.Disconnected(serverA), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `controller Connecting EXPECT Connecting state with that server`() = runTest {
        // Given
        val controller = FakeConnectionController(initialState = VpnConnectionState.Loading)
        val servers = FakeServerRepository()
        val viewModel = HomeViewModel(controller, servers, FakeNotificationPermissionState())

        // Then — subscriber must be active before emit so WhileSubscribed keeps the upstream alive
        viewModel.uiState.test {
            skipItems(1) // initial Loading

            // When
            controller.emit(VpnConnectionState.Connecting(serverA))
            advanceUntilIdle()

            assertEquals(HomeUiState.Connecting(serverA), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `controller Connected with non-null traffic EXPECT Connected state with traffic`() = runTest {
        // Given
        val controller = FakeConnectionController(initialState = VpnConnectionState.Loading)
        val servers = FakeServerRepository()
        val viewModel = HomeViewModel(controller, servers, FakeNotificationPermissionState())

        // Then — subscriber must be active before emit so WhileSubscribed keeps the upstream alive
        viewModel.uiState.test {
            skipItems(1) // initial Loading

            // When
            controller.emit(VpnConnectionState.Connected(server = serverA, since = instant, traffic = traffic))
            advanceUntilIdle()

            assertEquals(HomeUiState.Connected(server = serverA, since = instant, traffic = traffic), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `controller Connected with null traffic EXPECT Connected state with null traffic`() = runTest {
        // Given
        val controller = FakeConnectionController(initialState = VpnConnectionState.Loading)
        val servers = FakeServerRepository()
        val viewModel = HomeViewModel(controller, servers, FakeNotificationPermissionState())

        // Then — subscriber must be active before emit so WhileSubscribed keeps the upstream alive
        viewModel.uiState.test {
            skipItems(1) // initial Loading

            // When
            controller.emit(VpnConnectionState.Connected(server = serverA, since = instant, traffic = null))
            advanceUntilIdle()

            assertEquals(HomeUiState.Connected(server = serverA, since = instant, traffic = null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `controller Error with selected server EXPECT Error state with reason and server`() = runTest {
        // Given
        val controller = FakeConnectionController(initialState = VpnConnectionState.Loading)
        val servers = FakeServerRepository(initialSelected = serverA)
        val viewModel = HomeViewModel(controller, servers, FakeNotificationPermissionState())

        // Then — subscriber must be active before emit so WhileSubscribed keeps the upstream alive
        viewModel.uiState.test {
            skipItems(1) // initial Loading

            // When
            controller.emit(VpnConnectionState.Error(ConnectionError.ServerUnreachable))
            advanceUntilIdle()

            assertEquals(HomeUiState.Error(reason = ConnectionError.ServerUnreachable, server = serverA), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // =======================================================================
    // Effects — permission
    // =======================================================================

    @Test
    fun `entering PermissionRequired EXPECT RequestVpnPermission effect emitted exactly once`() = runTest {
        // Given
        val controller = FakeConnectionController(initialState = VpnConnectionState.Disconnected)
        val viewModel = HomeViewModel(controller, FakeServerRepository(), FakeNotificationPermissionState())

        viewModel.effects.test {
            // When
            controller.emit(VpnConnectionState.PermissionRequired)
            advanceUntilIdle()

            // Then
            assertEquals(HomeEffect.RequestVpnPermission, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PermissionRequired then Disconnected then PermissionRequired EXPECT RequestVpnPermission emitted twice`() =
        runTest {
            // Given
            val controller = FakeConnectionController(initialState = VpnConnectionState.Disconnected)
            val viewModel = HomeViewModel(controller, FakeServerRepository(), FakeNotificationPermissionState())

            viewModel.effects.test {
                // When — first entry into PermissionRequired
                controller.emit(VpnConnectionState.PermissionRequired)
                advanceUntilIdle()
                assertEquals(HomeEffect.RequestVpnPermission, awaitItem())

                // When — exit and re-enter PermissionRequired
                controller.emit(VpnConnectionState.Disconnected)
                advanceUntilIdle()
                controller.emit(VpnConnectionState.PermissionRequired)
                advanceUntilIdle()

                // Then — second effect
                assertEquals(HomeEffect.RequestVpnPermission, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `non-permission transition Disconnected to Connecting EXPECT no effect emitted`() = runTest {
        // Given
        val controller = FakeConnectionController(initialState = VpnConnectionState.Disconnected)
        val viewModel = HomeViewModel(controller, FakeServerRepository(), FakeNotificationPermissionState())

        viewModel.effects.test {
            // When
            controller.emit(VpnConnectionState.Connecting(serverA))
            advanceUntilIdle()

            // Then
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // =======================================================================
    // Effects — navigation
    // =======================================================================

    @Test
    fun `onIntent AddServer EXPECT NavigateToServers effect emitted`() = runTest {
        // Given
        val viewModel =
            HomeViewModel(FakeConnectionController(), FakeServerRepository(), FakeNotificationPermissionState())

        viewModel.effects.test {
            // When
            viewModel.onIntent(HomeIntent.AddServer)
            advanceUntilIdle()

            // Then
            assertEquals(HomeEffect.NavigateToServers, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // =======================================================================
    // Intent → controller / repository
    // =======================================================================

    @Test
    fun `onIntent Connect with selected server EXPECT controller connect called with that server`() = runTest {
        // Given
        val controller = FakeConnectionController()
        val servers = FakeServerRepository(initialSelected = serverA)
        val viewModel = HomeViewModel(controller, servers, FakeNotificationPermissionState())

        // When
        viewModel.onIntent(HomeIntent.Connect)
        advanceUntilIdle()

        // Then
        assertEquals(listOf(serverA), controller.connectCalls)
    }

    @Test
    fun `onIntent Connect with no selected server EXPECT controller connect not called`() = runTest {
        // Given
        val controller = FakeConnectionController()
        val servers = FakeServerRepository(initialSelected = null)
        val viewModel = HomeViewModel(controller, servers, FakeNotificationPermissionState())

        // When
        viewModel.onIntent(HomeIntent.Connect)
        advanceUntilIdle()

        // Then
        assertTrue(controller.connectCalls.isEmpty())
    }

    @Test
    fun `onIntent Retry with selected server EXPECT controller connect called with that server`() = runTest {
        // Given
        val controller = FakeConnectionController()
        val servers = FakeServerRepository(initialSelected = serverB)
        val viewModel = HomeViewModel(controller, servers, FakeNotificationPermissionState())

        // When
        viewModel.onIntent(HomeIntent.Retry)
        advanceUntilIdle()

        // Then
        assertEquals(listOf(serverB), controller.connectCalls)
    }

    @Test
    fun `onIntent Disconnect EXPECT controller disconnect called`() = runTest {
        // Given
        val controller = FakeConnectionController()
        val viewModel = HomeViewModel(controller, FakeServerRepository(), FakeNotificationPermissionState())

        // When
        viewModel.onIntent(HomeIntent.Disconnect)
        advanceUntilIdle()

        // Then
        assertEquals(1, controller.disconnectCount)
    }

    @Test
    fun `onIntent Cancel EXPECT controller disconnect called`() = runTest {
        // Given
        val controller = FakeConnectionController()
        val viewModel = HomeViewModel(controller, FakeServerRepository(), FakeNotificationPermissionState())

        // When
        viewModel.onIntent(HomeIntent.Cancel)
        advanceUntilIdle()

        // Then
        assertEquals(1, controller.disconnectCount)
    }

    @Test
    fun `onIntent SelectServer EXPECT servers selectServer called with provided id`() = runTest {
        // Given
        val servers = FakeServerRepository()
        val viewModel = HomeViewModel(FakeConnectionController(), servers, FakeNotificationPermissionState())
        val targetId = ServerId("target-42")

        // When
        viewModel.onIntent(HomeIntent.SelectServer(targetId))
        advanceUntilIdle()

        // Then
        assertEquals(listOf(targetId), servers.selectServerCalls)
    }

    // =======================================================================
    // Effects — notification permission (T-2)
    // =======================================================================

    @Test
    fun `entering Connected when gate not granted EXPECT RequestNotificationPermission emitted exactly once`() =
        runTest {
            // Given — gate starts not granted
            val controller = FakeConnectionController(initialState = VpnConnectionState.Disconnected)
            val notificationPermission = FakeNotificationPermissionState(initialGranted = false)
            val viewModel = HomeViewModel(controller, FakeServerRepository(), notificationPermission)

            viewModel.effects.test {
                // When — transition INTO Connected
                controller.emit(VpnConnectionState.Connected(server = serverA, since = instant, traffic = null))
                advanceUntilIdle()

                // Then — exactly one effect, nothing more
                assertEquals(HomeEffect.RequestNotificationPermission, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `entering Connected when gate IS granted EXPECT no notification permission effect`() = runTest {
        // Given — gate starts granted (default)
        val controller = FakeConnectionController(initialState = VpnConnectionState.Disconnected)
        val notificationPermission = FakeNotificationPermissionState(initialGranted = true)
        val viewModel = HomeViewModel(controller, FakeServerRepository(), notificationPermission)

        viewModel.effects.test {
            // When
            controller.emit(VpnConnectionState.Connected(server = serverA, since = instant, traffic = null))
            advanceUntilIdle()

            // Then — no notification effect
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Connected self-transition via traffic update EXPECT RequestNotificationPermission emitted only once`() =
        runTest {
            // Given — gate not granted; distinctUntilChangedBy must collapse Connected→Connected
            val controller = FakeConnectionController(initialState = VpnConnectionState.Disconnected)
            val notificationPermission = FakeNotificationPermissionState(initialGranted = false)
            val viewModel = HomeViewModel(controller, FakeServerRepository(), notificationPermission)

            viewModel.effects.test {
                // When — initial Connected entry
                controller.emit(VpnConnectionState.Connected(server = serverA, since = instant, traffic = null))
                advanceUntilIdle()
                assertEquals(HomeEffect.RequestNotificationPermission, awaitItem())

                // When — Connected→Connected self-transition (traffic field churn)
                controller.emit(VpnConnectionState.Connected(server = serverA, since = instant, traffic = traffic))
                advanceUntilIdle()

                // Then — no second emission
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reconnect Connected-Disconnected-Connected EXPECT RequestNotificationPermission NOT emitted again`() =
        runTest {
            // Given — requestedThisProcess guard prevents re-emission across reconnects
            val controller = FakeConnectionController(initialState = VpnConnectionState.Disconnected)
            val notificationPermission = FakeNotificationPermissionState(initialGranted = false)
            val viewModel = HomeViewModel(controller, FakeServerRepository(), notificationPermission)

            viewModel.effects.test {
                // When — first Connected entry → effect fires once
                controller.emit(VpnConnectionState.Connected(server = serverA, since = instant, traffic = null))
                advanceUntilIdle()
                assertEquals(HomeEffect.RequestNotificationPermission, awaitItem())

                // When — disconnect then reconnect
                controller.emit(VpnConnectionState.Disconnected)
                advanceUntilIdle()
                controller.emit(VpnConnectionState.Connected(server = serverA, since = instant, traffic = null))
                advanceUntilIdle()

                // Then — no second emission for the rest of the process lifetime
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `notificationsGranted reflects the gate value published by FakeNotificationPermissionState`() = runTest {
        // Given — gate starts granted
        val notificationPermission = FakeNotificationPermissionState(initialGranted = true)
        val viewModel = HomeViewModel(
            FakeConnectionController(),
            FakeServerRepository(),
            notificationPermission,
        )

        viewModel.notificationsGranted.test {
            // Initial value
            assertEquals(true, awaitItem())

            // When — gate changes to not granted via refresh
            notificationPermission.setGranted(false)
            notificationPermission.refresh()
            advanceUntilIdle()

            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `notificationChannelId equals vpnis_tunnel`() {
        // Given
        val notificationPermission = FakeNotificationPermissionState()
        val viewModel = HomeViewModel(FakeConnectionController(), FakeServerRepository(), notificationPermission)

        // Then
        assertEquals("vpnis_tunnel", viewModel.notificationChannelId)
    }
}
