package org.yarokovisty.vpnis.core.domain.connection

/**
 * Returns `true` when moving from [from] to [to] is a legal state transition
 * according to the VPN connection lifecycle.
 *
 * Legality is based on the **variant** (class) of each state, not on payload.
 * Self-transitions:
 * - `Connected -> Connected` — **legal** (traffic/since refresh).
 * - `Error -> Error` — **legal** (new failure reason on a retry attempt).
 * - All other self-transitions — **illegal** (no meaningful payload change).
 *
 * See [VpnConnectionState] for the full transition table and rationale.
 */
public fun isLegalTransition(from: VpnConnectionState, to: VpnConnectionState): Boolean = when (from) {
    is VpnConnectionState.Loading -> legalFromLoading(to)
    is VpnConnectionState.Disconnected -> legalFromDisconnected(to)
    is VpnConnectionState.PermissionRequired -> legalFromPermissionRequired(to)
    is VpnConnectionState.Connecting -> legalFromConnecting(to)
    is VpnConnectionState.Connected -> legalFromConnected(to)
    is VpnConnectionState.Error -> legalFromError(to)
}

private fun legalFromLoading(to: VpnConnectionState): Boolean = to is VpnConnectionState.Disconnected ||
    to is VpnConnectionState.PermissionRequired ||
    to is VpnConnectionState.Connected ||
    to is VpnConnectionState.Error

private fun legalFromDisconnected(to: VpnConnectionState): Boolean = to is VpnConnectionState.Connecting ||
    to is VpnConnectionState.PermissionRequired

private fun legalFromPermissionRequired(to: VpnConnectionState): Boolean = to is VpnConnectionState.Connecting ||
    to is VpnConnectionState.Disconnected

private fun legalFromConnecting(to: VpnConnectionState): Boolean = to is VpnConnectionState.Connected ||
    to is VpnConnectionState.Error ||
    to is VpnConnectionState.Disconnected

// Connected -> Connected is explicitly allowed for traffic/since refresh.
private fun legalFromConnected(to: VpnConnectionState): Boolean = to is VpnConnectionState.Connected ||
    to is VpnConnectionState.Disconnected ||
    to is VpnConnectionState.Error

// Error -> Error is explicitly allowed when a new failure reason replaces the old one.
private fun legalFromError(to: VpnConnectionState): Boolean = to is VpnConnectionState.Connecting ||
    to is VpnConnectionState.Disconnected ||
    to is VpnConnectionState.PermissionRequired ||
    to is VpnConnectionState.Error
