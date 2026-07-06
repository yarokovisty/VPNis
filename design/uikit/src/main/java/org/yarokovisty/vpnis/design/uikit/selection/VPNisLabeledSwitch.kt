package org.yarokovisty.vpnis.design.uikit.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Full-row labeled toggle switch for the VPNis design system.
 *
 * Encodes the "label + switch + full-row click" composition used across
 * preference screens. The entire row is the touch target — TalkBack announces
 * a single interactive item. The inner [Switch] is purely visual:
 * `onCheckedChange = null` and [Modifier.clearAndSetSemantics] ensure screen
 * readers do not double-announce. The row carries all semantics.
 *
 * **A11y:** the row uses [Modifier.toggleable] with [Role.Switch] and
 * [Modifier.minimumInteractiveComponentSize] for the 48 dp touch target.
 * Do NOT use `selectableGroup` — a switch is a toggle, not mutually exclusive.
 *
 * @param label Text label displayed to the left of the switch.
 * @param checked Whether the switch is currently on.
 * @param onCheckedChange Callback invoked when the user toggles the row.
 * @param modifier Optional [Modifier] applied to the row (e.g. `fillMaxWidth`).
 * @param enabled Whether the row accepts user interaction.
 * @param thumbContent Optional composable rendered inside the switch thumb — e.g.
 *   a lock icon when the VPN is active. Size it to `SwitchDefaults.IconSize`.
 */
@Composable
public fun VPNisLabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        // onCheckedChange = null + clearAndSetSemantics: the inner Switch is purely
        // visual. The enclosing Row carries the toggleable semantics so TalkBack
        // announces the row once, not twice.
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
            enabled = enabled,
            thumbContent = thumbContent,
        )
    }
}

// --- Previews ---------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledSwitchCheckedEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisLabeledSwitch(
            label = "VPN Protection",
            checked = true,
            onCheckedChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledSwitchUncheckedEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisLabeledSwitch(
            label = "VPN Protection",
            checked = false,
            onCheckedChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledSwitchCheckedDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisLabeledSwitch(
            label = "Kill Switch",
            checked = true,
            onCheckedChange = {},
            enabled = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledSwitchCheckedEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisLabeledSwitch(
            label = "VPN Protection",
            checked = true,
            onCheckedChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledSwitchUncheckedEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisLabeledSwitch(
            label = "VPN Protection",
            checked = false,
            onCheckedChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledSwitchCheckedDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisLabeledSwitch(
            label = "Kill Switch",
            checked = true,
            onCheckedChange = {},
            enabled = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisLabeledSwitchWithThumbContentLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisLabeledSwitch(
            label = "VPN Protection",
            checked = true,
            onCheckedChange = {},
            thumbContent = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.White, CircleShape),
                )
            },
        )
    }
}
