package org.yarokovisty.vpnis

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.yarokovisty.vpnis.design.theme.VPNisTheme
import org.yarokovisty.vpnis.feature.home.HomeRoute

/**
 * Release entry point: the Home screen wired to the Koin-provided ViewModel.
 *
 * [HomeRoute] resolves [HomeViewModel] from Koin (backed by [fakeVpnModule] until
 * epic B lands), collects its [uiState], and renders [HomeScreen]. Navigation and
 * permission callbacks are no-ops until #55 (nav graph) and #57 (permission flow)
 * wire them up.
 */
@Composable
fun AppRoot() {
    VPNisTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            HomeRoute()
        }
    }
}
