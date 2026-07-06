package org.yarokovisty.vpnis.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.uikit.selection.VPNisCheckbox
import org.yarokovisty.vpnis.design.uikit.selection.VPNisLabeledRadioButton
import org.yarokovisty.vpnis.design.uikit.selection.VPNisLabeledSwitch
import org.yarokovisty.vpnis.design.uikit.selection.VPNisRadioButton
import org.yarokovisty.vpnis.design.uikit.selection.VPNisSwitch
import org.yarokovisty.vpnis.design.uikit.selection.VPNisTriStateCheckbox

/**
 * Showcase section for the selection-controls family (`:design:uikit` `selection/`).
 *
 * Renders every control in its checked/unchecked/indeterminate and enabled/disabled
 * states. Also demonstrates [VPNisLabeledSwitch] with a lock-icon [thumbContent]
 * and a small radio group wired with [Modifier.selectableGroup].
 */
@Composable
fun SelectionShowcaseSection() {
    var switchChecked by remember { mutableStateOf(true) }
    var checkboxChecked by remember { mutableStateOf(true) }
    var triState by remember { mutableStateOf(ToggleableState.Indeterminate) }
    var radioSelected by remember { mutableIntStateOf(0) }
    val protocols = listOf("WireGuard", "Xray", "OpenVPN")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Switch
        Text("Switch", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VPNisSwitch(checked = switchChecked, onCheckedChange = { switchChecked = it })
            VPNisSwitch(checked = false, onCheckedChange = {})
            VPNisSwitch(checked = true, onCheckedChange = {}, enabled = false)
            VPNisSwitch(checked = false, onCheckedChange = {}, enabled = false)
        }

        // Checkbox
        Text("Checkbox", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VPNisCheckbox(checked = checkboxChecked, onCheckedChange = { checkboxChecked = it })
            VPNisCheckbox(checked = false, onCheckedChange = {})
            VPNisCheckbox(checked = true, onCheckedChange = null, enabled = false)
            VPNisCheckbox(checked = false, onCheckedChange = null, enabled = false)
        }

        // TriStateCheckbox
        Text("TriStateCheckbox", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VPNisTriStateCheckbox(state = ToggleableState.On, onClick = {})
            VPNisTriStateCheckbox(
                state = triState,
                onClick = {
                    triState = when (triState) {
                        ToggleableState.On -> ToggleableState.Off
                        ToggleableState.Off -> ToggleableState.Indeterminate
                        ToggleableState.Indeterminate -> ToggleableState.On
                    }
                },
            )
            VPNisTriStateCheckbox(state = ToggleableState.Indeterminate, onClick = null, enabled = false)
        }

        // RadioButton (bare)
        Text("RadioButton", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VPNisRadioButton(selected = true, onClick = {})
            VPNisRadioButton(selected = false, onClick = {})
            VPNisRadioButton(selected = true, onClick = null, enabled = false)
        }

        // LabeledRadioButton — selectableGroup on the parent Column
        Text("LabeledRadioButton (selectableGroup)", style = MaterialTheme.typography.labelMedium)
        Column(modifier = Modifier.selectableGroup()) {
            protocols.forEachIndexed { index, name ->
                VPNisLabeledRadioButton(
                    label = name,
                    selected = radioSelected == index,
                    onClick = { radioSelected = index },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // LabeledSwitch — active with lock thumbContent
        Text("LabeledSwitch", style = MaterialTheme.typography.labelMedium)
        VPNisLabeledSwitch(
            label = "VPN Protection",
            checked = switchChecked,
            onCheckedChange = { switchChecked = it },
            modifier = Modifier.fillMaxWidth(),
            thumbContent = {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
        VPNisLabeledSwitch(
            label = "Kill Switch (disabled)",
            checked = false,
            onCheckedChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
        )
    }
}
