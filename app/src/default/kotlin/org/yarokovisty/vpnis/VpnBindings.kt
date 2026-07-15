package org.yarokovisty.vpnis

import org.koin.core.module.Module
import org.yarokovisty.vpnis.data.fake.fakeVpnModule

/**
 * Default-variant VPN backend selector (issue #66).
 *
 * Active when `vpnis.buildNative=false` (the default / F-Droid channel). Exposes the fake
 * [org.yarokovisty.vpnis.core.domain.connection.ConnectionController] via [fakeVpnModule],
 * so the app ships without a real [android.net.VpnService], VPN permissions, or the libXray
 * AAR.
 *
 * The `buildNative` source set provides an identically named object in the same package that
 * returns the real `vpnModule`. Exactly one variant is active per build — the source-set
 * injection is wired in `app/build.gradle.kts` via
 * `androidComponents.onVariants { v -> v.sources.kotlin?.addStaticSourceDirectory(...) }`.
 *
 * [VpnBindings] is referenced **only** from [VpnisApplication] (and the app navigation test) —
 * it is the single seam that flips the whole fake→real swap.
 */
internal object VpnBindings {
    val module: Module = fakeVpnModule
}
