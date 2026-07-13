package org.yarokovisty.vpnis.core.domain.connection

import kotlinx.coroutines.flow.Flow
import org.yarokovisty.vpnis.core.domain.model.Server

/**
 * Controls the VPN tunnel lifecycle and exposes its state.
 *
 * Implementations live in :data:vpn. A fake implementation will be provided by
 * issue #53 for driving the presentation layer before the real tunnel is wired.
 */
public interface ConnectionController {
    public val state: Flow<VpnConnectionState>
    public suspend fun connect(server: Server)
    public suspend fun disconnect()
}
