package org.yarokovisty.vpnis

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.yarokovisty.vpnis.data.fake.fakeVpnModule
import org.yarokovisty.vpnis.data.server.serverModule
import org.yarokovisty.vpnis.feature.home.homeModule

/**
 * Bottom-navigation integration tests for [VpnisApp].
 *
 * [Config] overrides the Application class with a plain [android.app.Application] so
 * [VpnisApplication.onCreate] does NOT run (and does NOT call startKoin automatically).
 * Koin is started lazily inside [startKoinIfNeeded] — called at the top of each test —
 * which starts it once for the whole test-class run and leaves it running so that
 * consecutive tests share the same Koin context (modules are stateless factories, safe to
 * share). This avoids [org.koin.core.error.KoinApplicationAlreadyStartedException] and
 * [org.koin.core.error.ClosedScopeException] that arise when trying to start/stop Koin
 * around individual [composeRule] test executions.
 *
 * [isIncludeAndroidResources = true] is set in app/build.gradle.kts and is required for
 * Robolectric to load Android string resources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
internal class VpnisAppNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ---------------------------------------------------------------------------
    // Helper — resolve strings from the app module resources via Robolectric context
    // ---------------------------------------------------------------------------

    private fun str(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    // ---------------------------------------------------------------------------
    // Navigation tests
    // ---------------------------------------------------------------------------

    @Test
    fun `start destination is Home - home nav label is visible`() {
        startKoinIfNeeded()
        composeRule.setContent { VpnisApp() }

        composeRule.onNodeWithText(str(R.string.nav_home)).assertIsDisplayed()
    }

    @Test
    fun `click Servers nav item - servers placeholder title is displayed`() {
        startKoinIfNeeded()
        composeRule.setContent { VpnisApp() }

        // The nav bar item and the placeholder heading both render the nav_servers string.
        // Two nodes exist: the nav bar label (index 0) and the placeholder heading (index 1).
        composeRule.onAllNodesWithText(str(R.string.nav_servers)).onFirst().performClick()

        composeRule.onAllNodesWithText(str(R.string.nav_servers))[1].assertIsDisplayed()
    }

    @Test
    fun `click Bypass nav item - bypass placeholder is displayed`() {
        startKoinIfNeeded()
        composeRule.setContent { VpnisApp() }

        composeRule.onNodeWithText(str(R.string.nav_bypass)).performClick()

        composeRule.onAllNodesWithText(str(R.string.nav_bypass))[1].assertIsDisplayed()
    }

    @Test
    fun `click Settings nav item - settings placeholder is displayed`() {
        startKoinIfNeeded()
        composeRule.setContent { VpnisApp() }

        composeRule.onNodeWithText(str(R.string.nav_settings)).performClick()

        composeRule.onAllNodesWithText(str(R.string.nav_settings))[1].assertIsDisplayed()
    }

    @Test
    fun `navigate to Servers then click Home - home content returns`() {
        startKoinIfNeeded()
        composeRule.setContent { VpnisApp() }

        composeRule.onAllNodesWithText(str(R.string.nav_servers)).onFirst().performClick()
        composeRule.onNodeWithText(str(R.string.nav_home)).performClick()

        composeRule.onNodeWithText(str(R.string.nav_home)).assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun startKoinIfNeeded() {
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(ApplicationProvider.getApplicationContext())
                modules(homeModule, serverModule, fakeVpnModule)
            }
        }
    }
}
