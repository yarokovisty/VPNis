package org.yarokovisty.vpnis.data.fake

import org.junit.Test
import org.koin.test.check.checkKoinModules

/**
 * Verifies that [fakeVpnModule] resolves every declared definition without error (issue #127, T-8).
 *
 * Pure JVM — no Android context required because [fakeVpnModule] contains no [androidContext()]
 * bindings. [checkKoinModules] instantiates every definition in the module and throws if any
 * dependency is missing or fails to resolve.
 */
@Suppress("DEPRECATION") // checkKoinModules is deprecated in favour of verify(); retained for dynamic graph check
class FakeVpnModuleCheckTest {

    @Test
    fun `fakeVpnModule EXPECT all definitions resolve without error`() {
        checkKoinModules(listOf(fakeVpnModule))
    }
}
