package org.yarokovisty.vpnis.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.uikit.button.VPNisButton
import org.yarokovisty.vpnis.design.uikit.button.VPNisOutlinedButton
import org.yarokovisty.vpnis.design.uikit.button.VPNisTonalButton

/**
 * Showcase section for the button family (`:design:uikit` `button/`).
 *
 * Renders the three button variants in their enabled and disabled states. This
 * is the first section wired into the gallery and doubles as a scaffold check
 * that the showcase renders `:design:uikit` components correctly (issue #27).
 */
@Composable
fun ButtonShowcaseSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VPNisButton(text = "Connect", onClick = {})
            VPNisOutlinedButton(text = "Cancel", onClick = {})
            VPNisTonalButton(text = "Settings", onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VPNisButton(text = "Connect", onClick = {}, enabled = false)
            VPNisOutlinedButton(text = "Cancel", onClick = {}, enabled = false)
            VPNisTonalButton(text = "Settings", onClick = {}, enabled = false)
        }
    }
}
