package org.yarokovisty.vpnis.design.uikit.selection

import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * VPN-branded radio button for the VPNis design system.
 *
 * Thin wrapper over M3 [RadioButton]. Brand tokens are applied automatically via
 * [VPNisTheme] (primary selected colour). Use this for a standalone control;
 * prefer [VPNisLabeledRadioButton] for a full interactive label row.
 *
 * **A11y:** radio buttons express a mutually-exclusive group. Wrap a set of
 * [VPNisLabeledRadioButton] items in a `Column` with `Modifier.selectableGroup()`
 * so TalkBack announces the group as a radio group and reads the selection position.
 *
 * @param selected Whether this radio button is selected.
 * @param onClick Callback invoked on tap, or `null` for a read-only control.
 * @param modifier Optional [Modifier] applied to the radio button.
 * @param enabled Whether the radio button accepts user interaction.
 */
@Composable
public fun VPNisRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

@Preview(showBackground = true)
@Composable
private fun VPNisRadioButtonSelectedEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisRadioButton(selected = true, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisRadioButtonUnselectedEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisRadioButton(selected = false, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisRadioButtonSelectedDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisRadioButton(selected = true, onClick = null, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisRadioButtonUnselectedDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisRadioButton(selected = false, onClick = null, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisRadioButtonSelectedEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisRadioButton(selected = true, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisRadioButtonUnselectedEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisRadioButton(selected = false, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisRadioButtonSelectedDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisRadioButton(selected = true, onClick = null, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisRadioButtonUnselectedDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisRadioButton(selected = false, onClick = null, enabled = false)
    }
}
