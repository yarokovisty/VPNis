package org.yarokovisty.vpnis.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import org.yarokovisty.vpnis.R
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Minimal stub screen used for destinations that are not yet implemented
 * (Servers, Bypass, Settings).
 *
 * Displays a centred [title] with the themed headline style and a
 * localised "Coming soon" subtitle in the secondary colour so it is
 * visually distinct and clearly a placeholder.
 */
@Composable
internal fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.placeholder_coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Previews ----------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenLightPreview() {
    VPNisTheme(darkTheme = false) {
        PlaceholderScreen(title = "Серверы")
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenDarkPreview() {
    VPNisTheme(darkTheme = true) {
        PlaceholderScreen(title = "Серверы")
    }
}
