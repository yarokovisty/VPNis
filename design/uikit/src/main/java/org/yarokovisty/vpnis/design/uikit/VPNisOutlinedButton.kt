package org.yarokovisty.vpnis.design.uikit

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Outlined action button for the VPNis design system.
 *
 * Wraps M3 [OutlinedButton] with the VPNis palette pinned: transparent
 * background, a 1-dp `outline` stroke and a `primary` label (M3 defaults drift
 * to onSurfaceVariant / outlineVariant in newer releases, which is off-brand).
 * Suited for medium-emphasis actions alongside a filled primary button.
 *
 * @param text Label displayed inside the button.
 * @param onClick Callback invoked when the button is tapped.
 * @param modifier Optional [Modifier] applied to the button.
 * @param enabled Whether the button accepts user interaction.
 */
@Composable
public fun VPNisOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        border = if (enabled) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        Text(text = text)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedButtonEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisOutlinedButton(text = "Cancel", onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedButtonDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisOutlinedButton(text = "Cancel", onClick = {}, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedButtonEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisOutlinedButton(text = "Cancel", onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedButtonDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisOutlinedButton(text = "Cancel", onClick = {}, enabled = false)
    }
}
