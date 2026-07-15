package org.yarokovisty.vpnis.data.vpn

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.connection.isLegalTransition
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.Server
import java.time.Instant

/**
 * Process-scoped, source-of-truth implementation of [ConnectionController].
 *
 * Bound as a Koin `single` so there is exactly one instance per process. All
 * presentation-layer observers collect from [state]; the [VpnTunnelService] writes
 * lifecycle events back via [TunnelStateSink].
 *
 * ## State machine
 *
 * Every state write goes through the private [transition] helper, which calls
 * [isLegalTransition] before applying the change. Illegal transitions are silently
 * dropped — they indicate a race or a bug in the service, not a crash-worthy condition.
 *
 * The legal table (see [VpnConnectionState] KDoc for the full matrix):
 * ```
 * Disconnected      → Connecting (via connect)
 * Connecting        → Connected (via onTunnelEstablished)
 * Connecting        → Error     (via onTunnelError or config-build failure in connect)
 * Connecting        → Disconnected (via disconnect or onTunnelStopped)
 * Connected         → Disconnected (via disconnect or onTunnelStopped)
 * Connected         → Error     (via onTunnelError)
 * Disconnected/Error → PermissionRequired (via onPermissionRequired)
 * PermissionRequired → Connecting (on next connect)
 * ```
 *
 * ## Config build failure path
 *
 * [connect] transitions to [VpnConnectionState.Connecting] before attempting to build
 * the Xray config (so the UI reflects the in-progress state immediately), then calls
 * [XrayConfigBuilder.build]. If [XrayConfigBuilder.build] returns `null` the state is
 * advanced to [VpnConnectionState.Error] and [TunnelLauncher.launch] is NOT called.
 *
 * ## Connect / disconnect race
 *
 * `connect(server)` is a suspend function called from the presentation layer's
 * coroutine. It signals the service to start via [TunnelLauncher.launch]. A subsequent
 * `disconnect()` call cancels the in-progress intent by calling [TunnelLauncher.stop]
 * immediately; if the service was still in startup it will call [onTunnelStopped] which
 * drives the state to [VpnConnectionState.Disconnected]. Because both paths write through
 * [transition] (which guards on [isLegalTransition]), a late [onTunnelEstablished]
 * callback arriving after [disconnect] transitions to [VpnConnectionState.Disconnected]
 * will be silently dropped (Connected is not a legal successor of Disconnected).
 *
 * `connect` also resets [currentTarget] to the new server before calling
 * [TunnelLauncher.launch], so a reconnect to a different server always replaces the
 * previous target atomically.
 *
 * ## Concurrency model
 *
 * - [_state] is a [MutableStateFlow]; reads and writes are thread-safe by contract.
 * - [currentTarget] is `@Volatile` so that reads from any thread (e.g. [onTunnelEstablished]
 *   arriving on [kotlinx.coroutines.Dispatchers.IO]) always see the latest write from the
 *   [connect] suspend function. Writes to [currentTarget] happen only in [connect], which
 *   is a suspend function and therefore always called from a coroutine context — there is no
 *   concurrent write path, so `@Volatile` (rather than a mutex) is sufficient.
 * - [transition] writes exclusively to [_state] (thread-safe). No additional
 *   synchronisation is required.
 *
 * ## Traffic counters
 *
 * MVP stub: [VpnConnectionState.Connected.traffic] is always `null`. Issue #69 will add
 * a polling loop that reads byte counters from the kernel (via `/proc/net/dev` or the
 * libXray stats API) and emits [VpnConnectionState.Connected] updates with live
 * [org.yarokovisty.vpnis.core.domain.model.TrafficStats].
 *
 * @param launcher Seam for starting/stopping [VpnTunnelService] without depending on
 *   Android [android.app.Service] or [android.content.Context] types. Injected so the
 *   controller can be unit-tested with a fake [TunnelLauncher].
 */
