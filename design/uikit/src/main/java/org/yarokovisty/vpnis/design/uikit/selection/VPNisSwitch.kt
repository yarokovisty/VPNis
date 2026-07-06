package org.yarokovisty.vpnis.design.uikit.selection

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * VPN-branded toggle switch for the VPNis design system.
 *
 * Thin wrapper over M3 [Switch]. Brand tokens are applied automatically via
 * [VPNisTheme] (primary checked-track colour, surface thumb). Prefer
 * [VPNisLabeledSwitch] when a full interactive label row is needed.
 *
 * @param checked Whether the switch is currently on.
 * @param onCheckedChange Callback invoked when the user toggles the switch.
 * @param modifier Optional [Modifier] applied to the switch.
 * @param enabled Whether the switch accepts user interaction.
 * @param thumbContent Optional composable rendered inside the thumb — e.g. a
 *   lock icon when the VPN is active. Size it to `SwitchDefaults.IconSize`.
 */
@Composable
public fun VPNisSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbContent: (@Composable () -> Unit)? = null,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = thumbContent,
    )
}

@Preview(showBackground = true)
@Composable
private fun VPNisSwitchCheckedEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisSwitch(checked = true, onCheckedChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisSwitchUncheckedEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisSwitch(checked = false, onCheckedChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisSwitchCheckedDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisSwitch(checked = true, onCheckedChange = {}, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisSwitchUncheckedDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisSwitch(checked = false, onCheckedChange = {}, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisSwitchCheckedEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisSwitch(checked = true, onCheckedChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisSwitchUncheckedEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisSwitch(checked = false, onCheckedChange = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisSwitchCheckedDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisSwitch(checked = true, onCheckedChange = {}, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisSwitchUncheckedDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisSwitch(checked = false, onCheckedChange = {}, enabled = false)
    }
}
