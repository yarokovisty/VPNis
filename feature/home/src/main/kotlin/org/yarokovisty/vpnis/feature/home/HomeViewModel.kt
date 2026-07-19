package org.yarokovisty.vpnis.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.permission.NotificationPermissionState
import org.yarokovisty.vpnis.core.domain.repository.ServerRepository

/**
 * ViewModel for the Home screen.
 *
 * Responsibilities:
 * - Derives [HomeUiState] from [ConnectionController.state] and [ServerRepository.observeSelectedServer].
 * - Emits [HomeEffect.RequestVpnPermission] exactly once per transition INTO
 *   [VpnConnectionState.PermissionRequired].
 * - Handles [HomeIntent]s dispatched from the UI via [onIntent].
 *
 * Constructor parameters are injected by Koin (wired in issue #53 / #114).
 *
 * ## Testability
 * The constructor accepts interfaces only — no Android framework types beyond ViewModel itself.
 * Tests supply fakes implementing [ConnectionController], [ServerRepository], and
 * [NotificationPermissionState], set `Dispatchers.Main` via `kotlinx-coroutines-test`'s
 * `Dispatchers.setMain`, and drive state/effects through `TestScope` / `UnconfinedTestDispatcher`.
 */
public class HomeViewModel(
    private val controller: ConnectionController,
    private val servers: ServerRepository,
    private val notificationPermissionState: NotificationPermissionState,
) : ViewModel() {

    // -----------------------------------------------------------------------
    // UI State
    // -----------------------------------------------------------------------

    /**
     * The authoritative UI state for the Home screen.
     *
     * Seeded with [HomeUiState.Loading] until both upstream flows have emitted at
     * least one value. [SharingStarted.WhileSubscribed] with a 5-second grace window
     * keeps the upstream flows alive through configuration changes (activity recreation
     * re-subscribes within the window) but cancels them after the screen is gone.
     */
    public val uiState: StateFlow<HomeUiState> =
        combine(controller.state, servers.observeSelectedServer()) { connState, selected ->
            mapToUiState(connState, selected)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MS),
            initialValue = HomeUiState.Loading,
        )

    // -----------------------------------------------------------------------
    // Effects
    // -----------------------------------------------------------------------

    /**
     * One-shot effect channel.
     *
     * [Channel.BUFFERED] (64-slot) prevents effect loss during rapid state transitions
     * before the UI has collected the previous effect.  The channel is never replayed.
     */
    private val _effects: Channel<HomeEffect> = Channel(Channel.BUFFERED)

    /**
     * One-shot UI effects. Collect in the UI layer only while the screen is resumed
     * (e.g. with `repeatOnLifecycle(RESUMED)`) to avoid processing stale effects on
     * back-stack restoration.
     */
    public val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    // -----------------------------------------------------------------------
    // Notification permission gate — exposed as siblings to uiState
    // -----------------------------------------------------------------------

    /**
     * Emits `true` when the OS will display the app's status notifications, `false` otherwise.
     *
     * Backed by the same [NotificationPermissionState.isGranted] gate that the [Connected]-
     * transition block reads atomically via [NotificationPermissionState.refresh]. There is
     * exactly one source of truth for "granted" (perf#4 in the plan).
     *
     * The route drives banner visibility off this flow — the two-part gate (app-level AND
     * channel importance) means a mid-session app-level revocation **or** a channel silenced to
     * `IMPORTANCE_NONE` correctly re-surfaces the denial banner without any VM change.
     *
     * The `initialValue = true` is the optimistic default: on a cold start the gate has not yet
     * been evaluated, so we assume granted to avoid a flash of the denial banner before the first
     * `ON_RESUME` refresh.
     */
    public val notificationsGranted: StateFlow<Boolean> =
        notificationPermissionState.isGranted.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MS),
            initialValue = true,
        )

    /**
     * Opaque identifier for the notification channel associated with the permission gate.
     *
     * Passed through to the route so it can build a channel-level deep-link Intent
     * (`ACTION_CHANNEL_NOTIFICATION_SETTINGS`) without importing `:data:vpn` directly
     * (red-team 10a). This property is not a permission-logic signal — do not use it to
     * infer channel state.
     */
    public val notificationChannelId: String = notificationPermissionState.channelId

    // -----------------------------------------------------------------------
    // Permission-effect derivation
    // -----------------------------------------------------------------------

    init {
        // Derive RequestVpnPermission from domain state, NOT from the connect() call.
        // Using distinctUntilChangedBy on the `isPermissionRequired` boolean ensures the
        // effect fires exactly once per *entry* into PermissionRequired, not once per
        // emission (which could repeat if traffic/server fields change while in that state).
        // The ConnectionController contract disallows self-transitions for PermissionRequired
        // (see VpnConnectionState KDoc), so distinctUntilChanged is a safety net, not the
        // primary guard.
        controller.state
            .map { it is VpnConnectionState.PermissionRequired }
            .distinctUntilChangedBy { it }
            .onEach { isPermissionRequired ->
                if (isPermissionRequired) {
                    _effects.send(HomeEffect.RequestVpnPermission)
                }
            }
            .launchIn(viewModelScope)
    }

    init {
        // Derive RequestNotificationPermission from the transition INTO Connected.
        //
        // `distinctUntilChangedBy { isConnected }` collapses Connected→Connected self-transitions
        // caused by traffic counter or chronometer field churn, so this block runs exactly once
        // per *entry* into Connected — never on every tick (perf#2).
        //
        // `requestedThisProcess` is a plain var confined to this single collector (no concurrent
        // access), so no synchronization is needed. Once set to true it prevents the effect from
        // being emitted again for the rest of the process lifetime. A denied-then-reconnect
        // sequence therefore never re-spams the effect channel; the denial banner (driven by
        // `notificationsGranted`) is the only re-surfacing mechanism after the first ask (ux#7).
        //
        // The gate is read via `notificationPermissionState.refresh(): Boolean` rather than
        // `notificationsGranted.value` or `isGranted.first()`. `refresh()` computes the gate
        // value, stores it into the backing StateFlow, and returns the same computed value
        // atomically — no separate collector, no race with an `ON_RESUME` refresh (perf#1).
        var requestedThisProcess = false
        controller.state
            .map { it is VpnConnectionState.Connected }
            .distinctUntilChangedBy { it }
            .onEach { isConnected ->
                if (isConnected && !requestedThisProcess) {
                    val granted = notificationPermissionState.refresh()
                    if (!granted) {
                        requestedThisProcess = true
                        _effects.send(HomeEffect.RequestNotificationPermission)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    // -----------------------------------------------------------------------
    // Intent handling
    // -----------------------------------------------------------------------

    /**
     * Single entry point for UI-initiated actions.
     *
     * Each branch is O(1) — heavy lifting is done in the domain layer.
     */
    public fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Connect -> onConnect()
            HomeIntent.Disconnect -> onDisconnect()
            HomeIntent.Cancel -> onDisconnect()
            HomeIntent.Retry -> onConnect()
            HomeIntent.AddServer -> onAddServer()
            is HomeIntent.SelectServer -> onSelectServer(intent)
        }
    }

    // -----------------------------------------------------------------------
    // Public permission helpers
    // -----------------------------------------------------------------------

    /**
     * Re-reads the OS notification-permission gate and updates [notificationsGranted].
     *
     * Called by the route on `ON_RESUME` (gated on `!requestInFlight`) to close the
     * pull-semantics loop — i.e. to surface a change the user made in system settings while
     * the app was paused. Also called by the `RequestPermission` launcher result callback so
     * that both a grant and a deny update the gate immediately, and the banner visibility
     * follows without waiting for the next resume.
     */
    public fun refreshNotificationPermission() {
        viewModelScope.launch {
            notificationPermissionState.refresh()
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Reads the currently selected server and requests a connection.
     *
     * If no server is selected the intent is silently dropped — the UI guards against
     * this case by showing Connect only when a server is present ([HomeUiState.Disconnected]
     * with a non-null server). If the requirement changes, emit [HomeEffect.NavigateToServers]
     * here instead.
     */
    private fun onConnect() {
        viewModelScope.launch {
            val server: Server = servers.observeSelectedServer().first() ?: return@launch
            controller.connect(server)
        }
    }

    private fun onDisconnect() {
        viewModelScope.launch {
            controller.disconnect()
        }
    }

    private fun onAddServer() {
        viewModelScope.launch {
            _effects.send(HomeEffect.NavigateToServers)
        }
    }

    private fun onSelectServer(intent: HomeIntent.SelectServer) {
        viewModelScope.launch {
            servers.selectServer(intent.id)
        }
    }

    // -----------------------------------------------------------------------
    // State mapping
    // -----------------------------------------------------------------------

    private fun mapToUiState(connState: VpnConnectionState, selected: Server?): HomeUiState = when (connState) {
        VpnConnectionState.Loading -> HomeUiState.Loading
        VpnConnectionState.Disconnected -> HomeUiState.Disconnected(selected)
        // PermissionRequired produces the same resting UI as Disconnected.
        // The permission dialog is triggered via HomeEffect.RequestVpnPermission
        // (derived in init {}) — not via a distinct UI state.
        VpnConnectionState.PermissionRequired -> HomeUiState.Disconnected(selected)
        is VpnConnectionState.Connecting -> HomeUiState.Connecting(connState.server)
        is VpnConnectionState.Connected -> HomeUiState.Connected(
            server = connState.server,
            since = connState.since,
            traffic = connState.traffic,
        )
        is VpnConnectionState.Error -> HomeUiState.Error(
            reason = connState.reason,
            server = selected,
        )
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    private companion object {
        /** Grace window (ms) for WhileSubscribed — survives activity recreation. */
        private const val SUBSCRIPTION_STOP_TIMEOUT_MS = 5_000L
    }
}
