package org.yarokovisty.vpnis.showcase

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.uikit.list.VPNisListItem
import org.yarokovisty.vpnis.design.uikit.selection.VPNisSwitch

/**
 * Showcase section for the list-item family (`:design:uikit` `list/`).
 *
 * Demonstrates [VPNisListItem] in its key configurations:
 * - Headline-only (minimal slot usage)
 * - Headline + supporting text
 * - Headline + supporting + leading icon + trailing arrow (non-interactive trailing)
 * - Headline + supporting + leading icon + trailing [VPNisSwitch] (interactive trailing)
 * - Rows wrapped in [Modifier.clickable], illustrating that click behaviour is
 *   applied via the modifier param — not an onClick parameter.
 *
 * [HorizontalDivider]s between items mirror a realistic list appearance.
 */
@Composable
fun ListShowcaseSection() {
    var killSwitchEnabled by remember { mutableStateOf(false) }
    var splitTunnelEnabled by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // --- Headline only --------------------------------------------------
        Text(
            text = "Headline only",
            style = MaterialTheme.typography.labelMedium,
        )
        VPNisListItem(
            headlineContent = { Text("Auto-connect") },
        )
        HorizontalDivider()

        // --- Headline + supporting text -------------------------------------
        Text(
            text = "Headline + supporting text",
            style = MaterialTheme.typography.labelMedium,
        )
        VPNisListItem(
            headlineContent = { Text("Amsterdam, Netherlands") },
            supportingContent = { Text("Latency: 12 ms · 98% uptime") },
        )
        HorizontalDivider()

        // --- Leading icon + trailing arrow (navigable row) ------------------
        Text(
            text = "Leading icon + trailing arrow",
            style = MaterialTheme.typography.labelMedium,
        )
        VPNisListItem(
            headlineContent = { Text("Account") },
            supportingContent = { Text("alice@vpnis.io") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                )
            },
        )
        HorizontalDivider()

        // --- Leading icon + trailing Switch (Kill switch) -------------------
        Text(
            text = "Leading icon + trailing Switch",
            style = MaterialTheme.typography.labelMedium,
        )
        VPNisListItem(
            headlineContent = { Text("Kill switch") },
            supportingContent = { Text("Block traffic if VPN drops") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                )
            },
            trailingContent = {
                VPNisSwitch(
                    checked = killSwitchEnabled,
                    onCheckedChange = { killSwitchEnabled = it },
                )
            },
        )
        HorizontalDivider()

        VPNisListItem(
            headlineContent = { Text("Split tunnelling") },
            supportingContent = { Text("Route only VPN traffic through tunnel") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                )
            },
            trailingContent = {
                VPNisSwitch(
                    checked = splitTunnelEnabled,
                    onCheckedChange = { splitTunnelEnabled = it },
                )
            },
        )
        HorizontalDivider()

        // --- Clickable rows — demonstrates Modifier.clickable pattern -------
        Text(
            text = "Clickable rows (Modifier.clickable, no onClick param)",
            style = MaterialTheme.typography.labelMedium,
        )
        VPNisListItem(
            headlineContent = { Text("Connection protocol") },
            supportingContent = { Text("WireGuard (recommended)") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                )
            },
            // VPNisListItem has no onClick param — click is applied via modifier.
            modifier = Modifier.clickable { /* navigate to protocol picker */ },
        )
        HorizontalDivider()

        VPNisListItem(
            headlineContent = { Text("DNS settings") },
            supportingContent = { Text("1.1.1.1 (Cloudflare)") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                )
            },
            modifier = Modifier.clickable { /* navigate to DNS settings */ },
        )
    }
}
