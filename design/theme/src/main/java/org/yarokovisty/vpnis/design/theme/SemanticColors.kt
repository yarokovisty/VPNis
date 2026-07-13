package org.yarokovisty.vpnis.design.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extra semantic color roles not covered by the Material 3 standard [ColorScheme].
 *
 * **Warning role — DERIVED:** the "VPNis Material 3" design canvas does not contain a warning
 * colour role. The amber values follow the M3 tonal-palette construction for a warm amber source
 * colour (Hue ≈ 35 °). See `COLOR_AUDIT.md §§ Semantic colors` for the full audit row.
 *
 * **Connected role — FROM THE DESIGN CANVAS:** the "VPNis Material 3" design canvas specifies a
 * green/teal "connected" state colour used for the Connected hero button, ping indicators, and
 * session-active UI. Values are sourced directly from the Home-screen frames (a standard M3
 * green/teal tonal palette; source Hue ≈ 162 °).
 *
 * This type and [LocalVPNisSemanticColors] live in `:design:theme` so they can be provided
 * inside [VPNisTheme] without creating a `theme ↔ uikit` dependency cycle. Components in
 * `:design:uikit` read [LocalVPNisSemanticColors.current] internally and never expose this
 * type in their public API, keeping `:design:uikit → :design:theme` at `implementation` scope.
 *
 * @param warning Amber colour for warning foreground text and icons on [onWarning] backgrounds.
 * @param onWarning Colour for content placed directly on [warning].
 * @param warningContainer Low-emphasis amber container (banner backgrounds, chips).
 * @param onWarningContainer Colour for content inside [warningContainer].
 * @param connected Green/teal colour for connected-state foreground text and icons.
 * @param onConnected Colour for content placed directly on [connected].
 * @param connectedContainer Low-emphasis green/teal container (Connected button inner ring, ping chips).
 * @param onConnectedContainer Colour for content inside [connectedContainer].
 */
public data class VPNisSemanticColors(
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val connected: Color,
    val onConnected: Color,
    val connectedContainer: Color,
    val onConnectedContainer: Color,
)

/**
 * Light-theme semantic colors.
 *
 * Warning role — **DERIVED** (not in the design canvas).
 * Tonal palette for source #7E5700 (warm amber, Hue ≈ 35 °):
 * - warning          = #7E5700 (tone 40)
 * - onWarning        = #FFFFFF (tone 100)
 * - warningContainer = #FFDDB0 (tone 90)
 * - onWarningContainer = #281800 (tone 10)
 *
 * Connected role — **FROM THE DESIGN CANVAS** (Home-screen Connected hero frame).
 * Tonal palette for source #2F6A5F (green/teal, Hue ≈ 162 °):
 * - connected          = #2F6A5F (tone 40)
 * - onConnected        = #FFFFFF (tone 100)
 * - connectedContainer = #B6ECDF (tone 90)
 * - onConnectedContainer = #00201A (tone 10)
 */
internal val LightSemanticColors: VPNisSemanticColors = VPNisSemanticColors(
    warning = Color(0xFF7E5700),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDDB0),
    onWarningContainer = Color(0xFF281800),
    connected = Color(0xFF2F6A5F),
    onConnected = Color(0xFFFFFFFF),
    connectedContainer = Color(0xFFB6ECDF),
    onConnectedContainer = Color(0xFF00201A),
)

/**
 * Dark-theme semantic colors.
 *
 * Warning role — **DERIVED** (not in the design canvas):
 * - warning          = #FFB951 (tone 80)
 * - onWarning        = #422C00 (tone 20)
 * - warningContainer = #5F4100 (tone 30)
 * - onWarningContainer = #FFDDB0 (tone 90)
 *
 * Connected role — **FROM THE DESIGN CANVAS** (Home-screen Connected hero frame, dark variant):
 * - connected          = #9AD0C3 (tone 80)
 * - onConnected        = #003730 (tone 20)
 * - connectedContainer = #164F45 (tone 30)
 * - onConnectedContainer = #B6ECDF (tone 90)
 */
internal val DarkSemanticColors: VPNisSemanticColors = VPNisSemanticColors(
    warning = Color(0xFFFFB951),
    onWarning = Color(0xFF422C00),
    warningContainer = Color(0xFF5F4100),
    onWarningContainer = Color(0xFFFFDDB0),
    connected = Color(0xFF9AD0C3),
    onConnected = Color(0xFF003730),
    connectedContainer = Color(0xFF164F45),
    onConnectedContainer = Color(0xFFB6ECDF),
)

/**
 * [CompositionLocal] providing [VPNisSemanticColors] to the composition tree.
 *
 * Provided by [VPNisTheme]. Components in `:design:uikit` read it inside composable bodies:
 * ```kotlin
 * val semanticColors = LocalVPNisSemanticColors.current
 * ```
 * The default value is [LightSemanticColors], which provides a meaningful fallback when the
 * composable is used outside [VPNisTheme] (e.g. in isolated unit tests).
 *
 * `staticCompositionLocalOf` is appropriate: semantic colors only change on a full theme switch
 * (light ↔ dark), which already invalidates the entire theme subtree.
 */
public val LocalVPNisSemanticColors: ProvidableCompositionLocal<VPNisSemanticColors> =
    staticCompositionLocalOf { LightSemanticColors }
