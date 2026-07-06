package org.yarokovisty.vpnis.design.uikit.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Single-line or multi-line list item for the VPNis design system.
 *
 * Wraps M3 [ListItem] and preserves all five content slots while pinning
 * the container colour to [MaterialTheme.colorScheme.surface].
 *
 * **Container colour choice:** `surface` is the branded role for flat list
 * rows — rows blend into the page surface and are separated by dividers
 * (rather than by a raised tint), matching M3's own `ListItem` default and the
 * VPNis reference. It is pinned explicitly so the brand token is applied even
 * if M3's default changes.
 *
 * **Click handling:** `VPNisListItem` is **not** clickable by default. M3
 * [ListItem] has no `onClick` parameter — click behaviour is applied by the
 * caller via [Modifier.clickable] on the `modifier` param. This is
 * intentional: it lets callers control ripple shape, enabled state, and
 * semantics without a separate `onClick` overload.
 *
 * Example usage with click:
 * ```kotlin
 * VPNisListItem(
 *     headlineContent = { Text("Amsterdam") },
 *     modifier = Modifier.clickable { onServerSelected(serverId) },
 * )
 * ```
 *
 * @param headlineContent Required primary text slot, always visible.
 * @param modifier Optional [Modifier] applied to the list item — pass
 *   [Modifier.clickable] here to make the entire row interactive.
 * @param overlineContent Optional text rendered above [headlineContent].
 * @param supportingContent Optional secondary text rendered below [headlineContent],
 *   displayed in `bodyMedium` typography by M3's default.
 * @param leadingContent Optional icon or avatar slot on the leading edge.
 *   Size the composable to the intended icon/avatar size; M3 centres it vertically.
 *   Position mirrors to the trailing edge in RTL layouts.
 * @param trailingContent Optional icon, control, or metadata slot on the
 *   trailing edge. Typical values: a chevron icon, a [androidx.compose.material3.Switch],
 *   or a short metadata `Text`. Position mirrors to the leading edge in RTL layouts.
 */
@Composable
public fun VPNisListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = headlineContent,
        modifier = modifier,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// --- Preview helpers --------------------------------------------------------

/** Filled circle stand-in for a leading/trailing icon in previews (no material-icons dep). */
@Composable
private fun PreviewIconDot(color: Color = Color.Gray) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color = color, radius = size.minDimension * 0.35f)
    }
}

/** Small rounded rectangle stand-in for a trailing "chip" or button in previews. */
@Composable
private fun PreviewTrailingChip() {
    Box(
        modifier = Modifier.size(width = 48.dp, height = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(width = 48.dp, height = 24.dp)) {
            drawRoundRect(
                color = Color.LightGray,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            )
        }
        Text(text = "›", style = MaterialTheme.typography.labelLarge)
    }
}

// --- Previews — Light -------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisListItemHeadlineOnlyLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisListItem(
            headlineContent = { Text("Amsterdam, Netherlands") },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisListItemHeadlineAndSupportingLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisListItem(
            headlineContent = { Text("Amsterdam, Netherlands") },
            supportingContent = { Text("Latency: 12 ms · 98% uptime") },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisListItemFullSlotsLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisListItem(
            headlineContent = { Text("Amsterdam, Netherlands") },
            overlineContent = { Text("RECOMMENDED") },
            supportingContent = { Text("Latency: 12 ms · 98% uptime") },
            leadingContent = { PreviewIconDot(color = Color(0xFF4CAF50)) },
            trailingContent = { PreviewTrailingChip() },
        )
    }
}

// --- Previews — Dark --------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisListItemHeadlineOnlyDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisListItem(
            headlineContent = { Text("Amsterdam, Netherlands") },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisListItemHeadlineAndSupportingDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisListItem(
            headlineContent = { Text("Amsterdam, Netherlands") },
            supportingContent = { Text("Latency: 12 ms · 98% uptime") },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisListItemFullSlotsDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisListItem(
            headlineContent = { Text("Amsterdam, Netherlands") },
            overlineContent = { Text("RECOMMENDED") },
            supportingContent = { Text("Latency: 12 ms · 98% uptime") },
            leadingContent = { PreviewIconDot(color = Color(0xFF4CAF50)) },
            trailingContent = { PreviewTrailingChip() },
        )
    }
}

// --- Preview — RTL ----------------------------------------------------------

/**
 * RTL sensitivity preview — locale "ar" triggers a right-to-left layout
 * direction so that leading/trailing slot positions are exercised in the
 * mirrored direction.
 */
@Preview(showBackground = true, locale = "ar")
@Composable
private fun VPNisListItemRtlPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisListItem(
            headlineContent = { Text("أمستردام، هولندا") },
            supportingContent = { Text("زمن الاستجابة: ١٢ مللي ثانية") },
            leadingContent = { PreviewIconDot(color = Color(0xFF4CAF50)) },
            trailingContent = { PreviewTrailingChip() },
        )
    }
}

// --- Preview — Font scale 1.5x ----------------------------------------------

/**
 * Large-font accessibility preview — [fontScale] 1.5 exercises text truncation
 * and multi-line wrapping in headline and supporting slots.
 */
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun VPNisListItemFontScaleLargePreview() {
    VPNisTheme(darkTheme = false) {
        VPNisListItem(
            headlineContent = { Text("Amsterdam, Netherlands") },
            supportingContent = { Text("Latency: 12 ms · 98% uptime") },
            leadingContent = { PreviewIconDot() },
            trailingContent = { PreviewTrailingChip() },
        )
    }
}
