package org.yarokovisty.vpnis.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

/**
 * Stateful Home screen entry point.
 *
 * Obtains [HomeViewModel] from Koin, collects [HomeViewModel.uiState] with
 * lifecycle awareness, and consumes [HomeViewModel.effects] while the screen is
 * at least [androidx.lifecycle.Lifecycle.State.STARTED]. Renders the stateless
 * [HomeScreen] composable with the current state and the ViewModel's intent dispatcher.
 *
 * Navigation and permission callbacks default to no-ops. Real launchers land in:
 * - Permission handling: #57 (VpnService.prepare() activity result integration)
 * - Navigation to server list: #55 (Compose Navigation graph)
 *
 * @param modifier              Passed through to [HomeScreen].
 * @param onNavigateToServers   Called when the ViewModel emits [HomeEffect.NavigateToServers].
 * @param onRequestVpnPermission Called when the ViewModel emits [HomeEffect.RequestVpnPermission].
 * @param viewModel             Resolved by Koin; override in Compose previews or tests.
 */
@Composable
public fun HomeRoute(
    modifier: Modifier = Modifier,
    onNavigateToServers: () -> Unit = {},
    onRequestVpnPermission: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Consume one-shot effects while the screen is at least STARTED so stale effects
    // from the back stack are not re-delivered on return. collectLatest ensures that
    // if a new effect arrives before the handler finishes, the previous handler is
    // cancelled — effects are fire-and-forget UI triggers, not suspending operations,
    // so this is safe.
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                HomeEffect.NavigateToServers -> onNavigateToServers()
                HomeEffect.RequestVpnPermission -> onRequestVpnPermission()
            }
        }
    }

    HomeScreen(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}
