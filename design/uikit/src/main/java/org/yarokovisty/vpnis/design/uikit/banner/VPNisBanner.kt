package org.yarokovisty.vpnis.design.uikit.banner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.LocalVPNisSemanticColors
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Closed set of visual variants for [VPNisBanner].
 *
 * Each variant maps to a distinct container/content colour pair:
 * - [Info]    → `secondaryContainer` / `onSecondaryContainer`
 * - [Warning] → `VPNisSemanticColors.warningContainer` / `onWarningContainer` (amber, DERIVED)
 * - [Error]   → `errorContainer` / `onErrorContainer`
 *
 * Using an enum (rather than separate boolean parameters) keeps the variant model closed
 * and exhaustively switch-able — adding a fourth variant is a compile error at every call site.
 */
public enum class VPNisBannerVariant { Info, Warning, Error }

/**
 * A single bounded action for [VPNisBanner].
 *
 * Banners support at most two actions: [VPNisBanner]'s `primaryAction` and `secondaryAction`.
 * Using a named data class (rather than a free lambda list) enforces that boundary in the
 * type system.
 *
 * @param label Text label displayed on the [TextButton].
 * @param onClick Callback invoked when the user taps the button.
 */
public data class VPNisBannerAction(val label: String, val onClick: () -> Unit)

/**
 * Inline notification banner for the VPNis design system.
 *
 * Material 3 has no Banner component — this is a custom implementation built from M3
 * primitives ([Surface], [Row], [Icon], [Text], [IconButton], [TextButton]).
 *
 * Container and content colours are resolved from [MaterialTheme.colorScheme] or
 * [LocalVPNisSemanticColors] based on [variant]. The dismiss button draws its own "×" via
 * [Canvas] so that `:design:uikit` does not acquire a dependency on `material-icons-*`.
 *
 * **Layout:**
 * ```
 * Surface (shape = medium, containerColor, contentColor)
 *   Column
 *     Row  [icon?] [text weight(1f)] [dismiss-IconButton?]
 *     Row  (end-aligned)  [secondaryAction?]  [primaryAction?]
 * ```
 *
 * **A11y:**
 * - Dismiss [IconButton] has `contentDescription = "Dismiss"` and the default 48 dp touch
 *   target provided by [IconButton].
 * - [icon] (if provided) receives a variant-appropriate `contentDescription`
 *   ("Information", "Warning", or "Error") so screen readers announce the severity.
 *
 * **Dependency note:** this composable reads [LocalVPNisSemanticColors.current] internally
 * and never exposes [org.yarokovisty.vpnis.design.theme.VPNisSemanticColors] in its public
 * signature, keeping `:design:uikit → :design:theme` at `implementation` scope.
 *
 * @param text Message to display. Rendered with `bodyMedium` typography.
 * @param variant Visual style and colour role. See [VPNisBannerVariant].
 * @param modifier Optional [Modifier] applied to the outer [Surface].
 * @param icon Optional leading icon. Supply an [ImageVector] from the calling module — the
 *   app's `material-icons-core` provides `Icons.Filled.Info`, `.Warning`, `.Error`. Defaults
 *   to `null`. When provided, receives an accessibility label derived from [variant].
 * @param primaryAction Optional primary action rendered as a trailing [TextButton].
 * @param secondaryAction Optional secondary action rendered before [primaryAction].
 * @param onDismiss If non-null, a dismiss "×" button appears at the trailing edge of the main
 *   row. The callback is invoked on tap.
 */
