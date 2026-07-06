package org.yarokovisty.vpnis.design.uikit.card

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Filled card for the VPNis design system — non-clickable variant.
 *
 * Wraps M3 [Card] with the container pinned to
 * [MaterialTheme.colorScheme.surfaceContainerLow]. Use for informational
 * content that is not interactive.
 *
 * The [content] slot receives a [ColumnScope] receiver so callers can use
 * `Modifier.align` and sibling layout helpers directly inside the card.
 *
 * @param modifier Optional [Modifier] applied to the card.
 * @param content Slot composable rendered inside the card column.
 */
@Composable
public fun VPNisCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        content = content,
    )
}

/**
 * Filled card for the VPNis design system — clickable variant.
 *
 * Wraps M3 [Card] with the container pinned to
 * [MaterialTheme.colorScheme.surfaceContainerLow]. Use when the entire card
 * surface is a touch target.
 *
 * The [content] slot receives a [ColumnScope] receiver so callers can use
 * `Modifier.align` and sibling layout helpers directly inside the card.
 *
 * @param onClick Callback invoked when the card is tapped.
 * @param modifier Optional [Modifier] applied to the card.
 * @param enabled Whether the card accepts user interaction.
 * @param content Slot composable rendered inside the card column.
 */
@Composable
public fun VPNisCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        content = content,
    )
}

// --- Previews ---------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisCardNonClickableLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisCard {
            Text(
                text = "Server Location",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Amsterdam, Netherlands",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCardClickableEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisCard(onClick = {}) {
            Text(
                text = "Server Location",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Amsterdam, Netherlands",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCardClickableDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisCard(onClick = {}, enabled = false) {
            Text(
                text = "Server Location",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Amsterdam, Netherlands",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCardNonClickableDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisCard {
            Text(
                text = "Server Location",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Amsterdam, Netherlands",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCardClickableEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisCard(onClick = {}) {
            Text(
                text = "Server Location",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Amsterdam, Netherlands",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisCardClickableDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisCard(onClick = {}, enabled = false) {
            Text(
                text = "Server Location",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Amsterdam, Netherlands",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}
