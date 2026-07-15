package org.yarokovisty.vpnis

import org.koin.core.module.Module
import org.yarokovisty.vpnis.data.vpn.vpnModule

/**
 * Native-variant VPN backend selector (issue #66).
 *
 * Active when `vpnis.buildNative=true`. Exposes the production
 * [org.yarokovisty.vpnis.core.domain.connection.ConnectionController] via [vpnModule] —
 * the real [org.yarokovisty.vpnis.data.vpn.ConnectionControllerImpl] backed by
 * [android.net.VpnService] and the libXray `XrayCore.aar`. Depending on `:data:vpn` (wired
 * conditionally in `app/build.gradle.kts`) also merges its `<service>` and VPN permissions
 * into the app manifest.
 *
 * The `default` source set provides an identically named object in the same package that
 * returns `fakeVpnModule`. Exactly one variant is active per build — see the `default`
 * variant's [VpnBindings] KDoc for the source-set injection wiring.
 */
internal object VpnBindings {
    val module: Module = vpnModule
}