@Composable
public fun VPNisBanner(
    text: String,
    variant: VPNisBannerVariant,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primaryAction: VPNisBannerAction? = null,
    secondaryAction: VPNisBannerAction? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val semanticColors = LocalVPNisSemanticColors.current
    val colorScheme = MaterialTheme.colorScheme

    val containerColor: Color
    val contentColor: Color
    val variantDescription: String
    when (variant) {
        VPNisBannerVariant.Info -> {
            containerColor = colorScheme.secondaryContainer
            contentColor = colorScheme.onSecondaryContainer
            variantDescription = "Information"
        }
        VPNisBannerVariant.Warning -> {
            containerColor = semanticColors.warningContainer
            contentColor = semanticColors.onWarningContainer
            variantDescription = "Warning"
        }
        VPNisBannerVariant.Error -> {
            containerColor = colorScheme.errorContainer
            contentColor = colorScheme.onErrorContainer
            variantDescription = "Error"
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = if (onDismiss != null) 4.dp else 16.dp,
                    top = 12.dp,
                    bottom = 8.dp,
                ),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = variantDescription,
                        modifier = Modifier
                            .padding(top = 2.dp, end = 8.dp)
                            .size(20.dp),
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp, bottom = 4.dp),
                )
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        DismissIcon(tint = contentColor)
                    }
                }
            }
            if (primaryAction != null || secondaryAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (secondaryAction != null) {
                        TextButton(
                            onClick = secondaryAction.onClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                        ) {
                            Text(text = secondaryAction.label)
                        }
                    }
                    if (primaryAction != null) {
                        TextButton(
                            onClick = primaryAction.onClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                        ) {
                            Text(text = primaryAction.label)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws a centered "×" glyph sized to 18 dp. Used as the dismiss icon in [VPNisBanner]
 * so that `:design:uikit` does not acquire a dependency on `material-icons-*`.
 *
 * @param tint Stroke colour — caller passes the current content colour so the icon
 *   matches the banner's colour role.
 */
@Composable
private fun DismissIcon(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val strokeWidth = 2.dp.toPx()
        val inset = size.minDimension * 0.2f
        drawLine(
            color = tint,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

// --- Previews ---------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun VPNisBannerInfoLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisBanner(
            text = "Your connection is active. All traffic is encrypted end-to-end.",
            variant = VPNisBannerVariant.Info,
            primaryAction = VPNisBannerAction(label = "Details") {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisBannerInfoDismissibleLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisBanner(
            text = "New server region available: Tokyo, Japan.",
            variant = VPNisBannerVariant.Info,
            primaryAction = VPNisBannerAction(label = "Switch") {},
            secondaryAction = VPNisBannerAction(label = "Later") {},
            onDismiss = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisBannerWarningLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisBanner(
            text = "Your free trial expires in 3 days. Upgrade to keep unlimited access.",
            variant = VPNisBannerVariant.Warning,
            primaryAction = VPNisBannerAction(label = "Upgrade") {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisBannerWarningDismissibleLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisBanner(
            text = "Kill-switch is disabled. Your IP may be exposed if the VPN drops.",
            variant = VPNisBannerVariant.Warning,
            primaryAction = VPNisBannerAction(label = "Enable") {},
            secondaryAction = VPNisBannerAction(label = "Ignore") {},
            onDismiss = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisBannerErrorLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisBanner(
            text = "Unable to reach VPN server. Check your network and try again.",
            variant = VPNisBannerVariant.Error,
            primaryAction = VPNisBannerAction(label = "Retry") {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisBannerErrorDismissibleLightPreview() {
    VPNisTheme(darkTheme = false) {
        VPNisBanner(
            text = "Authentication failed. Your session may have expired.",
            variant = VPNisBannerVariant.Error,
            primaryAction = VPNisBannerAction(label = "Sign in") {},
            secondaryAction = VPNisBannerAction(label = "Cancel") {},
            onDismiss = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisBannerInfoDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisBanner(
            text = "Your connection is active. All traffic is encrypted end-to-end.",
            variant = VPNisBannerVariant.Info,
            primaryAction = VPNisBannerAction(label = "Details") {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisBannerInfoDismissibleDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisBanner(
            text = "New server region available: Tokyo, Japan.",
            variant = VPNisBannerVariant.Info,
            primaryAction = VPNisBannerAction(label = "Switch") {},
            secondaryAction = VPNisBannerAction(label = "Later") {},
            onDismiss = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisBannerWarningDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisBanner(
            text = "Your free trial expires in 3 days. Upgrade to keep unlimited access.",
            variant = VPNisBannerVariant.Warning,
            primaryAction = VPNisBannerAction(label = "Upgrade") {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisBannerWarningDismissibleDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisBanner(
            text = "Kill-switch is disabled. Your IP may be exposed if the VPN drops.",
            variant = VPNisBannerVariant.Warning,
            primaryAction = VPNisBannerAction(label = "Enable") {},
            secondaryAction = VPNisBannerAction(label = "Ignore") {},
            onDismiss = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisBannerErrorDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisBanner(
            text = "Unable to reach VPN server. Check your network and try again.",
            variant = VPNisBannerVariant.Error,
            primaryAction = VPNisBannerAction(label = "Retry") {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisBannerErrorDismissibleDarkPreview() {
    VPNisTheme(darkTheme = true) {
        VPNisBanner(
            text = "Authentication failed. Your session may have expired.",
            variant = VPNisBannerVariant.Error,
            primaryAction = VPNisBannerAction(label = "Sign in") {},
            secondaryAction = VPNisBannerAction(label = "Cancel") {},
            onDismiss = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun VPNisBannerFontScalePreview() {
    VPNisTheme(darkTheme = false) {
        VPNisBanner(
            text = "This subscription renews automatically on 15 August 2026. " +
                "You can cancel anytime in your account settings before the renewal date.",
            variant = VPNisBannerVariant.Warning,
            primaryAction = VPNisBannerAction(label = "Manage") {},
            secondaryAction = VPNisBannerAction(label = "Cancel") {},
            onDismiss = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
