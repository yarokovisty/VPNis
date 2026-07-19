package org.yarokovisty.vpnis.data.fake

import org.koin.core.module.Module
import org.koin.dsl.module
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController
import org.yarokovisty.vpnis.core.domain.permission.NotificationPermissionState

/**
 * Koin module that binds [FakeConnectionController] as the app-wide singleton for
 * [ConnectionController].
 *
 * This module provides the fake VPN core only. The [ServerRepository] binding has moved
 * to `:data:server`'s `serverModule`, which provides the real in-memory implementation
 * pre-seeded with the operator default server (FR-50, SRS §5.6, issue #56).
 *
 * [FakeServerRepository] remains in this module's source set for use in UI tests (issue #58)
 * that need to drive empty / specific-selection states — it is just not bound in this Koin
 * module anymore.
 *
 * **Swap invariant (epic B, issue #66):** replacing `fakeVpnModule` with the real
 * `:data:vpn` module (which provides the production [ConnectionController]) wires the
 * production tunnel without touching `:feature:home`, `:app`, or any other consumer.
 * The domain interface is the seam; this module is the only thing that changes.
 */
public val fakeVpnModule: Module = module {
    single<ConnectionController> { FakeConnectionController() }

    // NotificationPermissionState — fake pull-model gate; granted by default.
    // Symmetric with vpnModule so the swap invariant and checkModules both hold,
    // and #114 / #131 can wire a consumer without re-touching this module.
    single<NotificationPermissionState> { FakeNotificationPermissionState() }
}
