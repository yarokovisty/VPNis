package org.yarokovisty.vpnis.design.uikit.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Navigation rail for the VPNis design system, suited to medium-width layouts.
 *
 * Wraps M3 [NavigationRail] and pins the container colour to
 * [MaterialTheme.colorScheme.surface] for consistency with [VPNisNavigationBar].
 *
 * The optional [header] slot (e.g. a FAB or logo mark) is placed at the top
 * of the rail before the item list.  Items are emitted via the [content] slot
 * using [VPNisNavigationRailItem]; the caller owns the descriptor list and
 * computes `selected` by key/identity comparison:
 *
 * ```kotlin
 * VPNisNavigationRail {
 *     items.forEach { item ->
 *         VPNisNavigationRailItem(
 *             selected = item.key == selectedKey,
 *             onClick  = { selectedKey = item.key },
 *             icon     = { Icon(item.icon, contentDescription = item.label) },
 *             label    = { Text(item.label) },
 *         )
 *     }
 * }
 * ```
 *
 * **Badge:** wrap the `icon` slot of [VPNisNavigationRailItem] in M3's
 * `BadgedBox` — the same pattern as [VPNisNavigationBarItem].
 *
 * @param modifier Optional [Modifier] applied to the rail.
 * @param header Optional composable placed at the top of the rail column (e.g. a FAB).
 * @param content Column slot; emit one [VPNisNavigationRailItem] per destination.
 */
@Composable
public fun VPNisNavigationRail(
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        header = header,
        content = content,
    )
}

/**
 * Navigation rail item for use inside [VPNisNavigationRail].
 *
 * Wraps M3 [NavigationRailItem] and adds a [selectedIcon] slot mirroring the
 * pattern established in [VPNisNavigationBarItem].  The [ColumnScope] receiver
 * restricts usage to composables that already operate inside a column, enforcing
 * correct placement within [VPNisNavigationRail]'s `content` lambda.
 *
 * **Active-label colour:** relies on M3 1.4.x defaults via [VPNisTheme] — same
 * rationale as [VPNisNavigationBarItem].
 *
 * **Badge:** wrap the [icon] slot in M3's `BadgedBox`. When a badge is present
 * the icon **must** carry a `contentDescription` that conveys the count context
 * (e.g. `"Notifications, 2 unread"`).
 *
 * @param selected Whether this destination is currently active.
 * @param onClick Called when the item is tapped.
 * @param icon Composable shown when the item is not selected.
 * @param modifier Optional [Modifier] applied to the item.
 * @param selectedIcon Composable shown when selected; defaults to [icon].
 * @param enabled Whether the item accepts user interaction.
 * @param label Optional text label.
 * @param alwaysShowLabel Whether the label is visible even when unselected.
 */
@Composable
public fun ColumnScope.VPNisNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selectedIcon: @Composable () -> Unit = icon,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = if (selected) selectedIcon else icon,
        modifier = modifier,
        enabled = enabled,
        label = label,
        alwaysShowLabel = alwaysShowLabel,
    )
}

// --- Preview helpers --------------------------------------------------------

@Composable
private fun PreviewRailCircle(active: Boolean = false) {
    val color = if (active) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color = color, radius = size.minDimension * 0.4f)
    }
}

// --- Previews — Light -------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisNavigationRailSelectedLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisNavigationRail {
            VPNisNavigationRailItem(
                selected = true,
                onClick = {},
                icon = { PreviewRailCircle() },
                selectedIcon = { PreviewRailCircle(active = true) },
                label = { Text("VPN", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisNavigationRailItem(
                selected = false,
                onClick = {},
                icon = { PreviewRailCircle() },
                label = { Text("Settings", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisNavigationRailItem(
                selected = false,
                onClick = {},
                icon = { PreviewRailCircle() },
                label = { Text("Account", style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisNavigationRailWithBadgeLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisNavigationRail {
            VPNisNavigationRailItem(
                selected = true,
                onClick = {},
                icon = { PreviewRailCircle(active = true) },
                label = { Text("VPN") },
            )
            VPNisNavigationRailItem(
                selected = false,
                onClick = {},
                icon = { PreviewRailCircle() },
                label = { Text("Settings") },
            )
            // Item with badge — caller wraps icon in BadgedBox
            VPNisNavigationRailItem(
                selected = false,
                onClick = {},
                icon = {
                    BadgedBox(
                        badge = { Badge { Text("2") } },
                    ) {
                        PreviewRailCircle()
                    }
                },
                label = { Text("Alerts") },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisNavigationRailAlwaysShowLabelFalseLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisNavigationRail {
            VPNisNavigationRailItem(
                selected = true,
                onClick = {},
                icon = { PreviewRailCircle(active = true) },
                label = { Text("VPN") },
                alwaysShowLabel = false,
            )
            VPNisNavigationRailItem(
                selected = false,
                onClick = {},
                icon = { PreviewRailCircle() },
                label = { Text("Settings") },
                alwaysShowLabel = false,
            )
            VPNisNavigationRailItem(
                selected = false,
                onClick = {},
                icon = { PreviewRailCircle() },
                label = { Text("Account") },
                alwaysShowLabel = false,
            )
        }
    }
}

// --- Previews — Dark -------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisNavigationRailSelectedDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisNavigationRail {
            VPNisNavigationRailItem(
                selected = false,
                onClick = {},
                icon = { PreviewRailCircle() },
                label = { Text("VPN", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisNavigationRailItem(
                selected = true,
                onClick = {},
                icon = { PreviewRailCircle() },
                selectedIcon = { PreviewRailCircle(active = true) },
                label = { Text("Settings", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisNavigationRailItem(
                selected = false,
                onClick = {},
                icon = { PreviewRailCircle() },
                label = { Text("Account", style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisNavigationRailWithBadgeDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisNavigationRail {
            VPNisNavigationRailItem(
                selected = false,
                onClick = {},
                icon = { PreviewRailCircle() },
                label = { Text("VPN") },
            )
            VPNisNavigationRailItem(
                selected = true,
                onClick = {},
                icon = { PreviewRailCircle() },
                selectedIcon = { PreviewRailCircle(active = true) },
                label = { Text("Settings") },
            )
            VPNisNavigationRailItem(
                selected = false,
                onClick = {},
                icon = {
                    BadgedBox(
                        badge = { Badge { Text("4") } },
                    ) {
                        PreviewRailCircle()
                    }
                },
                label = { Text("Alerts") },
            )
        }
    }
}
