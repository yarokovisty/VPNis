package org.yarokovisty.vpnis.feature.home

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    val notificationsGranted by viewModel.notificationsGranted.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ---------------------------------------------------------------------------
    // VPN permission launcher
    // ---------------------------------------------------------------------------

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onIntent(HomeIntent.Connect)
        } else {
            viewModel.onIntent(HomeIntent.Cancel)
        }
    }

    // ---------------------------------------------------------------------------
    // Notification permission — route-local denial FSM (plan §3)
    // ---------------------------------------------------------------------------

    // Survives recomposition and activity recreation; tracks whether the system dialog has
    // been shown at least once in this Activity instance's saved-state lifetime.
    var hasRequestedBefore by rememberSaveable { mutableStateOf(false) }

    // Transient flags — intentionally NOT in rememberSaveable: they are only meaningful
    // while the dialog is visually on screen (requestInFlight) or for the current Connected
    // session (dismissedThisSession).
    var requestInFlight by remember { mutableStateOf(false) }
    var dismissedThisSession by remember { mutableStateOf(false) }

    // POST_NOTIFICATIONS result launcher.
    // We do not branch on the boolean result — refreshNotificationPermission() re-reads the
    // two-part gate (app-level AND channel importance), which is the source of truth for both
    // the grant and deny paths.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        requestInFlight = false
        viewModel.refreshNotificationPermission()
    }

    // Pull-semantics loop: re-read the gate on every resume so a change made in system settings
    // (grant or revoke) is reflected without needing another Connected transition. Gated on
    // !requestInFlight to avoid racing the in-flight dialog's pause/resume (ux#2).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!requestInFlight) {
            viewModel.refreshNotificationPermission()
        }
    }

    // Reset the session-scoped dismiss whenever a fresh Connected session starts. Keyed on
    // `since` (an Instant) — changes each time a new tunnel connection is established (ux#1).
    val connectedSince = (uiState as? HomeUiState.Connected)?.since
    LaunchedEffect(connectedSince) {
        if (connectedSince != null) {
            dismissedThisSession = false
        }
    }

    // ---------------------------------------------------------------------------
    // One-shot effects
    // ---------------------------------------------------------------------------

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
                HomeEffect.RequestNotificationPermission -> {
                    // On API < 33 there is no POST_NOTIFICATIONS runtime permission — ignore.
                    // On API >= 33 launch the system dialog exactly once per Activity lifetime
                    // (hasRequestedBefore guard). If the dialog was already shown once, do
                    // nothing — the gate-driven denial banner is the only remaining UX surface.
                    // Never relaunch: Play Store "don't nag" guideline + acceptance criterion
                    // ("повторный Connect после отказа диалог НЕ перезапускается").
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasRequestedBefore) {
                        hasRequestedBefore = true
                        requestInFlight = true
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // else: already asked once (or API < 33) — the banner handles the rest.
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Banner visibility — gate-driven (plan §3, ux#2/#3)
    // ---------------------------------------------------------------------------

    // Visible only when:
    // - we already asked (otherwise the banner would appear before the dialog on the very
    //   first Connected entry, which `requestedThisProcess` in the VM prevents);
    // - the gate is still denied (two-part: app-level + channel importance);
    // - the system dialog is not currently on screen (prevents banner + dialog at the same time);
    // - the user has not dismissed it for this Connected session.
    val showNotificationBanner =
        hasRequestedBefore && !notificationsGranted && !requestInFlight && !dismissedThisSession

    // ---------------------------------------------------------------------------
    // Screen
    // ---------------------------------------------------------------------------

    HomeScreen(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        showNotificationBanner = showNotificationBanner,
        onOpenNotificationSettings = {
            // Prefer the channel-level deep-link (lands directly on the tunnel channel — the common
            // "channel silenced" case). Fall back to app-level settings if the channel Intent cannot
            // be resolved (e.g. on a restricted OEM build).
            runCatching {
                context.startActivity(
                    NotificationSettingsIntents.channelNotificationSettings(
                        context = context,
                        channelId = viewModel.notificationChannelId,
                    ),
                )
            }.onFailure {
                runCatching {
                    context.startActivity(NotificationSettingsIntents.appNotificationSettings(context))
                }
            }
        },
        onDismissNotificationBanner = { dismissedThisSession = true },
    )
}
