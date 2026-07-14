package org.yarokovisty.vpnis.feature.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
 * ## VPN permission handling (issue #57)
 *
 * This route owns the full VPN permission lifecycle. When the ViewModel emits
 * [HomeEffect.RequestVpnPermission], the route calls [android.net.VpnService.prepare]
 * directly and either:
 * - launches the system permission dialog via an [androidx.activity.result.ActivityResultLauncher]
 *   if prepare returns a non-null [android.content.Intent], or
 * - proceeds immediately (consent already present) by dispatching [HomeIntent.Connect].
 *
 * The result callback translates the activity result into [HomeIntent.Connect] (granted) or
 * [HomeIntent.Cancel] (refused). `VpnService.prepare` and the resulting `Intent` never leave
 * this module — invariant I1.
 *
 * ## Navigation
 *
 * @param onNavigateToServers Called when the ViewModel emits [HomeEffect.NavigateToServers].
 *                            Wired by :app — this module does not own the NavController.
 *
 * @param modifier  Passed through to [HomeScreen].
 * @param viewModel Resolved by Koin; override in Compose previews or tests.
 */
@Composable
public fun HomeRoute(
    modifier: Modifier = Modifier,
    onNavigateToServers: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onIntent(HomeIntent.Connect)
        } else {
            viewModel.onIntent(HomeIntent.Cancel)
        }
    }

    // Consume one-shot effects while the screen is at least STARTED so stale effects
    // from the back stack are not re-delivered on return. collectLatest ensures that
    // if a new effect arrives before the handler finishes, the previous handler is
    // cancelled — effects are fire-and-forget UI triggers, not suspending operations,
    // so this is safe.
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                HomeEffect.NavigateToServers -> onNavigateToServers()
                HomeEffect.RequestVpnPermission -> {
                    val prepareIntent = android.net.VpnService.prepare(context)
                    if (prepareIntent != null) {
                        vpnPermissionLauncher.launch(prepareIntent)
                    } else {
                        // Consent already granted — proceed to connect immediately.
                        viewModel.onIntent(HomeIntent.Connect)
                    }
                }
            }
        }
    }

    HomeScreen(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}
