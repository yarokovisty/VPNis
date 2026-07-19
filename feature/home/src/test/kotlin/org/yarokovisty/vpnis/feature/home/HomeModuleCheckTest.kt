package org.yarokovisty.vpnis.feature.home

import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.check.checkModules
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController
import org.yarokovisty.vpnis.core.domain.permission.NotificationPermissionState
import org.yarokovisty.vpnis.core.domain.repository.ServerRepository

/**
 * Verifies that [homeModule] resolves the [HomeViewModel] definition without error (#114, T-2).
 *
 * Supplies test doubles for all three [HomeViewModel] constructor parameters
 * ([ConnectionController], [ServerRepository], [NotificationPermissionState]) via a local
 * `testDepsModule` so the check runs without the real `:data:vpn` or `:data:fake` modules.
 *
 * Run under Robolectric so that [androidContext()] is available for the Koin Android extension —
 * `koin-android`'s [viewModel] DSL needs an Application context to register the ViewModel scope.
 *
 * [checkModules] (KoinApplication extension) instantiates every definition in the graph and
 * throws if any dependency is missing or fails to resolve — this catches a missing 3rd `get()`
 * if the module binding is not updated to the 3-arg constructor.
 */
@Suppress("DEPRECATION") // checkModules is deprecated in favour of verify(); retained for dynamic graph check
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeModuleCheckTest {

    @Test
    fun `homeModule EXPECT HomeViewModel resolves with all three dependencies`() {
        // Given — supply a Robolectric application context + fakes for the three VM deps.
        val context: Context = RuntimeEnvironment.getApplication()

        val testDepsModule = module {
            single<ConnectionController> { FakeConnectionController() }
            single<ServerRepository> { FakeServerRepository() }
            single<NotificationPermissionState> { FakeNotificationPermissionState() }
        }

        // When / Then — throws if HomeViewModel(get(), get(), get()) cannot be satisfied.
        koinApplication {
            androidContext(context)
            modules(homeModule, testDepsModule)
        }.checkModules()
    }
}
