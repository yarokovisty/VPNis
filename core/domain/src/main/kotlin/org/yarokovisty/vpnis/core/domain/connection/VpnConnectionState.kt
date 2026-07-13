package org.yarokovisty.vpnis.core.domain.connection

import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
import java.time.Instant

/**
 * Source-of-truth for the VPN tunnel lifecycle, observed by the presentation layer.
 *
 * ## Legal transition table
 *
 * ```
 * Loading           -> Disconnected, PermissionRequired, Connected, Error
 * Disconnected      -> Connecting, PermissionRequired
 * PermissionRequired -> Connecting, Disconnected
 * Connecting        -> Connected, Error, Disconnected
 * Connected         -> Disconnected, Error
 * Error             -> Connecting, Disconnected, PermissionRequired
 * ```
 *
 * ### Self-transitions
 * - **Connected -> Connected**: allowed — [Connected.traffic] and/or [Connected.since]
 *   may update while the tunnel stays active (traffic-counter refreshes, etc.).
 * - **Error -> Error**: allowed — a new failure reason may replace the old one
 *   (e.g. a retry attempt fails with a different [ConnectionError]).
 * - All other self-transitions (Loading -> Loading, Disconnected -> Disconnected,
 *   PermissionRequired -> PermissionRequired, Connecting -> Connecting):
 *   **disallowed** — they carry no meaningful payload change.
 *
 * Use [isLegalTransition] to validate state changes in the connection controller
 * and in fakes/stubs that simulate the controller.
 *
 * ### Design notes
 * - [PermissionRequired] is a **state**, not an event. It survives process death
 *   and is re-emitted on cold start when the OS permission is still missing.
 *   It carries no Android types (`Intent`, `Context`) — the presentation layer
 *   triggers `VpnService.prepare()` itself when it sees this state.
 * - [Connected.traffic] is **nullable** so that a traffic-less stub and a real
 *   traffic counter can share this contract without a signature change. A `null`
 *   value means counters are not yet available; use [TrafficStats.EMPTY] only
 *   when zero is a meaningful reading rather than "unknown".
 */
public sealed interface VpnConnectionState {

    /** Cold-start / unknown — emitted while the controller reads persisted state. */
    public data object Loading : VpnConnectionState

    /** The tunnel is idle; no active VPN connection exists. */
    public data object Disconnected : VpnConnectionState

    /**
     * The app must obtain OS VPN consent (`VpnService.prepare()`) before connecting.
     * Persisted across process death; the presentation layer reacts to this state
     * by triggering the system permission dialog.
     */
    public data object PermissionRequired : VpnConnectionState

    /** A connection attempt is in progress for [server]. */
    public data class Connecting(val server: Server) : VpnConnectionState

    /**
     * The tunnel is active.
     *
     * @param server the server this tunnel is connected to.
     * @param since  the moment the tunnel became active.
     * @param traffic live traffic counters, or `null` when not yet available.
     */
    public data class Connected(val server: Server, val since: Instant, val traffic: TrafficStats?) :
        VpnConnectionState

    /** The last operation or the active tunnel failed with [reason]. */
    public data class Error(val reason: ConnectionError) : VpnConnectionState
}
