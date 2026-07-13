package org.yarokovisty.vpnis.data.fake

import org.koin.core.module.Module
import org.koin.dsl.module
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController
import org.yarokovisty.vpnis.core.domain.repository.ServerRepository

/**
 * Koin module that binds [FakeConnectionController] and [FakeServerRepository]
 * as the app-wide singletons for [ConnectionController] and [ServerRepository].
 *
 * **Swap invariant (epic B, issue #66):** replacing `fakeVpnModule` with the real
 * `:data:vpn` module (which provides the same [ConnectionController] and
 * [ServerRepository] bindings) wires the production tunnel without touching
 * `:feature:home`, `:app`, or any other consumer. The domain interfaces are the
 * seam; this module is the only thing that changes.
 */
public val fakeVpnModule: Module = module {
    single<ConnectionController> { FakeConnectionController() }
    single<ServerRepository> { FakeServerRepository() }
}
