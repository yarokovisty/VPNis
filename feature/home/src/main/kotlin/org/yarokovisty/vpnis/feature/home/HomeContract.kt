package org.yarokovisty.vpnis.feature.home

import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
import java.time.Instant

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------

/**
 * The complete UI state for the Home screen.
 *
 * Raw domain values are exposed without formatting; the UI layer (#51) owns
 * all string/duration/speed formatting so the ViewModel stays testable on the JVM.
 */
public sealed interface HomeUiState {

    /** Cold-start — the domain state has not yet been determined. */
    public data object Loading : HomeUiState

    /**
     * The tunnel is idle (or the OS permission dialog needs to appear).
     *
     * - [server] == null  → no server configured; prompt the user to add one.
     * - [server] != null  → server ready; show Connect button.
     *
     * The permission request is delivered as a one-shot [HomeEffect.RequestVpnPermission]
     * and does NOT produce a distinct UI state — the resting screen is always this variant.
     */
    public data class Disconnected(val server: Server?) : HomeUiState

    /** A connection attempt is in progress for [server]. */
    public data class Connecting(val server: Server) : HomeUiState

    /**
     * The VPN tunnel is active.
     *
     * @param server  the server the tunnel is connected to.
     * @param since   the moment the tunnel became active — format in the UI layer.
     * @param traffic live traffic counters, or null when not yet available.
     */
    public data class Connected(val server: Server, val since: Instant, val traffic: TrafficStats?) : HomeUiState

    /**
     * The last operation failed.
     *
     * @param reason the typed failure cause.
     * @param server the currently selected server at the time of the error, or null if none.
     */
    public data class Error(val reason: ConnectionError, val server: Server?) : HomeUiState
}

// ---------------------------------------------------------------------------
// Intent
// ---------------------------------------------------------------------------

/** User-initiated actions dispatched to [HomeViewModel.onIntent]. */
public sealed interface HomeIntent {

    /** User tapped Connect. No-op when no server is selected. */
    public data object Connect : HomeIntent

    /** User tapped Disconnect while the tunnel is active. */
    public data object Disconnect : HomeIntent

    /** User tapped Cancel during an in-progress connection attempt. */
    public data object Cancel : HomeIntent

    /** User tapped Retry after an error. No-op when no server is selected. */
    public data object Retry : HomeIntent

    /** User tapped Add Server — navigates to the server list. */
    public data object AddServer : HomeIntent

    /** User selected a server from the list by [id]. */
    public data class SelectServer(val id: ServerId) : HomeIntent
}

// ---------------------------------------------------------------------------
// Effect
// ---------------------------------------------------------------------------

/**
 * One-shot side effects emitted by [HomeViewModel].
 *
 * Effects are delivered exactly once and are not replayed on re-subscription.
 * The UI layer must not store effects in its own state.
 */
public sealed interface HomeEffect {

    /**
     * The OS VPN permission dialog must be launched.
     *
     * Fired once each time the domain state transitions INTO [VpnConnectionState.PermissionRequired].
     * The UI layer calls `VpnService.prepare()` and routes the result back via [HomeIntent.Connect].
     */
    public data object RequestVpnPermission : HomeEffect

    /** Navigate to the server-selection screen. */
    public data object NavigateToServers : HomeEffect
}
