package org.yarokovisty.vpnis.design.uikit.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Full-row labeled radio button for the VPNis design system.
 *
 * Encodes the "radio button + label + full-row click" composition used in
 * mutually-exclusive option lists. The entire row is the touch target — TalkBack
 * announces a single interactive item. The inner [RadioButton] is purely visual:
 * `onClick = null` and [Modifier.clearAndSetSemantics] ensure screen readers do
 * not double-announce. The row carries all semantics.
 *
 * **A11y:** place items inside a `Column` with `Modifier.selectableGroup()` so
 * TalkBack understands the group is mutually exclusive and announces position
 * and count correctly. Each row uses [Modifier.selectable] with
 * [Role.RadioButton] and [Modifier.minimumInteractiveComponentSize] for the
 * 48 dp touch target. The caller is responsible for `selectableGroup()`.
 *
 * Example:
 * ```kotlin
 * Column(modifier = Modifier.selectableGroup()) {
 *     protocols.forEachIndexed { index, label ->
 *         VPNisLabeledRadioButton(
 *             label = label,
 *             selected = selectedIndex == index,
 *             onClick = { selectedIndex = index },
 *             modifier = Modifier.fillMaxWidth(),
 *         )
 *     }
 * }
 * ```
 *
 * @param label Text label displayed to the right of the radio button.
 * @param selected Whether this option is currently selected.
 * @param onClick Callback invoked when the user taps the row.
 * @param modifier Optional [Modifier] applied to the row (e.g. `fillMaxWidth`).
 * @param enabled Whether the row accepts user interaction.
 */
@Composable
public fun VPNisLabeledRadioButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // onClick = null + clearAndSetSemantics: the inner RadioButton is purely
        // visual. The enclosing Row carries the selectable semantics so TalkBack
        // announces the row once, not twice.
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics {},
            enabled = enabled,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// --- Previews ---------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledRadioButtonSelectedEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisLabeledRadioButton(label = "WireGuard", selected = true, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledRadioButtonUnselectedEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisLabeledRadioButton(label = "Xray", selected = false, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledRadioButtonSelectedDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisLabeledRadioButton(label = "OpenVPN", selected = true, onClick = {}, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledRadioButtonSelectedEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisLabeledRadioButton(label = "WireGuard", selected = true, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledRadioButtonUnselectedEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisLabeledRadioButton(label = "Xray", selected = false, onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledRadioButtonSelectedDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisLabeledRadioButton(label = "OpenVPN", selected = true, onClick = {}, enabled = false)
    }
}
