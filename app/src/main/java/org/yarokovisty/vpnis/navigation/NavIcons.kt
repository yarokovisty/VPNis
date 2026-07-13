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
                moveTo(17f, 2f)
                lineToRelative(4f, 4f)
                lineToRelative(-4f, 4f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(-2.26f)
                curveToRelative(-0.69f, 0f, -1.3f, 0.42f, -1.56f, 1.06f)
                lineToRelative(-3.92f, 9.88f)
                curveTo(8.97f, 18.58f, 8.36f, 19f, 7.67f, 19f)
                horizontalLineTo(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(5.67f)
                lineToRelative(3.92f, -9.88f)
                curveTo(12.23f, 5.42f, 13.48f, 4.74f, 14.74f, 4.74f)
                horizontalLineTo(17f)
                verticalLineTo(2f)
                close()
                moveTo(9.26f, 9f)
                lineToRelative(-1.3f, -1.3f)
                curveTo(7.37f, 7.12f, 6.7f, 7f, 6f, 7f)
                horizontalLineTo(2f)
                verticalLineTo(9f)
                horizontalLineToRelative(4f)
                lineToRelative(0.96f, 2.44f)
                lineTo(9.26f, 9f)
                close()
                moveTo(14.74f, 17f)
                lineToRelative(1.3f, 1.3f)
                curveTo(16.63f, 18.88f, 17.3f, 19f, 18f, 19f)
                horizontalLineTo(22f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-4f)
                lineToRelative(-0.96f, -2.44f)
                lineTo(14.74f, 17f)
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
