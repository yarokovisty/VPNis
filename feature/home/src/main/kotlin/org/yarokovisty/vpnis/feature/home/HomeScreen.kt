package org.yarokovisty.vpnis.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.format.BitrateUnit
import org.yarokovisty.vpnis.core.format.formatBitrate
import org.yarokovisty.vpnis.design.uikit.banner.VPNisBanner
import org.yarokovisty.vpnis.design.uikit.banner.VPNisBannerAction
import org.yarokovisty.vpnis.design.uikit.banner.VPNisBannerVariant
import org.yarokovisty.vpnis.design.uikit.button.VPNisButton
import org.yarokovisty.vpnis.design.uikit.button.VPNisOutlinedButton
import org.yarokovisty.vpnis.feature.home.components.ConnectionButton
import org.yarokovisty.vpnis.feature.home.components.ConnectionButtonState
import org.yarokovisty.vpnis.feature.home.components.PingEmphasis
import org.yarokovisty.vpnis.feature.home.components.ServerCard
import org.yarokovisty.vpnis.feature.home.components.SessionTimer
import org.yarokovisty.vpnis.feature.home.components.StatusIndicator
import org.yarokovisty.vpnis.feature.home.components.TrafficStats
import org.yarokovisty.vpnis.feature.home.components.icons.HomeIcons

// ---------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------

internal val HomeContentHorizontalPadding = 20.dp
internal val HomeAppBarHeight = 56.dp
internal val HomeSectionSpacing = 16.dp
internal val HomeButtonSectionSpacing = 12.dp
private val EmptyCardCornerRadius = 22.dp
private val EmptyCardIconContainerSize = 44.dp
private val EmptyCardIconContainerRadius = 14.dp
private val EmptyCardIconSize = 24.dp
private val EmptyCardInternalPadding = 18.dp
private val EmptyCardIconTextGap = 14.dp
private val StatusToTimerSpacing = 8.dp
private val ButtonBlockTopSpacing = 24.dp
private val ButtonTopSpacing = 30.dp
private val ButtonTopSpacingEmpty = 34.dp

// ---------------------------------------------------------------------------
// Public screen composable
// ---------------------------------------------------------------------------

/**
 * Stateless Home screen composable.
 *
 * Edge-to-edge: applies [WindowInsets.safeDrawing] for horizontal + top insets only.
 * The bottom inset is intentionally left unconsumed — the app-level nav bar (#55) will
 * consume it. [enableEdgeToEdge] is called in MainActivity.
 *
 * The [when] over [HomeUiState] is exhaustive — adding a new subtype is a compile error.
 *
 * @param uiState Current UI state produced by the ViewModel (wired in #53).
 * @param onIntent Dispatch channel for user-initiated [HomeIntent]s.
 * @param modifier Optional [Modifier] applied to the outer container.
 */
@Composable
public fun HomeScreen(
    uiState: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
    showNotificationBanner: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onDismissNotificationBanner: () -> Unit = {},
) {
    when (uiState) {
        HomeUiState.Loading -> HomeLoadingContent(modifier = modifier)
        is HomeUiState.Disconnected -> HomeDisconnectedContent(
            state = uiState,
            onIntent = onIntent,
            modifier = modifier,
            showNotificationBanner = showNotificationBanner,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onDismissNotificationBanner = onDismissNotificationBanner,
        )
        is HomeUiState.Connecting -> HomeConnectingContent(
            state = uiState,
            onIntent = onIntent,
            modifier = modifier,
        )
        is HomeUiState.Connected -> HomeConnectedContent(
            state = uiState,
            onIntent = onIntent,
            modifier = modifier,
            showNotificationBanner = showNotificationBanner,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onDismissNotificationBanner = onDismissNotificationBanner,
        )
        is HomeUiState.Error -> HomeErrorContent(
            state = uiState,
            onIntent = onIntent,
            modifier = modifier,
        )
    }
}

// ---------------------------------------------------------------------------
// Shared chrome (app bar + scroll wrapper)
// ---------------------------------------------------------------------------

/**
 * Wraps content in the standard Home chrome: edge-to-edge insets, app bar, scrollable column.
 *
 * The app bar is a plain 56 dp Box with the "VPNis" title at 22sp/weight-500, matching
 * the design frames without pulling in the full Material [TopAppBar] component.
 */
