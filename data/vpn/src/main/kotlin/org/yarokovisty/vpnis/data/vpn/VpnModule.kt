package org.yarokovisty.vpnis.data.vpn

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the `:data:vpn` layer.
 *
 * Currently a placeholder that can receive future bindings (e.g. a real
 * [org.yarokovisty.vpnis.core.domain.connection.ConnectionController] implementation from
 * issue #63) without touching the caller's DI graph.
 *
 * [VpnTunnelService] is an Android [android.app.Service] and is instantiated by the
 * framework, not by Koin — it is NOT registered here. Issue #63 will add the
 * ConnectionController binding once the Xray-core lifecycle is wired.
 */
public val vpnModule: Module = module {
    // TODO(#63): single<ConnectionController> { DefaultConnectionController(get()) }
}
