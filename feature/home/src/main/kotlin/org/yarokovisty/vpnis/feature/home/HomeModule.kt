package org.yarokovisty.vpnis.feature.home

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the Home feature.
 *
 * Binds [HomeViewModel] so Koin can inject it into [HomeRoute] via [koinViewModel].
 * The constructor dependencies ([ConnectionController], [ServerRepository], and
 * [NotificationPermissionState]) are resolved from whatever domain module is active at
 * startup — [fakeVpnModule] during development/testing, the real `:data:vpn` module once
 * it lands in epic B (#66). This feature module never imports from `:data:*` directly,
 * preserving the domain-seam invariant.
 */
public val homeModule: Module = module {
    viewModel { HomeViewModel(get(), get(), get()) }
}
