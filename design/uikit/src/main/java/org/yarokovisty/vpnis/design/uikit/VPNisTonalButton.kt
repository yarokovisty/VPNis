package org.yarokovisty.vpnis.design.uikit

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Filled tonal action button for the VPNis design system.
 *
 * Wraps M3 [FilledTonalButton] with the VPNis palette pinned to
 * primaryContainer / onPrimaryContainer (M3 defaults a tonal button to
 * secondaryContainer, which is off-brand here). Suited for secondary actions
 * that need less visual weight than [VPNisButton].
 *
 * @param text Label displayed inside the button.
 * @param onClick Callback invoked when the button is tapped.
 * @param modifier Optional [Modifier] applied to the button.
 * @param enabled Whether the button accepts user interaction.
 */
@Composable
public fun VPNisTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(text = text)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisTonalButtonEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisTonalButton(text = "Settings", onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisTonalButtonDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisTonalButton(text = "Settings", onClick = {}, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisTonalButtonEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisTonalButton(text = "Settings", onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisTonalButtonDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisTonalButton(text = "Settings", onClick = {}, enabled = false)
    }
}
