package org.yarokovisty.vpnis.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.design.theme.VPNisTheme

// ---------------------------------------------------------------------------
// Connected — with traffic
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun HomeScreenConnectedLightPreview() {
    VPNisTheme(darkTheme = false) {
        HomeScreen(
            uiState = HomeUiState.Connected(
                server = PreviewServer,
                since = PreviewSince,
                traffic = PreviewTraffic,
            ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenConnectedDarkPreview() {
    VPNisTheme(darkTheme = true) {
        HomeScreen(
            uiState = HomeUiState.Connected(
                server = PreviewServer,
                since = PreviewSince,
                traffic = PreviewTraffic,
            ),
            onIntent = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Connected — no traffic yet (null)
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun HomeScreenConnectedNoTrafficLightPreview() {
    VPNisTheme(darkTheme = false) {
        HomeScreen(
            uiState = HomeUiState.Connected(
                server = PreviewServer,
                since = PreviewSince,
                traffic = null,
            ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenConnectedNoTrafficDarkPreview() {
    VPNisTheme(darkTheme = true) {
        HomeScreen(
            uiState = HomeUiState.Connected(
                server = PreviewServer,
                since = PreviewSince,
                traffic = null,
            ),
            onIntent = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Error — with server
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun HomeScreenErrorWithServerLightPreview() {
    VPNisTheme(darkTheme = false) {
        HomeScreen(
            uiState = HomeUiState.Error(
                reason = ConnectionError.ServerUnreachable,
                server = PreviewServer,
            ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenErrorWithServerDarkPreview() {
    VPNisTheme(darkTheme = true) {
        HomeScreen(
            uiState = HomeUiState.Error(
                reason = ConnectionError.TunnelSetupFailed,
                server = PreviewServer,
            ),
            onIntent = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Error — no server
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun HomeScreenErrorNoServerLightPreview() {
    VPNisTheme(darkTheme = false) {
        HomeScreen(
            uiState = HomeUiState.Error(
                reason = ConnectionError.PermissionDenied,
                server = null,
            ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenErrorNoServerDarkPreview() {
    VPNisTheme(darkTheme = true) {
        HomeScreen(
            uiState = HomeUiState.Error(
                reason = ConnectionError.Unknown(message = "Unexpected failure"),
                server = null,
            ),
            onIntent = {},
        )
    }
}
