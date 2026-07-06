package org.yarokovisty.vpnis.design.uikit.selection

import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * VPN-branded tri-state checkbox for the VPNis design system.
 *
 * Thin wrapper over M3 [TriStateCheckbox]. Brand tokens are applied automatically
 * via [VPNisTheme]. Use this when a parent toggle must reflect a mixed / partial
 * selection across a group of children.
 *
 * @param state Current toggle state: [ToggleableState.On], [ToggleableState.Off],
 *   or [ToggleableState.Indeterminate].
 * @param onClick Callback invoked on tap, or `null` for a read-only control.
 * @param modifier Optional [Modifier] applied to the checkbox.
 * @param enabled Whether the checkbox accepts user interaction.
 */
@Composable
public fun VPNisTriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TriStateCheckbox(
        state = state,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

@Preview(showBackground = true)
@Composable
private fun VPNisTriStateCheckboxOnEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisTriStateCheckbox(state = ToggleableState.On, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisTriStateCheckboxIndeterminateEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisTriStateCheckbox(state = ToggleableState.Indeterminate, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisTriStateCheckboxOffEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisTriStateCheckbox(state = ToggleableState.Off, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisTriStateCheckboxOnDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisTriStateCheckbox(state = ToggleableState.On, onClick = null, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisTriStateCheckboxOnEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisTriStateCheckbox(state = ToggleableState.On, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisTriStateCheckboxIndeterminateEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisTriStateCheckbox(state = ToggleableState.Indeterminate, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisTriStateCheckboxOffEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisTriStateCheckbox(state = ToggleableState.Off, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisTriStateCheckboxOnDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisTriStateCheckbox(state = ToggleableState.On, onClick = null, enabled = false)
    }
}
