package org.yarokovisty.vpnis.data.vpn

import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
import java.time.Instant

/**
 * Robolectric unit tests for [TunnelNotificationPresenter] (issues #127, #129, #130).
 *
 * Uses [StandardTestDispatcher] as the [mapDispatcher] so time is fully under test control.
 * Notification posts are counted via [android.app.ShadowNotificationManager.getAllNotifications].
 *
 * ## Throttle note (issue #130)
 *
 * The pipeline `sample`s the Connected sub-stream, and `Flow.sample` runs a fixed-period ticker that
 * never lets the scheduler idle — so tests that keep a live (non-completing) source advance a
 * **bounded** amount ([settle]) and [TunnelNotificationPresenter.stop] the presenter, rather than
 * calling `advanceUntilIdle` (which would hang on the ticker). Tests over a completing `flowOf`
 * source may still use `advanceUntilIdle` (the ticker stops when the upstream completes). The
 * measurable ≤1/sec throttle is verified by counting emissions on the [TunnelNotificationPresenter.contentPipeline]
 * seam directly, because Robolectric cannot count re-`notify()`s to the same slot.
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

        // Create both channels so build()/buildAlert() can reference them without a crash.
        TunnelNotifications.createChannel(context)
        TunnelNotifications.createAlertChannel(context)
        // The disconnect PendingIntent cache is a process-lifetime object field — reset it so a
        // prior test class's cached instance never leaks into this run.
        TunnelNotifications.resetCachesForTest()
    }

    private fun postedCount(): Int = Shadows.shadowOf(notificationManager).allNotifications.size

    private fun notificationAt(id: Int) = Shadows.shadowOf(notificationManager).getNotification(id)

    private fun textOf(id: Int): String =
        notificationAt(id)?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()

    /** Advance past one sample window (issue #130) so any Connected sub-stream emission fires. */
    private fun TestScope.settle() {
        testScheduler.advanceTimeBy(THROTTLE_SETTLE_MS)
        testScheduler.runCurrent()
    }

    private fun connected(traffic: TrafficStats? = null) = VpnConnectionState.Connected(server, since, traffic)

    // -------------------------------------------------------------------------
    // (a) Active-state filter: Disconnected → Inactive → no post
    // -------------------------------------------------------------------------

    @Test
    fun `Connected then Disconnected EXPECT only one notification posted`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val presenter = TunnelNotificationPresenter(FakeConnectionControllerStub(stateFlow), context, dispatcher)
        presenter.start(TestScope(dispatcher))

        // When — emit Connected then Disconnected
        stateFlow.value = connected()
        settle()
        val afterConnected = postedCount()

        stateFlow.value = VpnConnectionState.Disconnected
        settle()

        // Then — count stays at 1; Disconnected maps to Inactive and is filtered
        assertEquals(1, afterConnected)
        assertEquals(1, postedCount())
        presenter.stop()
    }

    // -------------------------------------------------------------------------
    // (b) distinctUntilChanged: same content twice → only one post
    // -------------------------------------------------------------------------

    @Test
    fun `consecutive identical content EXPECT only one notification posted`() = runTest {
        // A completing flowOf forces two CONSECUTIVE identical Connecting states through the pipeline
        // to prove distinctUntilChanged (not the source) collapses them. Connecting bypasses the
        // Connected sampler, so the completing source lets us assert without advancing a window.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connecting = VpnConnectionState.Connecting(server)
        val controller = FakeConnectionControllerStub(flowOf(connecting, connecting))
        val presenter = TunnelNotificationPresenter(controller, context, dispatcher)

        presenter.start(TestScope(dispatcher))
        advanceUntilIdle()

        assertEquals(1, postedCount())
    }

    @Test
    fun `consecutive identical Connected states EXPECT distinctUntilChanged collapses to one emission`() = runTest {
        // Issue #132: covers the Connected→Connected identical-content dedup gap.
        //
        // WHY a hand-rolled cold flow instead of MutableStateFlow:
        // MutableStateFlow conflates equal values — assigning `value = x` twice with the same `x`
        // produces only ONE emission under the hood, so distinctUntilChanged would never even see
        // the duplicate. A hand-rolled flow { emit; emit; awaitCancellation() } forces TWO upstream
        // emissions through the pipeline, letting distinctUntilChanged (not the source) do the
        // collapsing. The sampler then sees only the single deduplicated value and emits it once
        // per window — so the list must contain exactly 1 entry after one THROTTLE_SETTLE_MS window.

        // Given
        val dispatcher = StandardTestDispatcher(testScheduler)
        val traffic = TrafficStats(1L, 1L, 100L, 10L)
        val duplicateSource: Flow<VpnConnectionState> = flow {
            emit(connected(traffic))
            emit(connected(traffic)) // byte-identical — distinctUntilChanged must collapse this
            awaitCancellation()
        }
        val presenter = TunnelNotificationPresenter(FakeConnectionControllerStub(duplicateSource), context, dispatcher)

        val emissions = mutableListOf<NotificationContent.Connected>()
        val job = presenter.contentPipeline()
            .filterIsInstance<NotificationContent.Connected>()
            .onEach { emissions += it }
            .launchIn(backgroundScope)

        // When — advance past exactly one sample window so the sampler fires once
        settle()

        // Then — distinctUntilChanged collapsed both identical emissions to one; the sampler
        // emits that single value once → list size must be exactly 1, not 0 and not 2
        assertEquals(1, emissions.size)
        job.cancel()
    }

    // -------------------------------------------------------------------------
    // (c) No post after stop — a state emitted after stop() is never posted
    // -------------------------------------------------------------------------

    @Test
    fun `after stop no notification posted for subsequent emission`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val presenter = TunnelNotificationPresenter(FakeConnectionControllerStub(stateFlow), context, dispatcher)

        presenter.start(TestScope(dispatcher))
        // When — stop before the collector ever runs, then emit a Connected state
        presenter.stop()
        stateFlow.value = connected()
        advanceUntilIdle() // job cancelled before running ⇒ no collector, no ticker ⇒ no hang

        assertEquals(0, postedCount())
    }

    // -------------------------------------------------------------------------
    // (d) start → kill → start: stale collector from scopeA does not double-post
    // -------------------------------------------------------------------------

    @Test
    fun `start scopeA then start scopeB without stop EXPECT exactly one post per emission`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val presenter = TunnelNotificationPresenter(FakeConnectionControllerStub(stateFlow), context, dispatcher)

        presenter.start(TestScope(dispatcher))
        presenter.start(TestScope(dispatcher)) // defensive job?.cancel() cancels the first collector

        stateFlow.value = connected()
        settle()

        // Exactly ONE notification posted (not two); the first collector was cancelled.
        assertEquals(1, postedCount())
        presenter.stop()
    }

    // -------------------------------------------------------------------------
    // (e) Error → alert on slot 1002 (vpnis_alerts), ongoing slot 1001 untouched (issue #129)
    // -------------------------------------------------------------------------

    @Test
    fun `Error EXPECT alert on the alert slot and nothing on the ongoing slot`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val presenter = TunnelNotificationPresenter(FakeConnectionControllerStub(stateFlow), context, dispatcher)
        presenter.start(TestScope(dispatcher))

        stateFlow.value = VpnConnectionState.Error(ConnectionError.TunnelSetupFailed)
        settle()

        val alert = notificationAt(TunnelNotifications.ALERT_NOTIFICATION_ID)
        assertNotNull(alert)
        assertEquals(TunnelNotifications.ALERT_CHANNEL_ID, alert!!.channelId)
        assertNull(notificationAt(TunnelNotifications.NOTIFICATION_ID))
        presenter.stop()
    }

    @Test
    fun `Connected then Error EXPECT ongoing on 1001 and alert on 1002, distinct slots`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val presenter = TunnelNotificationPresenter(FakeConnectionControllerStub(stateFlow), context, dispatcher)
        presenter.start(TestScope(dispatcher))

        stateFlow.value = connected()
        settle()
        stateFlow.value = VpnConnectionState.Error(ConnectionError.TunnelSetupFailed)
        settle()

        assertEquals(TunnelNotifications.CHANNEL_ID, notificationAt(TunnelNotifications.NOTIFICATION_ID)!!.channelId)
        assertEquals(
            TunnelNotifications.ALERT_CHANNEL_ID,
            notificationAt(TunnelNotifications.ALERT_NOTIFICATION_ID)!!.channelId,
        )
        presenter.stop()
    }

    // -------------------------------------------------------------------------
    // (f) Dedup — at most one alert per reconnect chain
    // -------------------------------------------------------------------------

    @Test
    fun `two consecutive distinct Errors EXPECT only the first alert (gate)`() = runTest {
        // Two DIFFERENT reasons so distinctUntilChanged does not collapse them; only the alertPosted
        // gate can suppress the second. Errors bypass the sampler, so the completing flowOf is fine.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = FakeConnectionControllerStub(
            flowOf(
                VpnConnectionState.Error(ConnectionError.ServerUnreachable),
                VpnConnectionState.Error(ConnectionError.TunnelSetupFailed),
            ),
        )
        val presenter = TunnelNotificationPresenter(controller, context, dispatcher)

        presenter.start(TestScope(dispatcher))
        advanceUntilIdle()

        assertEquals(
            context.getString(R.string.vpn_alert_text_server_unreachable),
            textOf(TunnelNotifications.ALERT_NOTIFICATION_ID),
        )
    }

    @Test
    fun `start Error stop start Error EXPECT the gate resets so the new session re-alerts`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val presenter = TunnelNotificationPresenter(FakeConnectionControllerStub(stateFlow), context, dispatcher)

        // Session 1 — drop with ServerUnreachable
        presenter.start(TestScope(dispatcher))
        stateFlow.value = VpnConnectionState.Error(ConnectionError.ServerUnreachable)
        settle()
        presenter.stop()

        // Session 2 — a fresh start must reset the gate and re-alert on a new drop
        stateFlow.value = VpnConnectionState.Disconnected
        presenter.start(TestScope(dispatcher))
        stateFlow.value = VpnConnectionState.Error(ConnectionError.TunnelSetupFailed)
        settle()

        assertEquals(
            context.getString(R.string.vpn_alert_text_tunnel_failed),
            textOf(TunnelNotifications.ALERT_NOTIFICATION_ID),
        )
        presenter.stop()
    }

    // -------------------------------------------------------------------------
    // (g) Throttle (issue #130) — measured on the contentPipeline() seam
    // -------------------------------------------------------------------------

    @Test
    fun `Connected traffic changing faster than 1 per second EXPECT at most one emission per second`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(connected(TrafficStats(0, 0, 0, 0)))
        val presenter = TunnelNotificationPresenter(FakeConnectionControllerStub(stateFlow), context, dispatcher)

        val emissions = mutableListOf<NotificationContent>()
        val job = presenter.contentPipeline()
            .filterIsInstance<NotificationContent.Connected>()
            .onEach { emissions += it }
            .launchIn(backgroundScope)

        // 60 virtual seconds, a fresh distinct traffic value every 500ms (2/s, faster than the cap).
        repeat(120) { i ->
            stateFlow.value = connected(TrafficStats(i.toLong(), i.toLong(), i * 100L, i * 10L))
            testScheduler.advanceTimeBy(500)
        }
        testScheduler.runCurrent()

        // ≤1 notify()/sec over 60s (epic #126 DoD) AND still updating (not stuck at 0).
        assertTrue("expected <= 60 emissions, got ${emissions.size}", emissions.size <= 60)
        assertTrue("expected >= 25 emissions, got ${emissions.size}", emissions.size >= 25)
        job.cancel()
    }

    @Test
    fun `Connecting is surfaced immediately without waiting for a sample window`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val presenter = TunnelNotificationPresenter(FakeConnectionControllerStub(stateFlow), context, dispatcher)

        val seen = mutableListOf<NotificationContent>()
        val job = presenter.contentPipeline().onEach { seen += it }.launchIn(backgroundScope)

        stateFlow.value = VpnConnectionState.Connecting(server)
        testScheduler.advanceTimeBy(50) // well under the 1s throttle window
        testScheduler.runCurrent()

        assertTrue("Connecting must not be throttled", seen.any { it is NotificationContent.Connecting })
        job.cancel()
    }

    @Test
    fun `Error during a live traffic session EXPECT the alert posts promptly (un-sampled)`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateFlow = MutableStateFlow<VpnConnectionState>(connected(TrafficStats(1, 1, 1, 1)))
        val presenter = TunnelNotificationPresenter(FakeConnectionControllerStub(stateFlow), context, dispatcher)
        presenter.start(TestScope(dispatcher))
        settle() // establish the connected session

        // When — a mid-session drop; advance only 50ms (far under the 1s sample window)
        stateFlow.value = VpnConnectionState.Error(ConnectionError.TunnelSetupFailed)
        testScheduler.advanceTimeBy(50)
        testScheduler.runCurrent()

        // Then — the alert is already up: Error bypasses the sampler, so it is not delayed.
        assertNotNull(notificationAt(TunnelNotifications.ALERT_NOTIFICATION_ID))
        presenter.stop()
    }

    private companion object {
        const val THROTTLE_SETTLE_MS = 1_100L
    }
}

// -------------------------------------------------------------------------
// Stub collaborator — wraps a Flow as a ConnectionController
// -------------------------------------------------------------------------

private class FakeConnectionControllerStub(override val state: Flow<VpnConnectionState>) : ConnectionController {
    override suspend fun connect(server: Server) = Unit
    override suspend fun disconnect() = Unit
}
