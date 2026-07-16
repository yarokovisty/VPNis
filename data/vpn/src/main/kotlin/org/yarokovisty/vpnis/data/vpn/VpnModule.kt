package org.yarokovisty.vpnis.data.vpn

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController

/**
 * Koin module for the `:data:vpn` layer.
 *
 * Provides the production [ConnectionController] implementation backed by the real
 * [VpnTunnelService] and the source-set-appropriate [XrayCore] (resolved at build time
 * by [XrayCoreProvider]):
 * - Default builds (`buildNative=false`): [NoOpXrayCore].
 * - Native builds (`buildNative=true`): [LibXrayCoreImpl] backed by [RealLibxrayApi].
 *
 * ## Bindings
 *
 * - [ConnectionController] / [TunnelStateSink] — both implemented by [ConnectionControllerImpl].
 *   Bound as a single `single<ConnectionControllerImpl>` and re-exposed under both interfaces
 *   so that:
 *   - Presentation-layer callers receive the [ConnectionController] abstraction.
 *   - [VpnTunnelService] injects [TunnelStateSink] without knowing the impl class.
 * - [TunnelLauncher] — [AndroidTunnelLauncher], uses `androidContext()` for intent dispatch.
 * - [XrayCore] — resolved via [XrayCoreProvider.create], which selects the correct impl
 *   based on the active source set (no direct reference to [LibXrayCoreImpl] or
 *   [RealLibxrayApi] from `main`).
 *
 * ## Swap invariant (issue #66)
 *
 * Replacing `fakeVpnModule` (in `:data:fake`) with this `vpnModule` in [VpnisApplication]
 * wires the real tunnel without touching any other module. This module is the only change
 * needed for the swap. The public surface ([ConnectionController] + [TunnelStateSink]
 * bindings) is unchanged.
 *
 * ## Not registered here
 *
 * [VpnTunnelService] is instantiated by the Android framework, not by Koin — do not
 * add it here. The service uses `by inject()` (KoinComponent) to pull its dependencies.
 */
public val vpnModule: Module = module {
    // XrayCore — source-set variant selected at build time by XrayCoreProvider.
    // Default (buildNative=false): NoOpXrayCore.
    // Native (buildNative=true): LibXrayCoreImpl(RealLibxrayApi(), context.filesDir.path).
    single<XrayCore> { XrayCoreProvider.create(androidContext()) }

    // TunnelLauncher — dispatches intents to VpnTunnelService. Requires Android Context.
    single<TunnelLauncher> { AndroidTunnelLauncher(context = androidContext()) }

    // VpnConsentChecker — queries OS VPN consent state via VpnService.prepare().
    // Abstracted so ConnectionControllerImpl can be unit-tested with a fake stub.
    single<VpnConsentChecker> { AndroidVpnConsentChecker(androidContext()) }

    // ConnectionControllerImpl is the single concrete class that satisfies both
    // ConnectionController (public domain interface) and TunnelStateSink (internal
    // callback interface). Create it once and expose under both types.
    single { ConnectionControllerImpl(launcher = get(), consentChecker = get()) }
    single<ConnectionController> { get<ConnectionControllerImpl>() }
    single<TunnelStateSink> { get<ConnectionControllerImpl>() }
}
