package org.yarokovisty.vpnis.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.LocalVPNisSemanticColors
import org.yarokovisty.vpnis.design.theme.VPNisTheme
import org.yarokovisty.vpnis.feature.home.components.icons.HomeIcons

/**
 * Visual state of [ConnectionButton] — a UI-only enum that the screen maps from [HomeUiState].
 *
 * The full-screen composable (#52) owns the mapping; this enum keeps the button generic
 * and self-contained.
 */
public enum class ConnectionButtonState {
    /** VPN tunnel is active and traffic is flowing. */
    Connected,

    /** A connection attempt is in progress. */
    Connecting,

    /** No tunnel; ready to connect (or no server configured). */
    Disconnected,

    /** The last connection attempt failed. */
    Error,
}

/**
 * Large circular tap target representing the VPN toggle.
 *
 * Visual states:
 * - **Connected** — green core, pulse-ring animation, filled connectedContainer halo.
 * - **Connecting** — primary-coloured core, indeterminate arc spinner, filled primaryContainer halo.
 * - **Disconnected** — surfaceVariant core, dashed outline ring (no filled halo).
 * - **Error** — error-coloured core, exclamation icon, filled errorContainer halo.
 *
 * Accessibility:
 * - Announced as a toggle (on/off) via [Role.Switch] semantics via [Modifier.toggleable].
 * - [stateDescription] reflects the current [state] using the caller-supplied [stateLabel].
 * - Decorative animations are hidden from the accessibility tree via [clearAndSetSemantics].
 * - When [state] is [ConnectionButtonState.Connecting], the live region is on the
 *   [StatusIndicator] below — do NOT add a live region here.
 * - When [enabled] is false, a `disabled()` semantic is added and the core/icon are dimmed.
 *
 * String localisation note: [contentLabel] and [stateLabel] are caller-supplied to keep
 * this component i18n-ready. // TODO(#54): replace preview literals with string resources.
 *
 * @param state Current visual/semantic state.
 * @param onClick Invoked when the user taps the button.
 * @param modifier Optional [Modifier].
 * @param enabled Whether the button is interactive (false = visually dimmed, non-clickable).
 * @param contentLabel Accessibility label for the whole control, e.g. "VPN toggle".
 * @param stateLabel Accessibility state description matching [state], e.g. "Подключено".
 */
@Composable
public fun ConnectionButton(
    state: ConnectionButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentLabel: String = "VPN", // TODO(#54)
    stateLabel: String = "", // TODO(#54)
) {
    val colors = connectionButtonColors(state)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(200.dp)
            .toggleable(
                value = state == ConnectionButtonState.Connected,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onClick() },
            )
            .semantics {
                contentDescription = contentLabel
                this.stateDescription = stateLabel
                if (!enabled) disabled()
            },
    ) {
        ConnectionButtonDecorations(state = state, haloColor = colors.halo)
        ConnectionButtonCore(
            coreColor = colors.core,
            iconColor = colors.icon,
            state = state,
            enabled = enabled,
        )
    }
}

/**
 * Colour tokens resolved for a given [ConnectionButtonState].
 *
 * [halo] is the filled disc drawn between the outer ring decorations and the core.
 * For Disconnected state this colour is unused — no filled halo is drawn.
 */
internal data class ConnectionButtonColors(val core: Color, val icon: Color, val halo: Color)

@Composable
internal fun connectionButtonColors(state: ConnectionButtonState): ConnectionButtonColors {
    val semanticColors = LocalVPNisSemanticColors.current
    return when (state) {
        ConnectionButtonState.Connected -> ConnectionButtonColors(
            core = semanticColors.connected,
            icon = semanticColors.onConnected,
            halo = semanticColors.connectedContainer,
        )
        ConnectionButtonState.Connecting -> ConnectionButtonColors(
            core = MaterialTheme.colorScheme.primary,
            icon = MaterialTheme.colorScheme.onPrimary,
            halo = MaterialTheme.colorScheme.primaryContainer,
        )
        ConnectionButtonState.Disconnected -> ConnectionButtonColors(
            core = MaterialTheme.colorScheme.surfaceVariant,
            icon = MaterialTheme.colorScheme.onSurfaceVariant,
            halo = Color.Transparent,
        )
        ConnectionButtonState.Error -> ConnectionButtonColors(
            core = MaterialTheme.colorScheme.error,
            icon = MaterialTheme.colorScheme.onError,
            halo = MaterialTheme.colorScheme.errorContainer,
        )
    }
}

private const val DISABLED_ALPHA = 0.38f

@Composable
internal fun ConnectionButtonCore(
    coreColor: Color,
    iconColor: Color,
    state: ConnectionButtonState,
    enabled: Boolean = true,
) {
    val icon: ImageVector = when (state) {
        ConnectionButtonState.Connected, ConnectionButtonState.Disconnected -> HomeIcons.PowerSettingsNew
        ConnectionButtonState.Connecting -> HomeIcons.VpnKey
        ConnectionButtonState.Error -> HomeIcons.PriorityHigh
    }
    // Icon size: ~43-47% of 150dp core — bumped to 64dp (≈43%) for visual weight matching design.
    // Per-state: PowerSettingsNew/PriorityHigh at 64dp, VpnKey at 60dp (slightly narrower glyph).
    val iconSize = if (state == ConnectionButtonState.Connecting) 60.dp else 64.dp
    val disabledModifier = if (!enabled) Modifier.alpha(DISABLED_ALPHA) else Modifier
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(coreColor)
            .then(disabledModifier),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun ConnectionButtonConnectedLightPreview() {
    VPNisTheme(darkTheme = false) {
        ConnectionButton(
            state = ConnectionButtonState.Connected,
            onClick = {},
            contentLabel = "VPN переключатель",
            stateLabel = "Подключено",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionButtonConnectingLightPreview() {
    VPNisTheme(darkTheme = false) {
        ConnectionButton(
            state = ConnectionButtonState.Connecting,
            onClick = {},
            contentLabel = "VPN переключатель",
            stateLabel = "Подключение",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionButtonDisconnectedLightPreview() {
    VPNisTheme(darkTheme = false) {
        ConnectionButton(
            state = ConnectionButtonState.Disconnected,
            onClick = {},
            contentLabel = "VPN переключатель",
            stateLabel = "Отключено",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionButtonErrorLightPreview() {
    VPNisTheme(darkTheme = false) {
        ConnectionButton(
            state = ConnectionButtonState.Error,
            onClick = {},
            contentLabel = "VPN переключатель",
            stateLabel = "Ошибка",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionButtonConnectedDarkPreview() {
    VPNisTheme(darkTheme = true) {
        ConnectionButton(
            state = ConnectionButtonState.Connected,
            onClick = {},
            contentLabel = "VPN переключатель",
            stateLabel = "Подключено",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionButtonDisconnectedDarkPreview() {
    VPNisTheme(darkTheme = true) {
        ConnectionButton(
            state = ConnectionButtonState.Disconnected,
            onClick = {},
            contentLabel = "VPN переключатель",
            stateLabel = "Отключено",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionButtonErrorDarkPreview() {
    VPNisTheme(darkTheme = true) {
        ConnectionButton(
            state = ConnectionButtonState.Error,
            onClick = {},
            contentLabel = "VPN переключатель",
            stateLabel = "Ошибка",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionButtonDisabledPreview() {
    VPNisTheme(darkTheme = false) {
        ConnectionButton(
            state = ConnectionButtonState.Disconnected,
            onClick = {},
            contentLabel = "VPN переключатель",
            stateLabel = "Отключено",
            enabled = false,
        )
    }
}
