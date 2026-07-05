package org.yarokovisty.vpnis.design.uikit

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Primary action button for the VPNis design system.
 *
 * **Stub until issue #17.** Real VPNis Material 3 token-based styling (colours,
 * shape, typography) will be applied when the DesignSync import lands. The
 * public signature is stable; only the visual appearance will change.
 *
 * @param text Label displayed inside the button.
 * @param onClick Callback invoked when the button is tapped.
 * @param modifier Optional [Modifier] applied to the button.
 * @param enabled Whether the button accepts user interaction.
 */
@Composable
public fun VPNisButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(text = text)
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisButtonEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisButton(
            text = "Connect",
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisButtonDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisButton(
            text = "Connect",
            onClick = {},
            enabled = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisButtonEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisButton(
            text = "Connect",
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisButtonDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisButton(
            text = "Connect",
            onClick = {},
            enabled = false,
        )
    }
}
