package org.yarokovisty.vpnis.data.vpn

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController

/**
 * Koin module for the `:data:vpn` layer.
 *
 * Provides the production [ConnectionController] implementation backed by the real
 * [VpnTunnelService] and [NoOpXrayCore] (Xray stub until issue #72 lands).
 *
 * ## Bindings
 *
 * - [ConnectionController] / [TunnelStateSink] — both implemented by [ConnectionControllerImpl].
 *   Bound as a single `single<ConnectionControllerImpl>` and re-exposed under both interfaces
 *   so that:
 *   - Presentation-layer callers receive the [ConnectionController] abstraction.
 *   - [VpnTunnelService] injects [TunnelStateSink] without knowing the impl class.
 * - [TunnelLauncher] — [AndroidTunnelLauncher], uses `androidContext()` for intent dispatch.
 * - [XrayCore] — [NoOpXrayCore] until the libXray gomobile AAR is introduced in issue #72.
 *
 * ## Swap invariant (issue #66)
 *
 * Replacing `fakeVpnModule` (in `:data:fake`) with this `vpnModule` in [VpnisApplication]
 * wires the real tunnel without touching any other module. This module is the only change
 * needed for the swap.
 *
 * ## Not registered here
 *
 * [VpnTunnelService] is instantiated by the Android framework, not by Koin — do not
 * add it here. The service uses `by inject()` (KoinComponent) to pull its dependencies.
 */
public val vpnModule: Module = module {
    // XrayCore — NoOp until the real AAR lands in issue #72.
    // Swap this line for: single<XrayCore> { LibXrayCoreImpl(get()) }
    single<XrayCore> { NoOpXrayCore() }

    // TunnelLauncher — dispatches intents to VpnTunnelService. Requires Android Context.
    single<TunnelLauncher> { AndroidTunnelLauncher(context = androidContext()) }

    // ConnectionControllerImpl is the single concrete class that satisfies both
    // ConnectionController (public domain interface) and TunnelStateSink (internal
    // callback interface). Create it once and expose under both types.
    single { ConnectionControllerImpl(launcher = get()) }
    single<ConnectionController> { get<ConnectionControllerImpl>() }
    single<TunnelStateSink> { get<ConnectionControllerImpl>() }
}
