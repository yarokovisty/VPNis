package org.yarokovisty.vpnis.data.vpn

import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.test.check.checkModules
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Verifies that [vpnModule] resolves every declared definition without error (issue #127, T-8).
 *
 * Run under Robolectric so that a real [android.app.Application] context is available for the
 * [androidContext()] bindings used by [AndroidNotificationPermissionState],
 * [AndroidTunnelLauncher], [AndroidVpnConsentChecker], [XrayCoreProvider], and
 * [TunnelNotificationPresenter].
 *
 * [checkModules] (KoinApplication extension) instantiates every definition in the graph and
 * throws if any dependency is missing or fails to resolve.
 */
@Suppress("DEPRECATION") // checkModules is deprecated in favour of verify(); retained for dynamic graph check
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VpnModuleCheckTest {

    @Test
    fun `vpnModule EXPECT all definitions resolve without error`() {
        // Given — supply a Robolectric application context so androidContext() definitions resolve.
        val context: Context = RuntimeEnvironment.getApplication()

        // When / Then — throws if any definition is missing or unresolvable.
        koinApplication {
            androidContext(context)
            modules(vpnModule)
        }.checkModules()
    }
}
