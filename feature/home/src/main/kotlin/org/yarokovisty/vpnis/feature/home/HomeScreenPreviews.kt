package org.yarokovisty.vpnis.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
import org.yarokovisty.vpnis.design.theme.VPNisTheme
import java.time.Instant

// ---------------------------------------------------------------------------
// Shared preview fixtures (internal so HomeScreenPreviewsConnected can reuse)
// ---------------------------------------------------------------------------

internal val PreviewServer = Server(
    id = ServerId("preview-1"),
    name = "Netherlands · Amsterdam",
    config = "vless://preview",
)

// ~12 m 34 s of session time for the Connected previews.
internal val PreviewSince: Instant = Instant.now().minusSeconds(754L)

internal val PreviewTraffic = TrafficStats(
    rxBytes = 1_234_567L,
    txBytes = 345_678L,
    rxBps = 1_234_000L,
    txBps = 256_000L,
)

// ---------------------------------------------------------------------------
// Loading
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun HomeScreenLoadingLightPreview() {
    VPNisTheme(darkTheme = false) {
        HomeScreen(uiState = HomeUiState.Loading, onIntent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenLoadingDarkPreview() {
    VPNisTheme(darkTheme = true) {
        HomeScreen(uiState = HomeUiState.Loading, onIntent = {})
    }
}

// ---------------------------------------------------------------------------
// Disconnected — empty (no server)
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun HomeScreenDisconnectedEmptyLightPreview() {
    VPNisTheme(darkTheme = false) {
        HomeScreen(uiState = HomeUiState.Disconnected(server = null), onIntent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDisconnectedEmptyDarkPreview() {
    VPNisTheme(darkTheme = true) {
        HomeScreen(uiState = HomeUiState.Disconnected(server = null), onIntent = {})
    }
}

// ---------------------------------------------------------------------------
// Disconnected — with server
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun HomeScreenDisconnectedWithServerLightPreview() {
    VPNisTheme(darkTheme = false) {
        HomeScreen(uiState = HomeUiState.Disconnected(server = PreviewServer), onIntent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDisconnectedWithServerDarkPreview() {
    VPNisTheme(darkTheme = true) {
        HomeScreen(uiState = HomeUiState.Disconnected(server = PreviewServer), onIntent = {})
    }
}

// ---------------------------------------------------------------------------
// Connecting
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun HomeScreenConnectingLightPreview() {
    VPNisTheme(darkTheme = false) {
        HomeScreen(uiState = HomeUiState.Connecting(server = PreviewServer), onIntent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenConnectingDarkPreview() {
    VPNisTheme(darkTheme = true) {
        HomeScreen(uiState = HomeUiState.Connecting(server = PreviewServer), onIntent = {})
    }
}

// ---------------------------------------------------------------------------
// Disconnected — with server + notification banner (#131)
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun HomeScreenDisconnectedWithServerBannerLightPreview() {
    VPNisTheme(darkTheme = false) {
        HomeScreen(
            uiState = HomeUiState.Disconnected(server = PreviewServer),
            onIntent = {},
            showNotificationBanner = true,
            onOpenNotificationSettings = {},
            onDismissNotificationBanner = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDisconnectedWithServerBannerDarkPreview() {
    VPNisTheme(darkTheme = true) {
        HomeScreen(
            uiState = HomeUiState.Disconnected(server = PreviewServer),
            onIntent = {},
            showNotificationBanner = true,
            onOpenNotificationSettings = {},
            onDismissNotificationBanner = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Disconnected — empty (no server) + notification banner (#131)
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun HomeScreenDisconnectedEmptyBannerLightPreview() {
    VPNisTheme(darkTheme = false) {
        HomeScreen(
            uiState = HomeUiState.Disconnected(server = null),
            onIntent = {},
            showNotificationBanner = true,
            onOpenNotificationSettings = {},
            onDismissNotificationBanner = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDisconnectedEmptyBannerDarkPreview() {
    VPNisTheme(darkTheme = true) {
        HomeScreen(
            uiState = HomeUiState.Disconnected(server = null),
            onIntent = {},
            showNotificationBanner = true,
            onOpenNotificationSettings = {},
            onDismissNotificationBanner = {},
        )
    }
}
