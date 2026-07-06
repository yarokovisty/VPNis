package org.yarokovisty.vpnis.design.uikit.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Primary tab row for the VPNis design system.
 *
 * Wraps M3 [PrimaryTabRow] with the container colour pinned to
 * [MaterialTheme.colorScheme.surface].  The active indicator (underline pill)
 * and active label colour use M3 1.3+ defaults that resolve to the branded
 * `primary` and `secondary` tokens respectively via [VPNisTheme]'s colour
 * scheme — no additional pinning is required.
 *
 * **`selectedTabIndex` vs key selection:** unlike navigation bar/rail items,
 * [selectedTabIndex] is an `Int` here because M3 needs the integer position to
 * animate the underline indicator to the correct tab.  This is the intentional
 * tab-row exception to the key-based selection rule used elsewhere in VPNis.
 *
 * Emit [VPNisTab] or [VPNisLeadingIconTab] composables inside the [tabs] slot:
 *
 * ```kotlin
 * var selected by remember { mutableStateOf(0) }
 * VPNisPrimaryTabRow(selectedTabIndex = selected) {
 *     tabLabels.forEachIndexed { index, label ->
 *         VPNisTab(
 *             selected = index == selected,
 *             onClick  = { selected = index },
 *             text     = { Text(label) },
 *         )
 *     }
 * }
 * ```
 *
 * @param selectedTabIndex Zero-based index of the currently active tab.
 * @param modifier Optional [Modifier] applied to the tab row.
 * @param tabs Slot for [VPNisTab] or [VPNisLeadingIconTab] composables.
 */
@Composable
public fun VPNisPrimaryTabRow(selectedTabIndex: Int, modifier: Modifier = Modifier, tabs: @Composable () -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tabs = tabs,
    )
}

/**
 * Secondary tab row for the VPNis design system.
 *
 * Wraps M3 [SecondaryTabRow] with the container colour pinned to
 * [MaterialTheme.colorScheme.surface].  The full-width divider and active-tab
 * content colour use M3 defaults branded via [VPNisTheme].
 *
 * Like [VPNisPrimaryTabRow], [selectedTabIndex] is an `Int` because M3 needs the
 * position to render the active state correctly.
 *
 * @param selectedTabIndex Zero-based index of the currently active tab.
 * @param modifier Optional [Modifier] applied to the tab row.
 * @param tabs Slot for [VPNisTab] or [VPNisLeadingIconTab] composables.
 */
@Composable
public fun VPNisSecondaryTabRow(selectedTabIndex: Int, modifier: Modifier = Modifier, tabs: @Composable () -> Unit) {
    SecondaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tabs = tabs,
    )
}

/**
 * Tab for use inside [VPNisPrimaryTabRow] or [VPNisSecondaryTabRow].
 *
 * Wraps M3 [Tab] and exposes the standard [text] and [icon] slots.  Either slot
 * may be `null`; providing both produces a stacked layout (icon above text).
 * For a side-by-side layout use [VPNisLeadingIconTab] instead.
 *
 * **Content colours pinned:** M3 [Tab] defaults `unselectedContentColor` to
 * `selectedContentColor` and the enclosing tab row provides `primary` as the
 * ambient content colour — so out of the box *every* tab label renders in
 * `primary`, with no selected/unselected distinction. This wrapper pins
 * `selectedContentColor = primary` and `unselectedContentColor =
 * onSurfaceVariant` (both branded, read live from [VPNisTheme]) to restore the
 * distinction and correct dark-theme colours.
 *
 * @param selected Whether this tab is currently active.
 * @param onClick Called when the tab is tapped.
 * @param modifier Optional [Modifier] applied to the tab.
 * @param enabled Whether the tab accepts user interaction.
 * @param text Optional text label slot.
 * @param icon Optional icon slot.
 */
@Composable
public fun VPNisTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        text = text,
        icon = icon,
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Leading-icon tab for use inside [VPNisPrimaryTabRow] or [VPNisSecondaryTabRow].
 *
 * Wraps M3 [LeadingIconTab] to produce a row with an icon on the leading edge
 * and a text label on the trailing edge — the "side-by-side" layout, in contrast
 * to the stacked layout of [VPNisTab] when both [VPNisTab.text] and [VPNisTab.icon]
 * are provided.
 *
 * @param selected Whether this tab is currently active.
 * @param onClick Called when the tab is tapped.
 * @param text Required text label slot.
 * @param icon Required icon slot; supply a size-24 dp icon for best results.
 * @param modifier Optional [Modifier] applied to the tab.
 * @param enabled Whether the tab accepts user interaction.
 */
