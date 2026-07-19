package org.yarokovisty.vpnis.data.vpn

import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import java.time.Instant

/**
 * Robolectric unit tests for [TunnelNotificationPresenter] (issue #127, task T-6).
 *
 * Uses [StandardTestDispatcher] as the [mapDispatcher] so time is fully under test control.
 * Notification posts are counted via [android.app.ShadowNotificationManager.getAllNotifications].
 *
 * Four behaviours are covered, one per test:
 * (a) Active-state filter — Disconnected (→ Inactive) is never posted.
 * (b) distinctUntilChanged — identical Connected values do not produce duplicate posts.
 * (c) No post after stop — after [TunnelNotificationPresenter.stop] no further emission is posted.
 * (d) start → kill → start — the defensive job?.cancel() prevents double-collection by the
 *     stale collector from scopeA.
 *
 * Not unit-testable here (guarded by design + device QA #133): the `active` [java.util.concurrent.atomic.AtomicBoolean]
 * gate defends a *multi-threaded sub-frame* race — an emission already executing `onEach` on the
 * collector thread when `stop()` flips `active` on another thread. A single-threaded test dispatcher
 * cannot isolate the gate from `job.cancel()` (a cancelled coroutine's `onEach` never runs), so (c)
 * verifies the observable contract (no post after stop) rather than the gate in isolation. The
 * guaranteed closer for the race is `VpnTunnelService.finishTeardown`'s final
 * `cancel(NOTIFICATION_ID)` sweep (issue #127), exercised on-device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TunnelNotificationPresenterTest {

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    private val server = Server(id = ServerId("s"), name = "Srv", config = "cfg")
    private val since = Instant.ofEpochSecond(1_700_000_000L)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        notificationManager = context.getSystemService(NotificationManager::class.java)

        // Create the channel so build() can reference it without a crash.
        TunnelNotifications.createChannel(context)
    }

    private fun postedCount(): Int = Shadows.shadowOf(notificationManager).allNotifications.size

    // -------------------------------------------------------------------------
    // (a) Active-state filter: Disconnected → Inactive → no post
    // -------------------------------------------------------------------------

    @Test
    fun `Connected then Disconnected EXPECT only one notification posted`() = runTest {
        // Given
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val controller = FakeConnectionControllerStub(stateFlow)
        val presenter = TunnelNotificationPresenter(controller, context, dispatcher)
        val scope = TestScope(dispatcher)

        presenter.start(scope)

        // When — emit Connected then Disconnected
        stateFlow.value = VpnConnectionState.Connected(server, since, traffic = null)
        advanceUntilIdle()
        val afterConnected = postedCount()

        stateFlow.value = VpnConnectionState.Disconnected
        advanceUntilIdle()

        // Then — count stays at 1; Disconnected maps to Inactive and is filtered
        assertEquals(1, afterConnected)
        assertEquals(1, postedCount())
    }

    // -------------------------------------------------------------------------
    // (b) distinctUntilChanged: same Connected value twice → only one post
    // -------------------------------------------------------------------------

    @Test
    fun `consecutive identical content EXPECT only one notification posted`() = runTest {
        // Given — a source flow that emits two CONSECUTIVE identical Connected states. A real
        // StateFlow dedups equal values at source, so we use flowOf to force the duplicate through
        // the pipeline and prove distinctUntilChanged (not the source) collapses it.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connectedState = VpnConnectionState.Connected(server, since, traffic = null)
        val controller = FakeConnectionControllerStub(flowOf(connectedState, connectedState))
        val presenter = TunnelNotificationPresenter(controller, context, dispatcher)
        val scope = TestScope(dispatcher)

        // When
        presenter.start(scope)
        advanceUntilIdle()

        // Then — distinctUntilChanged collapses the identical second emission → one post only
        assertEquals(1, postedCount())
    }

    // -------------------------------------------------------------------------
    // (c) No post after stop — a state emitted after stop() is never posted
    //     (observable contract; see class KDoc on the active gate's untestable race)
    // -------------------------------------------------------------------------

    @Test
    fun `after stop no notification posted for subsequent emission`() = runTest {
        // Given
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val controller = FakeConnectionControllerStub(stateFlow)
        val presenter = TunnelNotificationPresenter(controller, context, dispatcher)
        val scope = TestScope(dispatcher)

        presenter.start(scope)
        val beforeStop = postedCount()

        // When — stop then emit a Connected state
        presenter.stop()
        stateFlow.value = VpnConnectionState.Connected(server, since, traffic = null)
        advanceUntilIdle()

        // Then — no new notification is posted once the presenter is stopped
        assertEquals(0, beforeStop)
        assertEquals(0, postedCount())
    }

    // -------------------------------------------------------------------------
    // (d) start → kill → start: stale collector from scopeA does not double-post
    // -------------------------------------------------------------------------

    @Test
    fun `start scopeA then start scopeB without stop EXPECT exactly one post per emission`() = runTest {
        // Given
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val controller = FakeConnectionControllerStub(stateFlow)
        val presenter = TunnelNotificationPresenter(controller, context, dispatcher)

        val scopeA = TestScope(dispatcher)
        val scopeB = TestScope(dispatcher)

        // When — start in scopeA, then start in scopeB (simulates dirty restart)
        presenter.start(scopeA)
        presenter.start(scopeB) // defensive job?.cancel() cancels scopeA's collector

        stateFlow.value = VpnConnectionState.Connected(server, since, traffic = null)
        advanceUntilIdle()

        // Then — exactly ONE notification posted (not two); scopeA's collector was cancelled
        assertEquals(1, postedCount())
    }
}

// -------------------------------------------------------------------------
// Stub collaborator — wraps a MutableStateFlow as a ConnectionController
// -------------------------------------------------------------------------

private class FakeConnectionControllerStub(override val state: Flow<VpnConnectionState>) : ConnectionController {
    override suspend fun connect(server: Server) = Unit
    override suspend fun disconnect() = Unit
}
