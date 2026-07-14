package org.yarokovisty.vpnis.data.vpn

import org.yarokovisty.vpnis.core.domain.model.ConnectionError

/**
 * Callback interface through which [VpnTunnelService] writes lifecycle events back into
 * the [ConnectionControllerImpl] state machine.
 *
 * Keeping these callbacks on a separate interface (rather than casting
 * [org.yarokovisty.vpnis.core.domain.connection.ConnectionController] to an impl type)
 * means:
 * - [VpnTunnelService] depends only on this narrow internal interface, not on the impl class.
 * - The dependency is injectable and replaceable in tests.
 * - [ConnectionControllerImpl] implements this alongside the public [ConnectionController]
 *   contract without leaking its callback surface to external modules.
 *
 * ## Thread-safety
 *
 * These methods may be called from the service's main thread or from background coroutines
 * (e.g. the hev native-loop coroutine on [kotlinx.coroutines.Dispatchers.IO]). Every
 * implementation must be thread-safe. [ConnectionControllerImpl] satisfies this by writing
 * exclusively to [kotlinx.coroutines.flow.MutableStateFlow], which is thread-safe.
 */
internal interface TunnelStateSink {

    /**
     * Called by [VpnTunnelService] after [android.net.VpnService.Builder.establish] succeeds
     * and the hev native loop is running. The controller transitions from
     * [org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState.Connecting] to
     * [org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState.Connected].
     */
    fun onTunnelEstablished()

    /**
     * Called by [VpnTunnelService] after the tunnel has been fully torn down (hev stopped,
     * fd closed). Transitions the state to
     * [org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState.Disconnected].
     */
    fun onTunnelStopped()

    /**
     * Called by [VpnTunnelService] when the tunnel encounters a fatal error.
     *
     * @param reason The typed failure reason to surface in
     *   [org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState.Error].
     */
    fun onTunnelError(reason: ConnectionError)

    /**
     * Called by [VpnTunnelService] when [android.net.VpnService.Builder.establish] returns
     * `null`, meaning the OS VPN permission has not been granted.
     *
     * Transitions to
     * [org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState.PermissionRequired]
     * so the presentation layer can trigger `VpnService.prepare()`.
     */
    fun onPermissionRequired()
}
