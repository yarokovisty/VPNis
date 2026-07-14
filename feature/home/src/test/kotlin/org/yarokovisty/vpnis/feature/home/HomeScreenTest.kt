package org.yarokovisty.vpnis.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
import org.yarokovisty.vpnis.design.theme.VPNisTheme
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ---------------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------------

    private val testServer = Server(
        id = ServerId("a"),
        name = "Frankfurt · FR-01",
        config = "vless://test-config",
    )

    private val testInstant = Instant.ofEpochSecond(1_700_000_000L)

    private val testTraffic = TrafficStats(
        rxBytes = 1_024L,
        txBytes = 512L,
        rxBps = 100L,
        txBps = 50L,
    )

    // ---------------------------------------------------------------------------
    // Helper — resolve a string from the feature:home resources via Robolectric context
    // ---------------------------------------------------------------------------

    private fun str(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    // ---------------------------------------------------------------------------
    // Loading state
    // ---------------------------------------------------------------------------

    @Test
    fun `loading state - light theme - renders without crashing and no other state text visible`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(uiState = HomeUiState.Loading, onIntent = {})
            }
        }
        // No status text from other states should be present.
        composeRule.onNodeWithText(str(R.string.home_status_connected)).assertDoesNotExist()
        composeRule.onNodeWithText(str(R.string.home_status_disconnected)).assertDoesNotExist()
        composeRule.onNodeWithText(str(R.string.home_status_connecting)).assertDoesNotExist()
        composeRule.onNodeWithText(str(R.string.home_status_error)).assertDoesNotExist()
    }

    @Test
    fun `loading state - dark theme - renders without crashing`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = true) {
                HomeScreen(uiState = HomeUiState.Loading, onIntent = {})
            }
        }
        composeRule.onNodeWithText(str(R.string.home_status_connected)).assertDoesNotExist()
        composeRule.onNodeWithText(str(R.string.home_status_disconnected)).assertDoesNotExist()
    }

    // ---------------------------------------------------------------------------
    // Disconnected state — no server (empty)
    // ---------------------------------------------------------------------------

    @Test
    fun `disconnected empty - light theme - shows empty title`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(uiState = HomeUiState.Disconnected(server = null), onIntent = {})
            }
        }
        // Scroll to the card title in case it is below the fold.
        composeRule.onNodeWithText(str(R.string.home_empty_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `disconnected empty - light theme - shows connect and add server buttons`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(uiState = HomeUiState.Disconnected(server = null), onIntent = {})
            }
        }
        composeRule.onNodeWithText(str(R.string.home_action_connect))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.home_action_add_server))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `disconnected empty - dark theme - renders without crashing`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = true) {
                HomeScreen(uiState = HomeUiState.Disconnected(server = null), onIntent = {})
            }
        }
        composeRule.onNodeWithText(str(R.string.home_empty_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `disconnected empty - click Connect button - dispatches Connect intent`() {
        val captured = mutableListOf<HomeIntent>()
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(uiState = HomeUiState.Disconnected(server = null), onIntent = { captured += it })
            }
        }
        composeRule.onNodeWithText(str(R.string.home_action_connect))
            .performScrollTo()
            .performClick()
        assertTrue(HomeIntent.Connect in captured)
    }

    @Test
    fun `disconnected empty - click Add Server button - dispatches AddServer intent`() {
        val captured = mutableListOf<HomeIntent>()
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(uiState = HomeUiState.Disconnected(server = null), onIntent = { captured += it })
            }
        }
        composeRule.onNodeWithText(str(R.string.home_action_add_server))
            .performScrollTo()
            .performClick()
        assertEquals(listOf(HomeIntent.AddServer), captured)
    }

    // ---------------------------------------------------------------------------
    // Disconnected state — server present
    // ---------------------------------------------------------------------------

    @Test
    fun `disconnected with server - light theme - shows server name and disconnected status`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(uiState = HomeUiState.Disconnected(server = testServer), onIntent = {})
            }
        }
        composeRule.onNodeWithText(testServer.name).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.home_status_disconnected)).assertIsDisplayed()
    }

    @Test
    fun `disconnected with server - dark theme - renders without crashing`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = true) {
                HomeScreen(uiState = HomeUiState.Disconnected(server = testServer), onIntent = {})
            }
        }
        composeRule.onNodeWithText(testServer.name).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `disconnected with server - click connection button - dispatches Connect intent`() {
        val captured = mutableListOf<HomeIntent>()
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(
                    uiState = HomeUiState.Disconnected(server = testServer),
                    onIntent = { captured += it },
                )
            }
        }
        composeRule.onNode(hasContentDescription(str(R.string.home_button_content_description)))
            .performClick()
        assertTrue(HomeIntent.Connect in captured)
    }

    // ---------------------------------------------------------------------------
    // Connecting state
    // ---------------------------------------------------------------------------

    @Test
    fun `connecting state - light theme - shows connecting status`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(uiState = HomeUiState.Connecting(server = testServer), onIntent = {})
            }
        }
        composeRule.onNodeWithText(str(R.string.home_status_connecting)).assertIsDisplayed()
    }

    @Test
    fun `connecting state - dark theme - renders without crashing`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = true) {
                HomeScreen(uiState = HomeUiState.Connecting(server = testServer), onIntent = {})
            }
        }
        composeRule.onNodeWithText(str(R.string.home_status_connecting)).assertIsDisplayed()
    }

    @Test
    fun `connecting state - click Cancel button - dispatches Cancel intent`() {
        val captured = mutableListOf<HomeIntent>()
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(
                    uiState = HomeUiState.Connecting(server = testServer),
                    onIntent = { captured += it },
                )
            }
        }
        composeRule.onNodeWithText(str(R.string.home_action_cancel))
            .performScrollTo()
            .performClick()
        assertEquals(listOf(HomeIntent.Cancel), captured)
    }

    // ---------------------------------------------------------------------------
    // Connected state
    // ---------------------------------------------------------------------------

    @Test
    fun `connected state - light theme - shows connected status`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(
                    uiState = HomeUiState.Connected(
                        server = testServer,
                        since = testInstant,
                        traffic = testTraffic,
                    ),
                    onIntent = {},
                )
            }
        }
        composeRule.onNodeWithText(str(R.string.home_status_connected)).assertIsDisplayed()
    }

    @Test
    fun `connected state - dark theme - renders without crashing`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = true) {
                HomeScreen(
                    uiState = HomeUiState.Connected(
                        server = testServer,
                        since = testInstant,
                        traffic = null,
                    ),
                    onIntent = {},
                )
            }
        }
        composeRule.onNodeWithText(str(R.string.home_status_connected)).assertIsDisplayed()
    }

    @Test
    fun `connected state - click connection button - dispatches Disconnect intent`() {
        val captured = mutableListOf<HomeIntent>()
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(
                    uiState = HomeUiState.Connected(
                        server = testServer,
                        since = testInstant,
                        traffic = null,
                    ),
                    onIntent = { captured += it },
                )
            }
        }
        composeRule.onNode(hasContentDescription(str(R.string.home_button_content_description)))
            .performClick()
        assertTrue(HomeIntent.Disconnect in captured)
    }

    // ---------------------------------------------------------------------------
    // Error state
    // ---------------------------------------------------------------------------

    @Test
    fun `error state - light theme - shows error status`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(
                    uiState = HomeUiState.Error(
                        reason = ConnectionError.ServerUnreachable,
                        server = testServer,
                    ),
                    onIntent = {},
                )
            }
        }
        composeRule.onNodeWithText(str(R.string.home_status_error)).assertIsDisplayed()
    }

    @Test
    fun `error state - dark theme - renders without crashing`() {
        composeRule.setContent {
            VPNisTheme(darkTheme = true) {
                HomeScreen(
                    uiState = HomeUiState.Error(
                        reason = ConnectionError.ServerUnreachable,
                        server = testServer,
                    ),
                    onIntent = {},
                )
            }
        }
        composeRule.onNodeWithText(str(R.string.home_status_error)).assertIsDisplayed()
    }

    @Test
    fun `error state - click Retry button - dispatches Retry intent`() {
        val captured = mutableListOf<HomeIntent>()
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(
                    uiState = HomeUiState.Error(
                        reason = ConnectionError.ServerUnreachable,
                        server = testServer,
                    ),
                    onIntent = { captured += it },
                )
            }
        }
        composeRule.onNodeWithText(str(R.string.home_action_retry))
            .performScrollTo()
            .performClick()
        assertEquals(listOf(HomeIntent.Retry), captured)
    }

    @Test
    fun `error state - click choose another button - dispatches AddServer intent`() {
        val captured = mutableListOf<HomeIntent>()
        composeRule.setContent {
            VPNisTheme(darkTheme = false) {
                HomeScreen(
                    uiState = HomeUiState.Error(
                        reason = ConnectionError.ServerUnreachable,
                        server = testServer,
                    ),
                    onIntent = { captured += it },
                )
            }
        }
        composeRule.onNodeWithText(str(R.string.home_action_choose_another))
            .performScrollTo()
            .performClick()
        assertEquals(listOf(HomeIntent.AddServer), captured)
    }
}