@Composable
internal fun HomeScaffold(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            ),
    ) {
        // App bar — 56 dp row, title at 22sp / weight 500
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HomeAppBarHeight)
                .padding(horizontal = HomeContentHorizontalPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Scrollable content column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HomeContentHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
            // Bottom padding: content clears the gesture area; nav bar inset consumed by #55.
            Spacer(modifier = Modifier.height(HomeSectionSpacing))
        }
    }
}

// ---------------------------------------------------------------------------
// Loading state
// ---------------------------------------------------------------------------

@Composable
private fun HomeLoadingContent(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            ),
    ) {
        CircularProgressIndicator()
    }
}

// ---------------------------------------------------------------------------
// Disconnected state
// ---------------------------------------------------------------------------

@Composable
private fun HomeDisconnectedContent(
    state: HomeUiState.Disconnected,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
    showNotificationBanner: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onDismissNotificationBanner: () -> Unit = {},
) {
    HomeScaffold(modifier = modifier) {
        if (state.server == null) {
            HomeEmptyContent(
                onIntent = onIntent,
                showNotificationBanner = showNotificationBanner,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onDismissNotificationBanner = onDismissNotificationBanner,
            )
        } else {
            HomeDisconnectedWithServerContent(
                server = state.server,
                onIntent = onIntent,
                showNotificationBanner = showNotificationBanner,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onDismissNotificationBanner = onDismissNotificationBanner,
            )
        }
    }
}

