package org.yarokovisty.vpnis.design.uikit.card

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Outlined card for the VPNis design system — non-clickable variant.
 *
 * Wraps M3 [OutlinedCard] with the container pinned to
 * [MaterialTheme.colorScheme.surface] and the border pinned to
 * [MaterialTheme.colorScheme.outlineVariant] via [CardDefaults.outlinedCardBorder].
 * Use for informational content that requires a visible boundary without elevation.
 *
 * The [content] slot receives a [ColumnScope] receiver so callers can use
 * `Modifier.align` and sibling layout helpers directly inside the card.
 *
 * @param modifier Optional [Modifier] applied to the card.
 * @param content Slot composable rendered inside the card column.
 */
@Composable
public fun VPNisOutlinedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = CardDefaults.outlinedCardBorder(enabled = true),
        content = content,
    )
}

/**
 * Outlined card for the VPNis design system — clickable variant.
 *
 * Wraps M3 [OutlinedCard] with the container pinned to
 * [MaterialTheme.colorScheme.surface] and the border pinned to
 * [MaterialTheme.colorScheme.outlineVariant] via [CardDefaults.outlinedCardBorder].
 * Use when the entire card surface is a touch target and a flat outlined
 * appearance is preferred over elevation.
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
public fun VPNisOutlinedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = CardDefaults.outlinedCardBorder(enabled = enabled),
        content = content,
    )
}

// --- Previews ---------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedCardNonClickableLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisOutlinedCard {
            Text(
                text = "Protocol",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Xray — VLESS + XTLS-Reality",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedCardClickableEnabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisOutlinedCard(onClick = {}) {
            Text(
                text = "Protocol",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Xray — VLESS + XTLS-Reality",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedCardClickableDisabledLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisOutlinedCard(onClick = {}, enabled = false) {
            Text(
                text = "Protocol",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Xray — VLESS + XTLS-Reality",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedCardNonClickableDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisOutlinedCard {
            Text(
                text = "Protocol",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Xray — VLESS + XTLS-Reality",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedCardClickableEnabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisOutlinedCard(onClick = {}) {
            Text(
                text = "Protocol",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Xray — VLESS + XTLS-Reality",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisOutlinedCardClickableDisabledDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisOutlinedCard(onClick = {}, enabled = false) {
            Text(
                text = "Protocol",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Xray — VLESS + XTLS-Reality",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}
