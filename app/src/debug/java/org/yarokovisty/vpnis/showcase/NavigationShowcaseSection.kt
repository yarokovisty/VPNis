package org.yarokovisty.vpnis.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.uikit.navigation.VPNisNavigationBar
import org.yarokovisty.vpnis.design.uikit.navigation.VPNisNavigationBarItem
import org.yarokovisty.vpnis.design.uikit.navigation.VPNisNavigationRail
import org.yarokovisty.vpnis.design.uikit.navigation.VPNisNavigationRailItem
import org.yarokovisty.vpnis.design.uikit.navigation.VPNisPrimaryTabRow
import org.yarokovisty.vpnis.design.uikit.navigation.VPNisSecondaryTabRow
import org.yarokovisty.vpnis.design.uikit.navigation.VPNisTab

private data class NavItemSpec(val key: String, val label: String, val icon: ImageVector, val badgeCount: Int = 0)

private val navItems = listOf(
    NavItemSpec(key = "connection", label = "VPN", icon = Icons.Filled.Lock),
    NavItemSpec(key = "settings", label = "Settings", icon = Icons.Filled.Settings),
    NavItemSpec(key = "account", label = "Account", icon = Icons.Filled.Person),
    NavItemSpec(key = "alerts", label = "Alerts", icon = Icons.Filled.Info, badgeCount = 3),
)

private val tabLabels = listOf("Protocol", "Logs", "Stats")

/**
 * Showcase section for the navigation & tabs family (`:design:uikit` `navigation/`).
 *
 * Demonstrates key-based selection for [VPNisNavigationBar] and
 * [VPNisNavigationRail], and index-based selection for [VPNisPrimaryTabRow] and
 * [VPNisSecondaryTabRow].  The fourth nav item shows the badge pattern via
 * [BadgedBox] — badge attachment is caller-owned via the icon slot.
 */
@Composable
fun NavigationShowcaseSection() {
    var selectedNavKey by remember { mutableStateOf(navItems.first().key) }
    var selectedRailKey by remember { mutableStateOf(navItems.first().key) }
    var selectedPrimaryTab by remember { mutableIntStateOf(0) }
    var selectedSecondaryTab by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- NavigationBar --------------------------------------------------

        Text(text = "NavigationBar (key-based selection)", style = MaterialTheme.typography.labelMedium)
        VPNisNavigationBar(
            // Remove system insets so the bar fits inline in the showcase scroll list.
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            navItems.forEach { item ->
                VPNisNavigationBarItem(
                    selected = item.key == selectedNavKey,
                    onClick = { selectedNavKey = item.key },
                    icon = {
                        if (item.badgeCount > 0) {
                            // Badge pattern: caller wraps icon in BadgedBox.
                            // contentDescription must convey both the label and the count.
                            BadgedBox(
                                badge = { Badge { Text(item.badgeCount.toString()) } },
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = "${item.label}, ${item.badgeCount} new",
                                )
                            }
                        } else {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                            )
                        }
                    },
                    label = { Text(item.label) },
                )
            }
        }

        // --- NavigationRail -------------------------------------------------

        Text(text = "NavigationRail (key-based selection)", style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.height(220.dp)) {
            VPNisNavigationRail {
                navItems.take(3).forEach { item ->
                    VPNisNavigationRailItem(
                        selected = item.key == selectedRailKey,
                        onClick = { selectedRailKey = item.key },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Active: $selectedRailKey",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // --- PrimaryTabRow --------------------------------------------------

        Text(text = "PrimaryTabRow", style = MaterialTheme.typography.labelMedium)
        VPNisPrimaryTabRow(selectedTabIndex = selectedPrimaryTab) {
            tabLabels.forEachIndexed { index, label ->
                VPNisTab(
                    selected = index == selectedPrimaryTab,
                    onClick = { selectedPrimaryTab = index },
                    text = { Text(label) },
                )
            }
        }

        // --- SecondaryTabRow ------------------------------------------------

        Text(text = "SecondaryTabRow", style = MaterialTheme.typography.labelMedium)
        VPNisSecondaryTabRow(selectedTabIndex = selectedSecondaryTab) {
            tabLabels.forEachIndexed { index, label ->
                VPNisTab(
                    selected = index == selectedSecondaryTab,
                    onClick = { selectedSecondaryTab = index },
                    text = { Text(label) },
                )
            }
        }
    }
}