@Composable
private fun HomeEmptyContent(
    onIntent: (HomeIntent) -> Unit,
    showNotificationBanner: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onDismissNotificationBanner: () -> Unit = {},
) {
    val contentLabel = stringResource(R.string.home_button_content_description)
    val stateLabel = stringResource(R.string.home_button_state_disconnected)

    Spacer(modifier = Modifier.height(ButtonTopSpacingEmpty))

    ConnectionButton(
        state = ConnectionButtonState.Disconnected,
        onClick = { onIntent(HomeIntent.Connect) },
        contentLabel = contentLabel,
        stateLabel = stateLabel,
    )

    Spacer(modifier = Modifier.height(HomeSectionSpacing))

    StatusIndicator(
        title = stringResource(R.string.home_status_disconnected),
        subtitle = stringResource(R.string.home_status_no_server),
    )

    Spacer(modifier = Modifier.height(ButtonBlockTopSpacing))

    HomeEmptyPromptCard()

    Spacer(modifier = Modifier.height(ButtonBlockTopSpacing))

    VPNisButton(
        text = stringResource(R.string.home_action_connect),
        onClick = { onIntent(HomeIntent.Connect) },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(HomeButtonSectionSpacing))

    VPNisOutlinedButton(
        text = stringResource(R.string.home_action_add_server),
        onClick = { onIntent(HomeIntent.AddServer) },
        modifier = Modifier.fillMaxWidth(),
    )

    // Notification section is placed BELOW the primary onboarding CTAs — the user's primary
    // task on a no-server screen is adding a server, not fixing notifications (plan T-3, ux §2).
    if (showNotificationBanner) {
        Spacer(modifier = Modifier.height(HomeSectionSpacing))

        NotificationsSection(
            onOpenSettings = onOpenNotificationSettings,
            onDismiss = onDismissNotificationBanner,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HomeEmptyPromptCard() {
    Surface(
        shape = RoundedCornerShape(EmptyCardCornerRadius),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(EmptyCardInternalPadding),
        ) {
            // Leading icon in a surface container to match the canvas
            Surface(
                shape = RoundedCornerShape(EmptyCardIconContainerRadius),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(EmptyCardIconContainerSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HomeIcons.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(EmptyCardIconSize),
                    )
                }
            }

            Spacer(modifier = Modifier.width(EmptyCardIconTextGap))

            Column {
                Text(
                    text = stringResource(R.string.home_empty_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.home_empty_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun HomeDisconnectedWithServerContent(
    server: Server,
    onIntent: (HomeIntent) -> Unit,
    showNotificationBanner: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onDismissNotificationBanner: () -> Unit = {},
) {
    val contentLabel = stringResource(R.string.home_button_content_description)
    val stateLabel = stringResource(R.string.home_button_state_disconnected)
    val protocol = stringResource(R.string.home_server_protocol_vless_reality)
    val downloadLabel = stringResource(R.string.home_traffic_download)
    val uploadLabel = stringResource(R.string.home_traffic_upload)
    val placeholder = stringResource(R.string.home_value_placeholder)

    Spacer(modifier = Modifier.height(ButtonTopSpacing))

    ConnectionButton(
        state = ConnectionButtonState.Disconnected,
        onClick = { onIntent(HomeIntent.Connect) },
        contentLabel = contentLabel,
        stateLabel = stateLabel,
    )

    Spacer(modifier = Modifier.height(HomeSectionSpacing))

    StatusIndicator(
        title = stringResource(R.string.home_status_disconnected),
        subtitle = null,
    )

    // Notification section sits after StatusIndicator, before ServerCard — a proactive
    // recovery prompt on the resting home screen (plan T-3, ux anchor §2).
    if (showNotificationBanner) {
        Spacer(modifier = Modifier.height(HomeSectionSpacing))

        NotificationsSection(
            onOpenSettings = onOpenNotificationSettings,
            onDismiss = onDismissNotificationBanner,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(modifier = Modifier.height(ButtonBlockTopSpacing))

    ServerCard(
        name = server.name,
        subtitle = protocol,
        pingText = null,
        onClick = { onIntent(HomeIntent.AddServer) },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(HomeSectionSpacing))

    TrafficStats(
        downloadLabel = downloadLabel,
        uploadLabel = uploadLabel,
        downloadValue = placeholder,
        uploadValue = placeholder,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------------------
// Connecting state
// ---------------------------------------------------------------------------

@Composable
private fun HomeConnectingContent(
    state: HomeUiState.Connecting,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeScaffold(modifier = modifier) {
        val contentLabel = stringResource(R.string.home_button_content_description)
        val stateLabel = stringResource(R.string.home_button_state_connecting)
        val connectingTitle = stringResource(R.string.home_status_connecting)
        val protocol = stringResource(R.string.home_server_protocol_vless_reality)
        val downloadLabel = stringResource(R.string.home_traffic_download)
        val uploadLabel = stringResource(R.string.home_traffic_upload)
        val placeholder = stringResource(R.string.home_value_placeholder)

        Spacer(modifier = Modifier.height(ButtonTopSpacing))

        ConnectionButton(
            state = ConnectionButtonState.Connecting,
            onClick = { onIntent(HomeIntent.Cancel) },
            contentLabel = contentLabel,
            stateLabel = stateLabel,
        )

        Spacer(modifier = Modifier.height(HomeSectionSpacing))

        StatusIndicator(
            title = connectingTitle,
            subtitle = stringResource(R.string.home_status_connecting_detail),
            isLive = true,
        )

        Spacer(modifier = Modifier.height(ButtonBlockTopSpacing))

        ServerCard(
            name = state.server.name,
            subtitle = protocol,
            showSpinner = true,
            statusDescription = connectingTitle,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(HomeSectionSpacing))

        TrafficStats(
            downloadLabel = downloadLabel,
            uploadLabel = uploadLabel,
            downloadValue = placeholder,
            uploadValue = placeholder,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(HomeSectionSpacing))

        VPNisOutlinedButton(
            text = stringResource(R.string.home_action_cancel),
            onClick = { onIntent(HomeIntent.Cancel) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Connected state
// ---------------------------------------------------------------------------

@Composable
private fun HomeConnectedContent(
    state: HomeUiState.Connected,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
    showNotificationBanner: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onDismissNotificationBanner: () -> Unit = {},
) {
    HomeScaffold(modifier = modifier) {
        val contentLabel = stringResource(R.string.home_button_content_description)
        val stateLabel = stringResource(R.string.home_button_state_connected)
        val protocol = stringResource(R.string.home_server_protocol_vless_reality)
        val downloadLabel = stringResource(R.string.home_traffic_download)
        val uploadLabel = stringResource(R.string.home_traffic_upload)
        val placeholder = stringResource(R.string.home_value_placeholder)
        val unitBps = stringResource(R.string.home_traffic_unit_bps)
        val unitKbps = stringResource(R.string.home_traffic_unit_kbps)
        val unitMbps = stringResource(R.string.home_traffic_unit_mbps)

        fun unitLabel(unit: BitrateUnit): String = when (unit) {
            BitrateUnit.BYTES -> unitBps
            BitrateUnit.KILOBYTES -> unitKbps
            BitrateUnit.MEGABYTES -> unitMbps
        }

        val download = state.traffic?.let { formatBitrate(it.rxBps) }
        val upload = state.traffic?.let { formatBitrate(it.txBps) }

        Spacer(modifier = Modifier.height(ButtonTopSpacing))

        ConnectionButton(
            state = ConnectionButtonState.Connected,
            onClick = { onIntent(HomeIntent.Disconnect) },
            contentLabel = contentLabel,
            stateLabel = stateLabel,
        )

        Spacer(modifier = Modifier.height(HomeSectionSpacing))

        StatusIndicator(
            title = stringResource(R.string.home_status_connected),
            subtitle = null,
        )

        Spacer(modifier = Modifier.height(StatusToTimerSpacing))

        // Session timer sits directly under the status title as the "subtitle" line.
        // Pass the raw i18n template (with its %1$s placeholder intact) — SessionTimer
        // substitutes the live elapsed time into the a11y label on every tick, so TalkBack
        // reads the current duration rather than a value frozen at first composition.
        SessionTimer(
            since = state.since,
            contentDescriptionTemplate = stringResource(
                R.string.home_session_duration_content_description,
            ),
        )

        Spacer(modifier = Modifier.height(ButtonBlockTopSpacing))

        if (showNotificationBanner) {
            NotificationsSection(
                onOpenSettings = onOpenNotificationSettings,
                onDismiss = onDismissNotificationBanner,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(HomeSectionSpacing))
        }

        ServerCard(
            name = state.server.name,
            subtitle = protocol,
            pingText = null,
            pingEmphasis = PingEmphasis.Good,
            onClick = { onIntent(HomeIntent.AddServer) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(HomeSectionSpacing))

        TrafficStats(
            downloadLabel = downloadLabel,
            uploadLabel = uploadLabel,
            downloadValue = download?.value ?: placeholder,
            downloadUnit = download?.let { unitLabel(it.unit) },
            uploadValue = upload?.value ?: placeholder,
            uploadUnit = upload?.let { unitLabel(it.unit) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Notifications section (reused in Connected + both Disconnected sub-branches)
// ---------------------------------------------------------------------------

/**
 * Stateless notification-permission rationale section (#131).
 *
 * A feature-level wrapper around [VPNisBanner] that owns the copy, icon, CTA, dismiss slot,
 * and the `liveRegion = Polite` a11y annotation. Intentionally kept `internal` to
 * `:feature:home` — it is NOT promoted to `:design:uikit` (plan DM-3, arch review Issue 4).
 *
 * Callers gate rendering with [shouldShowNotificationsSection]; this composable is purely
 * presentational and has no visibility logic of its own.
 *
 * @param onOpenSettings Invoked when the user taps the "Open settings" CTA.
 * @param onDismiss      Invoked when the user dismisses the banner.
 * @param modifier       Optional [Modifier] applied to the [VPNisBanner].
 */
@Composable
internal fun NotificationsSection(onOpenSettings: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    VPNisBanner(
        text = stringResource(R.string.home_notification_banner_text),
        variant = VPNisBannerVariant.Info,
        icon = Icons.Filled.Info,
        primaryAction = VPNisBannerAction(
            label = stringResource(R.string.home_notification_banner_button),
            onClick = onOpenSettings,
        ),
        onDismiss = onDismiss,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

// ---------------------------------------------------------------------------
// Error state
// ---------------------------------------------------------------------------

@Composable
private fun HomeErrorContent(state: HomeUiState.Error, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    HomeScaffold(modifier = modifier) {
        val contentLabel = stringResource(R.string.home_button_content_description)
        val stateLabel = stringResource(R.string.home_button_state_error)
        val serverHost = state.server?.name ?: ""

        Spacer(modifier = Modifier.height(ButtonTopSpacing))

        ConnectionButton(
            state = ConnectionButtonState.Error,
            onClick = { onIntent(HomeIntent.Retry) },
            contentLabel = contentLabel,
            stateLabel = stateLabel,
        )

        Spacer(modifier = Modifier.height(HomeSectionSpacing))

        StatusIndicator(
            title = stringResource(R.string.home_status_error),
            subtitle = stringResource(R.string.home_status_error_detail),
            titleColor = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(ButtonBlockTopSpacing))

        VPNisBanner(
            text = stringResource(R.string.home_error_message, serverHost),
            variant = VPNisBannerVariant.Error,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(HomeSectionSpacing))

        VPNisButton(
            text = stringResource(R.string.home_action_retry),
            onClick = { onIntent(HomeIntent.Retry) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(HomeButtonSectionSpacing))

        VPNisOutlinedButton(
            text = stringResource(R.string.home_action_choose_another),
            onClick = { onIntent(HomeIntent.AddServer) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
