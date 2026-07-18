package org.yarokovisty.vpnis.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Local [ImageVector] definitions for bottom-navigation glyphs that are not
 * present in `material-icons-core`.
 *
 * Path data sourced from Material Symbols (Apache 2.0 license).
 * Defined locally to avoid pulling in `material-icons-extended` (~10 MB artifact).
 *
 * Icons defined here: Dns, AltRoute, Tune.
 * The `Home` glyph is available as [androidx.compose.material.icons.Icons.Default.Home]
 * and does NOT need to be duplicated here.
 */
internal object NavIcons {

    /**
     * Material Symbol: dns (24 dp, filled).
     * Used for the Servers tab in the bottom navigation bar.
     */
    val Dns: ImageVector by lazy {
        ImageVector.Builder(
            name = "Dns",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                // Top row
                moveTo(20f, 1f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(4f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(3f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(7f, 7f)
                curveTo(5.9f, 7f, 5f, 6.1f, 5f, 5f)
                reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
                close()
                // Bottom row
                moveTo(20f, 11f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(4f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(7f, 17f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
                close()
            }
        }.build()
    }

    /**
     * Material Symbol: alt_route (24 dp, filled).
     * Used for the Bypass tab in the bottom navigation bar.
     */
    val AltRoute: ImageVector by lazy {
        ImageVector.Builder(
            name = "AltRoute",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                // Left prong tip
                moveTo(9.78f, 11.16f)
                lineToRelative(-1.42f, 1.42f)
                curveToRelative(-0.68f, -0.69f, -1.34f, -1.58f, -1.79f, -2.94f)
                lineToRelative(1.94f, -0.49f)
                curveTo(8.83f, 10.04f, 9.28f, 10.65f, 9.78f, 11.16f)
                close()
                // Left arrow head
                moveTo(11f, 6f)
                lineTo(7f, 2f)
                lineTo(3f, 6f)
                horizontalLineToRelative(3.02f)
                curveTo(6.04f, 6.81f, 6.1f, 7.54f, 6.21f, 8.17f)
                lineToRelative(1.94f, -0.49f)
                curveTo(8.08f, 7.2f, 8.03f, 6.63f, 8.02f, 6f)
                horizontalLineTo(11f)
                close()
                // Right arrow head + main route
                moveTo(21f, 6f)
                lineToRelative(-4f, -4f)
                lineToRelative(-4f, 4f)
                horizontalLineToRelative(2.99f)
                curveToRelative(-0.1f, 3.68f, -1.28f, 4.75f, -2.54f, 5.88f)
                curveToRelative(-0.5f, 0.44f, -1.01f, 0.92f, -1.45f, 1.55f)
                curveToRelative(-0.34f, -0.49f, -0.73f, -0.88f, -1.13f, -1.24f)
                lineTo(9.46f, 13.6f)
                curveTo(10.39f, 14.45f, 11f, 15.14f, 11f, 17f)
                verticalLineToRelative(5f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-5f)
                curveToRelative(0f, -2.02f, 0.71f, -2.66f, 1.79f, -3.63f)
                curveToRelative(1.38f, -1.24f, 3.08f, -2.78f, 3.2f, -7.37f)
                horizontalLineTo(21f)
                close()
            }
        }.build()
    }

    /**
     * Material Symbol: tune (24 dp, filled).
     * Used for the Settings tab in the bottom navigation bar.
     */
    val Tune: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tune",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(3f, 17f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(10f)
                verticalLineTo(5f)
                horizontalLineTo(3f)
                close()
                moveTo(13f, 21f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(2f)
                close()
                moveTo(7f, 9f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineTo(9f)
                horizontalLineTo(7f)
                close()
                moveTo(21f, 13f)
                verticalLineToRelative(-2f)
                horizontalLineTo(11f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(10f)
                close()
                moveTo(15f, 9f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(6f)
                close()
            }
        }.build()
    }
}
