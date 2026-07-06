package org.yarokovisty.vpnis.design.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extra semantic color roles not covered by the Material 3 standard [ColorScheme].
 *
 * **All roles in this type are DERIVED** — the "VPNis Material 3" design canvas does not
 * contain a warning colour role. The amber values follow the M3 tonal-palette construction
 * for a warm amber source colour (Hue ≈ 35 °). See `COLOR_AUDIT.md §§ Semantic colors` for
 * the full audit row.
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
 */
public data class VPNisSemanticColors(
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

/**
 * Light-theme semantic colors — amber warning role. **DERIVED** — not in the design canvas.
 *
 * Tonal palette for source #7E5700 (warm amber, Hue ≈ 35 °):
 * - warning          = #7E5700 (tone 40)
 * - onWarning        = #FFFFFF (tone 100)
 * - warningContainer = #FFDDB0 (tone 90)
 * - onWarningContainer = #281800 (tone 10)
 */
internal val LightSemanticColors: VPNisSemanticColors = VPNisSemanticColors(
    warning = Color(0xFF7E5700),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDDB0),
    onWarningContainer = Color(0xFF281800),
)

/**
 * Dark-theme semantic colors — amber warning role. **DERIVED** — not in the design canvas.
 *
 * - warning          = #FFB951 (tone 80)
 * - onWarning        = #422C00 (tone 20)
 * - warningContainer = #5F4100 (tone 30)
 * - onWarningContainer = #FFDDB0 (tone 90)
 */
internal val DarkSemanticColors: VPNisSemanticColors = VPNisSemanticColors(
    warning = Color(0xFFFFB951),
    onWarning = Color(0xFF422C00),
    warningContainer = Color(0xFF5F4100),
    onWarningContainer = Color(0xFFFFDDB0),
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
