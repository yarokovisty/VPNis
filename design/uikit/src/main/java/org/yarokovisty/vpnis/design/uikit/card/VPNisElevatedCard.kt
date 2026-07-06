package org.yarokovisty.vpnis.design.uikit.card

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Elevated card for the VPNis design system — non-clickable variant.
 *
 * Wraps M3 [ElevatedCard] with the container pinned to
 * [MaterialTheme.colorScheme.surfaceContainerLow]. The elevation shadow
 * distinguishes the card from the background without an explicit border.
 * Use for informational content that is not interactive.
 *
 * The [content] slot receives a [ColumnScope] receiver so callers can use
 * `Modifier.align` and sibling layout helpers directly inside the card.
 *
 * @param modifier Optional [Modifier] applied to the card.
 * @param content Slot composable rendered inside the card column.
 */
@Composable
public fun VPNisElevatedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        content = content,
    )
}

/**
 * Elevated card for the VPNis design system — clickable variant.
 *
 * Wraps M3 [ElevatedCard] with the container pinned to
 * [MaterialTheme.colorScheme.surfaceContainerLow]. Use when the entire card
 * surface is a touch target and visual elevation is needed to distinguish
 * the card from the background.
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
public fun VPNisElevatedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        content = content,
    )
}

// --- Previews ---------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisElevatedCardNonClickableLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisElevatedCard {
            Text(
                text = "Connection Plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Premium — unlimited bandwidth",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisElevatedCardClickableEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisElevatedCard(onClick = {}) {
            Text(
                text = "Connection Plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Premium — unlimited bandwidth",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisElevatedCardClickableDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisElevatedCard(onClick = {}, enabled = false) {
            Text(
                text = "Connection Plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Premium — unlimited bandwidth",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisElevatedCardNonClickableDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisElevatedCard {
            Text(
                text = "Connection Plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Premium — unlimited bandwidth",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisElevatedCardClickableEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisElevatedCard(onClick = {}) {
            Text(
                text = "Connection Plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Premium — unlimited bandwidth",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisElevatedCardClickableDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisElevatedCard(onClick = {}, enabled = false) {
            Text(
                text = "Connection Plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Premium — unlimited bandwidth",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}