@Composable
public fun VPNisLeadingIconTab(
    selected: Boolean,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LeadingIconTab(
        selected = selected,
        onClick = onClick,
        text = text,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --- Preview helpers --------------------------------------------------------

@Composable
private fun PreviewTabCircle(active: Boolean = false) {
    val color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = Modifier.size(18.dp)) {
        drawCircle(color = color, radius = size.minDimension * 0.4f)
    }
}

// --- Previews — PrimaryTabRow Light ----------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisPrimaryTabRowLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisPrimaryTabRow(selectedTabIndex = 0) {
            VPNisTab(
                selected = true,
                onClick = {},
                text = { Text("Protocol", style = MaterialTheme.typography.titleSmall) },
            )
            VPNisTab(
                selected = false,
                onClick = {},
                text = { Text("Logs", style = MaterialTheme.typography.titleSmall) },
            )
            VPNisTab(
                selected = false,
                onClick = {},
                text = { Text("Stats", style = MaterialTheme.typography.titleSmall) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisPrimaryTabRowWithIconsLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisPrimaryTabRow(selectedTabIndex = 1) {
            VPNisTab(
                selected = false,
                onClick = {},
                icon = { PreviewTabCircle() },
                text = { Text("Protocol") },
            )
            VPNisTab(
                selected = true,
                onClick = {},
                icon = { PreviewTabCircle(active = true) },
                text = { Text("Logs") },
            )
            VPNisTab(
                selected = false,
                onClick = {},
                icon = { PreviewTabCircle() },
                text = { Text("Stats") },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisPrimaryTabRowLeadingIconLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisPrimaryTabRow(selectedTabIndex = 0) {
            VPNisLeadingIconTab(
                selected = true,
                onClick = {},
                icon = { PreviewTabCircle(active = true) },
                text = { Text("Protocol") },
            )
            VPNisLeadingIconTab(
                selected = false,
                onClick = {},
                icon = { PreviewTabCircle() },
                text = { Text("Logs") },
            )
            VPNisLeadingIconTab(
                selected = false,
                onClick = {},
                icon = { PreviewTabCircle() },
                text = { Text("Stats") },
            )
        }
    }
}

// --- Previews — PrimaryTabRow Dark -----------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisPrimaryTabRowDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisPrimaryTabRow(selectedTabIndex = 1) {
            VPNisTab(
                selected = false,
                onClick = {},
                text = { Text("Protocol", style = MaterialTheme.typography.titleSmall) },
            )
            VPNisTab(
                selected = true,
                onClick = {},
                text = { Text("Logs", style = MaterialTheme.typography.titleSmall) },
            )
            VPNisTab(
                selected = false,
                onClick = {},
                text = { Text("Stats", style = MaterialTheme.typography.titleSmall) },
            )
        }
    }
}

// --- Previews — SecondaryTabRow Light --------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisSecondaryTabRowLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisSecondaryTabRow(selectedTabIndex = 0) {
            VPNisTab(
                selected = true,
                onClick = {},
                text = { Text("Protocol", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisTab(
                selected = false,
                onClick = {},
                text = { Text("Logs", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisTab(
                selected = false,
                onClick = {},
                text = { Text("Stats", style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

// --- Previews — SecondaryTabRow Dark ---------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisSecondaryTabRowDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisSecondaryTabRow(selectedTabIndex = 2) {
            VPNisTab(
                selected = false,
                onClick = {},
                text = { Text("Protocol", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisTab(
                selected = false,
                onClick = {},
                text = { Text("Logs", style = MaterialTheme.typography.labelLarge) },
            )
            VPNisTab(
                selected = true,
                onClick = {},
                text = { Text("Stats", style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

// --- Preview — Font scale 1.5x ----------------------------------------------

@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun VPNisTabRowFontScaleLargePreview() {
    VPNisTheme(darkTheme = false) {
        VPNisPrimaryTabRow(selectedTabIndex = 0) {
            VPNisTab(
                selected = true,
                onClick = {},
                text = { Text("Protocol", style = MaterialTheme.typography.titleSmall) },
            )
            VPNisTab(
                selected = false,
                onClick = {},
                text = { Text("Logs", style = MaterialTheme.typography.titleSmall) },
            )
            VPNisTab(
                selected = false,
                onClick = {},
                text = { Text("Stats", style = MaterialTheme.typography.titleSmall) },
            )
        }
    }
}
