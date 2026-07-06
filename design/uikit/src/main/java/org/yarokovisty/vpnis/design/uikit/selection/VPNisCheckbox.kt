package org.yarokovisty.vpnis.design.uikit.selection

import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * VPN-branded checkbox for the VPNis design system.
 *
 * Thin wrapper over M3 [Checkbox]. Brand tokens are applied automatically via
 * [VPNisTheme] (primary checked colour, outline unchecked border). Suitable for
 * multi-select option lists.
 *
 * @param checked Whether the checkbox is ticked.
 * @param onCheckedChange Callback invoked on tap, or `null` for a read-only control.
 * @param modifier Optional [Modifier] applied to the checkbox.
 * @param enabled Whether the checkbox accepts user interaction.
 */
@Composable
public fun VPNisCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

@Preview(showBackground = true)
@Composable
private fun VPNisCheckboxCheckedEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisCheckbox(checked = true, onCheckedChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCheckboxUncheckedEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisCheckbox(checked = false, onCheckedChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCheckboxCheckedDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisCheckbox(checked = true, onCheckedChange = null, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCheckboxUncheckedDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisCheckbox(checked = false, onCheckedChange = null, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCheckboxCheckedEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisCheckbox(checked = true, onCheckedChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCheckboxUncheckedEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisCheckbox(checked = false, onCheckedChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCheckboxCheckedDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisCheckbox(checked = true, onCheckedChange = null, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCheckboxUncheckedDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisCheckbox(checked = false, onCheckedChange = null, enabled = false)
    }
}
