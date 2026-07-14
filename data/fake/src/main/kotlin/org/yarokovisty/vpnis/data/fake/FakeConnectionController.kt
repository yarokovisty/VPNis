package org.yarokovisty.vpnis.data.fake

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.connection.isLegalTransition
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
import java.time.Instant

/**
 * Configurable fake implementation of [ConnectionController] that reproduces
 * real-core async behaviour without touching any Android framework or network code.
 *
 * The fake is driven by [FakeScenario], which can be swapped at runtime via the
 * [scenario] property. Each [connect] call cancels any in-flight progression before
 * starting the new one, matching the cancellation contract the real VpnService enforces.
 *
 * A dedicated [CoroutineScope] (backed by [SupervisorJob]) owns all timed transitions
 * so they survive ViewModel recreation without leaking into the caller's scope. Call
 * [close] when the fake is no longer needed (e.g. in test teardown) to cancel that scope.
 *
 * All state transitions satisfy [isLegalTransition].
 *
 * ## VPN consent gate
 *
 * When [requirePermissionOnFirstConnect] is `true` (the default), the first [connect] call
 * of the process emits [VpnConnectionState.PermissionRequired] instead of proceeding to
 * [VpnConnectionState.Connecting]. This mirrors `VpnService.prepare()` returning a non-null
 * `Intent` the first time it is called within a process, and `null` on all subsequent calls.
 *
 * Once consent is granted — modelled by the caller invoking [connect] again while the state
 * is already [VpnConnectionState.PermissionRequired] — the internal `permissionGranted` flag
 * is set to `true` and the connection proceeds normally. Consent persists for the rest of the
 * process lifetime and is NOT reset by [disconnect], matching real `VpnService` behaviour where
 * OS consent survives individual disconnect/reconnect cycles.
 *
 * Consent refusal is modelled by the caller invoking [disconnect] while in
 * [VpnConnectionState.PermissionRequired]. That transitions the state to
 * [VpnConnectionState.Disconnected] (a legal edge), leaving `permissionGranted = false`
 * so the next [connect] will ask again.
 *
 * Because this fake is bound as a process-scoped Koin `single` (see `fakeVpnModule`),
 * the `permissionGranted` flag also demonstrates process-death restore: on a cold start the
 * flag resets to `false` (new process = new instance), faithfully reproducing the OS behaviour
 * where a freshly-launched process must call `VpnService.prepare()` again.
 *
 * @param scenario                      Initial simulation scenario — can be changed via [scenario].
 * @param dispatcher                    Dispatcher for the fake's internal timed-transition coroutines.
 *                                      Inject [kotlinx.coroutines.test.UnconfinedTestDispatcher] or a
 *                                      [kotlinx.coroutines.test.TestCoroutineScheduler]-controlled dispatcher
 *                                      in tests (#58) to make time controllable.
 * @param handshakeDelayMs              Simulated handshake duration for [FakeScenario.HappyPath]
 *                                      and [FakeScenario.ConnectDisconnectRace].
 * @param timeoutDelayMs                Simulated timeout duration for [FakeScenario.HandshakeTimeout].
 * @param quickDelayMs                  Short delay before an immediate failure ([FakeScenario.ServerError]).
 * @param revokeDelayMs                 Time after Connected before the tunnel is revoked
 *                                      ([FakeScenario.SuddenRevoke]).
 * @param trafficTickMs                 Interval between traffic-counter updates while Connected.
 * @param requirePermissionOnFirstConnect When `true`, the first [connect] call emits
 *                                      [VpnConnectionState.PermissionRequired] instead of
 *                                      proceeding immediately to [VpnConnectionState.Connecting],
 *                                      mirroring `VpnService.prepare()` returning a non-null Intent.
 *                                      Set to `false` in tests that do not exercise the consent flow.
 */
