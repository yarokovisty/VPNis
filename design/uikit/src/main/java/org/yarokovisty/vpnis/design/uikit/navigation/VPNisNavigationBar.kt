package org.yarokovisty.vpnis.design.uikit.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Bottom navigation bar for the VPNis design system.
 *
 * Wraps M3 [NavigationBar] and pins the container colour to
 * [MaterialTheme.colorScheme.surface] — the branded role for navigation surfaces
 * that blend with the page background rather than contrasting with it.
 *
 * **Item iteration:** callers iterate their own descriptor list outside this
 * composable and emit one [VPNisNavigationBarItem] per entry.  `selected` is
 * computed by key/identity comparison against a remembered selection key — not
 * by passing a `selectedIndex: Int` into the bar:
 *
 * ```kotlin
 * var selectedKey by remember { mutableStateOf(items.first().key) }
 * VPNisNavigationBar {
 *     items.forEach { item ->
 *         VPNisNavigationBarItem(
 *             selected = item.key == selectedKey,
 *             onClick  = { selectedKey = item.key },
 *             icon     = { Icon(item.icon, contentDescription = item.label) },
 *             label    = { Text(item.label) },
 *         )
 *     }
 * }
 * ```
 *
 * **Edge-to-edge:** [windowInsets] defaults to [NavigationBarDefaults.windowInsets]
 * so the bar automatically consumes the system navigation gesture zone.
 * Pass `WindowInsets(0, 0, 0, 0)` to opt out in non-edge-to-edge contexts such
 * as nested previews or showcase screens.
 *
 * @param modifier Optional [Modifier] applied to the bar surface.
 * @param windowInsets Insets consumed by the bar for edge-to-edge layouts.
 * @param content Row slot; emit one [VPNisNavigationBarItem] per destination.
 */
@Composable
public fun VPNisNavigationBar(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = windowInsets,
        content = content,
    )
}

/**
 * Navigation bar item for use inside [VPNisNavigationBar].
 *
 * Wraps M3 [NavigationBarItem] and adds a dedicated [selectedIcon] slot for the
 * filled or tinted icon variant shown when the item is active.  When [selectedIcon]
 * is omitted it falls back to [icon], matching M3 behaviour for single-icon items.
 *
 * **Active-label colour decision:** this wrapper does not pin
 * `NavigationBarItemColors` explicitly.  In M3 1.4.x the active-label colour role
 * changed from `onSurface` to `secondary`; the branded `secondary` token flows
 * automatically through [VPNisTheme]'s colour scheme.  If a future design audit
 * requires explicit overriding it can be done internally without changing the
 * public signature.
 *
 * **Badge:** wrap the [icon] slot in M3's `BadgedBox` to attach a numeric or dot
 * badge.  When a badge is present the icon composable **must** carry a
 * `contentDescription` that conveys both the item purpose and the count, e.g.
 * `"Alerts, 3 new"`, so screen readers announce the full context in one pass.
 *
 * @param selected Whether this destination is currently active.
 * @param onClick Called when the item is tapped.
 * @param icon Composable shown when the item is not selected.
 * @param modifier Optional [Modifier] applied to the item.
 * @param selectedIcon Composable shown when the item is selected; defaults to [icon].
 * @param enabled Whether the item accepts user interaction.
 * @param label Optional text label; use [MaterialTheme.typography.labelLarge] for
 *   consistency with the VPNis type scale.
 * @param alwaysShowLabel Whether the label is visible even when the item is unselected.
 */
@Composable
public fun RowScope.VPNisNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selectedIcon: @Composable () -> Unit = icon,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
) {
    NavigationBarItem(
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

/** Filled circle placeholder for an icon slot in previews (no material-icons dep). */
@Composable
private fun PreviewNavCircle(active: Boolean = false) {
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
private fun VPNisNavigationBarSelectedLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisNavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            VPNisNavigationBarItem(
                selected = true,
                onClick = {},
                icon = { PreviewNavCircle() },
                selectedIcon = { PreviewNavCircle(active = true) },
                label = { Text("VPN", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewNavCircle() },
                label = { Text("Settings", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewNavCircle() },
                label = { Text("Account", style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisNavigationBarWithBadgeLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisNavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            VPNisNavigationBarItem(
                selected = true,
                onClick = {},
                icon = { PreviewNavCircle() },
                selectedIcon = { PreviewNavCircle(active = true) },
                label = { Text("VPN") },
            )
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewNavCircle() },
                label = { Text("Settings") },
            )
            // Item with badge — caller wraps icon in BadgedBox
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = {
                    BadgedBox(
                        badge = { Badge { Text("3") } },
                    ) {
                        PreviewNavCircle()
                    }
                },
                label = { Text("Alerts") },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisNavigationBarAlwaysShowLabelFalseLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisNavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            VPNisNavigationBarItem(
                selected = true,
                onClick = {},
                icon = { PreviewNavCircle(active = true) },
                label = { Text("VPN") },
                alwaysShowLabel = false,
            )
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewNavCircle() },
                label = { Text("Settings") },
                alwaysShowLabel = false,
            )
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewNavCircle() },
                label = { Text("Account") },
                alwaysShowLabel = false,
            )
        }
    }
}

// --- Previews — Dark -------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisNavigationBarSelectedDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisNavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            VPNisNavigationBarItem(
                selected = true,
                onClick = {},
                icon = { PreviewNavCircle() },
                selectedIcon = { PreviewNavCircle(active = true) },
                label = { Text("VPN", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewNavCircle() },
                label = { Text("Settings", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewNavCircle() },
                label = { Text("Account", style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisNavigationBarWithBadgeDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisNavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewNavCircle() },
                label = { Text("VPN") },
            )
            VPNisNavigationBarItem(
                selected = true,
                onClick = {},
                icon = { PreviewNavCircle() },
                selectedIcon = { PreviewNavCircle(active = true) },
                label = { Text("Settings") },
            )
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = {
                    BadgedBox(
                        badge = { Badge { Text("5") } },
                    ) {
                        PreviewNavCircle()
                    }
                },
                label = { Text("Alerts") },
            )
        }
    }
}

// --- Preview — Font scale 1.5x ----------------------------------------------

@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun VPNisNavigationBarFontScaleLargePreview() {
    VPNisTheme(darkTheme = false) {
        VPNisNavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            VPNisNavigationBarItem(
                selected = true,
                onClick = {},
                icon = { PreviewNavCircle(active = true) },
                label = { Text("VPN", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewNavCircle() },
                label = { Text("Settings", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewNavCircle() },
                label = { Text("Account", style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}
