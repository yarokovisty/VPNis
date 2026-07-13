package org.yarokovisty.vpnis.feature.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Title + optional subtitle displayed beneath the [ConnectionButton].
 *
 * The title is styled at ~24sp / weight 500 (closest to M3 headlineSmall).
 * The subtitle uses [MaterialTheme.colorScheme.onSurfaceVariant] at ~14sp.
 *
 * Accessibility: when [isLive] is true the title text is marked as a Polite live region
 * so TalkBack announces transient status changes (e.g. "Подключение…") without focus moves.
 * Set [isLive] = true only while in the Connecting state.
 *
 * String localisation note: [title] and [subtitle] are caller-supplied.
 * // TODO(#54): replace preview literals with string resources.
 *
 * @param title Primary status label (e.g. "Подключено", "Подключение…").
 * @param subtitle Secondary detail (e.g. server name, error hint); null hides the row.
 * @param modifier Optional [Modifier].
 * @param titleColor Override colour for [title]; defaults to [MaterialTheme.colorScheme.onSurface].
 * @param isLive When true, wraps [title] in a Polite live region for TalkBack announcements.
 */
@Composable
public fun StatusIndicator(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
    isLive: Boolean = false,
) {
    val resolvedTitleColor = if (titleColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        titleColor
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 32.sp,
            ),
            color = resolvedTitleColor,
            modifier = if (isLive) {
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            } else {
                Modifier
            },
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun StatusIndicatorConnectedLightPreview() {
    VPNisTheme(darkTheme = false) {
        StatusIndicator(
            title = "Подключено",
            subtitle = "Amsterdam · NL",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusIndicatorConnectingLightPreview() {
    VPNisTheme(darkTheme = false) {
        StatusIndicator(
            title = "Подключение…",
            subtitle = null,
            isLive = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusIndicatorDisconnectedLightPreview() {
    VPNisTheme(darkTheme = false) {
        StatusIndicator(
            title = "Отключено",
            subtitle = null,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusIndicatorErrorLightPreview() {
    VPNisTheme(darkTheme = false) {
        StatusIndicator(
            title = "Ошибка",
            subtitle = "Не удалось подключиться",
            titleColor = MaterialTheme.colorScheme.error,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusIndicatorConnectedDarkPreview() {
    VPNisTheme(darkTheme = true) {
        StatusIndicator(
            title = "Подключено",
            subtitle = "Amsterdam · NL",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusIndicatorConnectingDarkPreview() {
    VPNisTheme(darkTheme = true) {
        StatusIndicator(
            title = "Подключение…",
            subtitle = null,
            isLive = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusIndicatorDisconnectedDarkPreview() {
    VPNisTheme(darkTheme = true) {
        StatusIndicator(
            title = "Отключено",
            subtitle = null,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusIndicatorErrorDarkPreview() {
    VPNisTheme(darkTheme = true) {
        StatusIndicator(
            title = "Ошибка",
            subtitle = "Не удалось подключиться",
            titleColor = MaterialTheme.colorScheme.error,
        )
    }
}
