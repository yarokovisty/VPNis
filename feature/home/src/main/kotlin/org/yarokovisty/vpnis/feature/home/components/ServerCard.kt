package org.yarokovisty.vpnis.feature.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yarokovisty.vpnis.design.theme.LocalVPNisSemanticColors
import org.yarokovisty.vpnis.design.theme.VPNisTheme
import org.yarokovisty.vpnis.feature.home.components.icons.HomeIcons

/**
 * Ping emphasis level used by [ServerCard] to colour the ping value.
 *
 * The screen (#52) maps a raw millisecond value to this enum.
 */
public enum class PingEmphasis {
    /** Good ping ≤ ~80 ms — coloured `connected` (green). */
    Good,

    /** Mid ping ~80–200 ms — coloured `warning` (amber). */
    Mid,

    /** High/unreachable ping > 200 ms or null — coloured `error` (red). */
    Poor,
}

/**
 * Card displaying a VPN server entry on the Home screen.
 *
 * Layout: leading 44 dp icon container → name + subtitle column → trailing area.
 * The trailing area shows either a [CircularProgressIndicator] (when [showSpinner] = true),
 * a custom [trailing] slot, or the default ping + chevron row.
 *
 * The entire card surface is clickable when [onClick] != null.
 *
 * Accessibility:
 * - When [showSpinner] is true (Connecting state) the spinner has no inherent label.
 *   Pass [statusDescription] (e.g. "Подключение") so TalkBack announces the card's
 *   current status via `stateDescription`. Do NOT hardcode language strings here.
 * - Ping meaning is never conveyed by colour alone: the numeric text ("42 ms" / "—") is
 *   always present; [pingEmphasis] colour is reinforcement only.
 *
 * @param name Server display name.
 * @param subtitle Protocol/config subtitle, e.g. "VLESS · Reality".
 * @param modifier Optional [Modifier].
 * @param pingText Formatted ping string, e.g. "42 ms". Null displays "—".
 * @param pingEmphasis Colour emphasis applied to [pingText].
 * @param showSpinner When true, replaces the ping/chevron trailing with a spinner (Connecting state).
 * @param statusDescription Optional accessible state description for the card node — set this
 *   when [showSpinner] is true so TalkBack announces the Connecting status (e.g. "Подключение").
 *   Ignored when null.
 * @param onClick Tap callback; null → card is non-interactive.
 * @param trailing Custom trailing slot; overrides the default ping+chevron when non-null.
 */
@Composable
public fun ServerCard(
    name: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    pingText: String? = null,
    pingEmphasis: PingEmphasis = PingEmphasis.Poor,
    showSpinner: Boolean = false,
    statusDescription: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val semanticColors = LocalVPNisSemanticColors.current
    val pingColor: Color = when (pingEmphasis) {
        PingEmphasis.Good -> semanticColors.connected
        PingEmphasis.Mid -> semanticColors.warning
        PingEmphasis.Poor -> MaterialTheme.colorScheme.error
    }
    val statusModifier = if (statusDescription != null) {
        Modifier.semantics { stateDescription = statusDescription }
    } else {
        Modifier
    }
    val clickModifier = if (onClick != null) {
        Modifier.semantics { role = Role.Button }.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .then(statusModifier)
            .then(clickModifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            ServerCardLeadingIcon()
            Spacer(modifier = Modifier.width(14.dp))
            ServerCardTextContent(name = name, subtitle = subtitle, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            ServerCardTrailing(
                trailing = trailing,
                showSpinner = showSpinner,
                pingText = pingText,
                pingColor = pingColor,
            )
        }
    }
}

@Composable
private fun ServerCardLeadingIcon() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = HomeIcons.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ServerCardTextContent(name: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ServerCardTrailing(
    trailing: (@Composable () -> Unit)?,
    showSpinner: Boolean,
    pingText: String?,
    pingColor: Color,
) {
    when {
        trailing != null -> trailing()
        showSpinner -> {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        else -> {
            Text(
                text = pingText ?: "—",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = pingColor,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = HomeIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun ServerCardGoodPingLightPreview() {
    VPNisTheme(darkTheme = false) {
        ServerCard(
            name = "Netherlands",
            subtitle = "VLESS · Reality",
            pingText = "42 ms",
            pingEmphasis = PingEmphasis.Good,
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerCardMidPingLightPreview() {
    VPNisTheme(darkTheme = false) {
        ServerCard(
            name = "United States",
            subtitle = "VLESS · Reality",
            pingText = "120 ms",
            pingEmphasis = PingEmphasis.Mid,
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerCardNoPingLightPreview() {
    VPNisTheme(darkTheme = false) {
        ServerCard(
            name = "Japan",
            subtitle = "Shadowsocks",
            pingText = null,
            pingEmphasis = PingEmphasis.Poor,
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerCardSpinnerLightPreview() {
    VPNisTheme(darkTheme = false) {
        ServerCard(
            name = "Netherlands",
            subtitle = "VLESS · Reality",
            showSpinner = true,
            statusDescription = "Connecting",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerCardGoodPingDarkPreview() {
    VPNisTheme(darkTheme = true) {
        ServerCard(
            name = "Netherlands",
            subtitle = "VLESS · Reality",
            pingText = "42 ms",
            pingEmphasis = PingEmphasis.Good,
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerCardNoPingDarkPreview() {
    VPNisTheme(darkTheme = true) {
        ServerCard(
            name = "Japan",
            subtitle = "Shadowsocks",
            pingText = null,
            pingEmphasis = PingEmphasis.Poor,
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerCardSpinnerDarkPreview() {
    VPNisTheme(darkTheme = true) {
        ServerCard(
            name = "Netherlands",
            subtitle = "VLESS · Reality",
            showSpinner = true,
            statusDescription = "Connecting",
        )
    }
}