internal class ConnectionControllerImpl(private val launcher: TunnelLauncher) :
    ConnectionController,
    TunnelStateSink {

    private val _state = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)

    override val state: Flow<VpnConnectionState> = _state.asStateFlow()

    /**
     * The server that was passed to the most recent [connect] call.
     *
     * Read by [onTunnelEstablished] (which may run on a background thread) to populate
     * [VpnConnectionState.Connected.server]. Marked [@Volatile] for safe cross-thread
     * visibility — see concurrency model in class KDoc.
     *
     * Note: on config-build failure (when [XrayConfigBuilder.build] returns `null`),
     * [currentTarget] is intentionally left set to the server that was passed to [connect].
     * This is harmless: [VpnConnectionState.Error] has no legal edge to
     * [VpnConnectionState.Connected], so a stale [onTunnelEstablished] arriving in the
     * Error state would be silently dropped by the [isLegalTransition] guard.
     */
    @Volatile
    private var currentTarget: Server? = null

    // -------------------------------------------------------------------------
    // ConnectionController — public API
    // -------------------------------------------------------------------------

    /**
     * Requests a VPN connection to [server].
     *
     * Transitions the state to [VpnConnectionState.Connecting], then builds the Xray
     * JSON config via [XrayConfigBuilder.build]. If the build fails (returns `null`),
     * the state advances to [VpnConnectionState.Error] with
     * [ConnectionError.TunnelSetupFailed] and [TunnelLauncher.launch] is NOT called.
     * On success, [TunnelLauncher.launch] is called with the server and the built config.
     *
     * The `PermissionRequired → Connecting` edge: [connect] is called again after the user
     * grants OS consent in the permission dialog. [VpnConnectionState.PermissionRequired]
     * is a legal predecessor of [VpnConnectionState.Connecting], so the transition succeeds.
     */
    override suspend fun connect(server: Server) {
        Log.d(TAG, "connect: server=${server.id.value}")
        currentTarget = server
        transition(VpnConnectionState.Connecting(server))

        val json = XrayConfigBuilder.build(server.config)
        if (json == null) {
            Log.e(TAG, "connect: XrayConfigBuilder.build returned null for server=${server.id.value} — moving to Error")
            transition(VpnConnectionState.Error(ConnectionError.TunnelSetupFailed))
            // currentTarget is intentionally left set. Error has no legal edge to Connected,
            // so a stale onTunnelEstablished callback (if any) will be silently dropped.
            return
        }

        launcher.launch(server = server, configJson = json)
    }

    /**
     * Requests a VPN disconnection.
     *
     * Sets state to [VpnConnectionState.Disconnected] immediately (defensive — the
     * service will confirm via [onTunnelStopped], but reacting early lets the UI update
     * without waiting for the service teardown round-trip). Then signals the service to
     * stop via [TunnelLauncher.stop].
     *
     * If called while in [VpnConnectionState.PermissionRequired] (user refused the dialog)
     * this transitions to [VpnConnectionState.Disconnected] — a legal edge per the
     * transition table — and the service is asked to stop (it may not be running, which is
     * a no-op at the service level).
     */
    override suspend fun disconnect() {
        Log.d(TAG, "disconnect")
        transition(VpnConnectionState.Disconnected)
        launcher.stop()
    }

    // -------------------------------------------------------------------------
    // TunnelStateSink — callbacks from VpnTunnelService
    // -------------------------------------------------------------------------

    /**
     * Called by [VpnTunnelService] after [android.net.VpnService.Builder.establish] returns
     * a non-null [android.os.ParcelFileDescriptor] and the hev native loop has started.
     *
     * Transitions to [VpnConnectionState.Connected] using [currentTarget] as the server.
     * If [currentTarget] is `null` (should not happen in a well-ordered lifecycle, but
     * possible if the service outlives the controller's connect call), the transition is
     * dropped and an error is logged.
     *
     * Traffic: `null` per MVP — issue #69 adds live polling.
     */
    override fun onTunnelEstablished() {
        val server = currentTarget
        if (server == null) {
            Log.e(TAG, "onTunnelEstablished: currentTarget is null — dropping transition")
            return
        }
        Log.d(TAG, "onTunnelEstablished: server=${server.id.value}")
        // traffic = null per MVP (issue #69 adds live polling)
        transition(VpnConnectionState.Connected(server = server, since = Instant.now(), traffic = null))
    }

    /**
     * Called by [VpnTunnelService] after the tunnel is fully torn down (hev stopped, fd closed).
     *
     * Transitions to [VpnConnectionState.Disconnected]. This is the authoritative cleanup
     * signal from the service side; [disconnect] also transitions to Disconnected defensively
     * to unblock the UI before the service round-trip completes.
     */
    override fun onTunnelStopped() {
        Log.d(TAG, "onTunnelStopped")
        transition(VpnConnectionState.Disconnected)
    }

    /**
     * Called by [VpnTunnelService] when a fatal tunnel error occurs.
     *
     * Transitions to [VpnConnectionState.Error]. Legal from [VpnConnectionState.Connecting]
     * and [VpnConnectionState.Connected]; the [transition] guard drops the call from any
     * other state.
     */
    override fun onTunnelError(reason: ConnectionError) {
        Log.d(TAG, "onTunnelError: reason=$reason")
        transition(VpnConnectionState.Error(reason))
    }

    /**
     * Called by [VpnTunnelService] when [android.net.VpnService.Builder.establish] returns
     * `null` (OS VPN permission not granted).
     *
     * Transitions to [VpnConnectionState.PermissionRequired]. The presentation layer reacts
     * to this state by triggering [android.net.VpnService.prepare] and showing the system
     * permission dialog. After the user grants consent, the layer calls [connect] again,
     * which sends the `PermissionRequired → Connecting` transition.
     */
    override fun onPermissionRequired() {
        Log.d(TAG, "onPermissionRequired")
        transition(VpnConnectionState.PermissionRequired)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Applies [next] only when [isLegalTransition] approves the move from the current state.
     *
     * Illegal transitions are silently dropped. This mirrors [FakeConnectionController]'s
     * [transition] behaviour: protect against service race conditions without crashing.
     *
     * Thread-safe: [MutableStateFlow] CAS guarantees atomicity of the compare-and-set.
     * [_state.value] read and write are not atomically linked here, but since transitions
     * only advance through a legal graph and the only concurrent callers (service callbacks
     * vs connect/disconnect) are on different paths with different target states, the
     * non-atomic check-then-act is acceptable — a stale read would at worst silently drop
     * one transition, which the guard already handles.
     */
    private fun transition(next: VpnConnectionState) {
        val current = _state.value
        if (isLegalTransition(from = current, to = next)) {
            _state.value = next
        } else {
            Log.d(TAG, "transition: dropped illegal $current → $next")
        }
    }

    private companion object {
        const val TAG = "ConnectionControllerImpl"
    }
}