public class FakeConnectionController(
    scenario: FakeScenario = FakeScenario.HappyPath,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    public val handshakeDelayMs: Long = 1_500L,
    public val timeoutDelayMs: Long = 5_000L,
    public val quickDelayMs: Long = 300L,
    public val revokeDelayMs: Long = 3_000L,
    public val trafficTickMs: Long = 1_000L,
    public val requirePermissionOnFirstConnect: Boolean = true,
) : ConnectionController {

    /** Switch the simulation scenario; takes effect on the next [connect] call. */
    public var scenario: FakeScenario = scenario

    private val _state = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)

    override val state: Flow<VpnConnectionState> = _state

    // Internal scope for timed transitions. SupervisorJob so a single failing coroutine
    // does not cancel sibling jobs.
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    // Tracks the current in-flight connect/progression job so it can be cancelled
    // atomically when disconnect() is called or a new connect() supersedes it.
    private var progressionJob: Job? = null

    // Cumulative traffic accumulators, reset on each new connection.
    private var rxBytes = 0L
    private var txBytes = 0L

    // Tracks whether OS VPN consent has been granted for this process lifetime.
    // Mirrors VpnService.prepare() returning null after the first successful prepare().
    // NOT reset on disconnect — real OS consent survives individual tunnel cycles.
    private var permissionGranted = false

    override suspend fun connect(server: Server) {
        // Cancel any existing handshake/traffic progression — this is the race guard.
        cancelProgression()

        // VPN consent gate: mirrors VpnService.prepare() returning a non-null Intent on the
        // first connect of a process. If the current state is already PermissionRequired, this
        // call represents the post-grant retry — grant consent and fall through to Connecting.
        // Otherwise, emit PermissionRequired and return without launching a progression job.
        if (requirePermissionOnFirstConnect && !permissionGranted) {
            if (_state.value is VpnConnectionState.PermissionRequired) {
                permissionGranted = true
            } else {
                transition(VpnConnectionState.PermissionRequired)
                return
            }
        }

        transition(VpnConnectionState.Connecting(server))

        progressionJob = scope.launch {
            when (scenario) {
                FakeScenario.HappyPath -> runHappyPath(server)
                FakeScenario.HandshakeTimeout -> runHandshakeTimeout()
                FakeScenario.ServerError -> runServerError()
                FakeScenario.SuddenRevoke -> runSuddenRevoke(server)
                FakeScenario.ConnectDisconnectRace -> runHappyPath(server)
            }
        }
    }

    override suspend fun disconnect() {
        // Cancel in-flight progression first — this is what prevents a late Connected
        // emission from leaking after disconnect() races with an in-progress handshake.
        cancelProgression()
        transition(VpnConnectionState.Disconnected)
    }

    /**
     * Cancels any in-flight timed-progression coroutine and waits for it to finish.
     *
     * This is the core of the ConnectDisconnectRace fix: if disconnect() arrives while
     * the handshake delay is still running, the delay is interrupted, the Connecting state
     * never advances to Connected, and the caller of disconnect() then sets Disconnected —
     * leaving no window for a stale Connected to leak through.
     */
    private fun cancelProgression() {
        progressionJob?.cancel()
        progressionJob = null
        rxBytes = 0L
        txBytes = 0L
    }

    // -------------------------------------------------------------------------
    // Scenario implementations
    // -------------------------------------------------------------------------

    private suspend fun runHappyPath(server: Server) {
        delay(handshakeDelayMs)
        if (!isCoroutineActive()) return // disconnected while we were waiting

        val since = Instant.now()
        transition(VpnConnectionState.Connected(server, since, TrafficStats.EMPTY))
        tickTraffic(server, since)
    }

    private suspend fun runHandshakeTimeout() {
        delay(timeoutDelayMs)
        if (!isCoroutineActive()) return
        transition(VpnConnectionState.Error(ConnectionError.ServerUnreachable))
    }

    private suspend fun runServerError() {
        delay(quickDelayMs)
        if (!isCoroutineActive()) return
        transition(VpnConnectionState.Error(ConnectionError.TunnelSetupFailed))
    }

    private suspend fun runSuddenRevoke(server: Server) {
        delay(handshakeDelayMs)
        if (!isCoroutineActive()) return

        val since = Instant.now()
        transition(VpnConnectionState.Connected(server, since, TrafficStats.EMPTY))

        // Start traffic ticking, then revoke after the revoke delay.
        val trafficJob = scope.launch { tickTraffic(server, since) }
        delay(revokeDelayMs)
        trafficJob.cancel()

        if (!isCoroutineActive()) return
        transition(VpnConnectionState.Error(ConnectionError.Revoked))
    }

    // -------------------------------------------------------------------------
    // Traffic ticker
    // -------------------------------------------------------------------------

    /**
     * Emits Connected state updates at [trafficTickMs] intervals with increasing
     * byte counters and non-zero throughput rates, simulating a live tunnel.
     *
     * Runs inside [progressionJob]'s scope so it is automatically cancelled when
     * [cancelProgression] is called (disconnect/reconnect).
     */
    private suspend fun tickTraffic(server: Server, since: Instant) {
        while (isCoroutineActive()) {
            delay(trafficTickMs)
            if (!isCoroutineActive()) break

            // Simulate ~1 Mbps download / ~200 Kbps upload.
            val rxDelta = 125_000L // 1 Mbps in bytes/s
            val txDelta = 25_000L // 200 Kbps in bytes/s
            rxBytes += rxDelta
            txBytes += txDelta

            transition(
                VpnConnectionState.Connected(
                    server = server,
                    since = since,
                    traffic = TrafficStats(
                        rxBytes = rxBytes,
                        txBytes = txBytes,
                        rxBps = rxDelta,
                        txBps = txDelta,
                    ),
                ),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Emits [next] only if it is a legal transition from the current state.
     * Illegal transitions are silently dropped — this protects against bugs in
     * the scenario logic rather than crashing the app.
     */
    private fun transition(next: VpnConnectionState) {
        val current = _state.value
        if (isLegalTransition(current, next)) {
            _state.value = next
        }
    }

    /**
     * Returns whether the current coroutine context is still active.
     *
     * Reads [currentCoroutineContext] rather than the outer [scope] so that
     * cancellation of [progressionJob] is correctly detected inside suspend fns.
     */
    private suspend fun isCoroutineActive(): Boolean = currentCoroutineContext().isActive

    /**
     * Cancels the fake's internal scope. Call this in test teardown or when the fake
     * is no longer needed to avoid leaking the background coroutine dispatcher.
     */
    public fun close() {
        scope.cancel()
    }
}
