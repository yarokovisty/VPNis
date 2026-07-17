package org.yarokovisty.vpnis.feature.home.components

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.LocalVPNisSemanticColors

internal const val PULSE_DURATION_MS = 2400

/**
 * Decorative layer drawn between the outer ring elements and the core disc.
 *
 * Layer order (bottom → top inside the 200dp [ConnectionButton] Box):
 * 1. Outer ring/pulse: [ConnectedPulseRing] | [CircularProgressIndicator] | [DisconnectedDashedRing]
 * 2. Filled halo disc (~164dp): [ContainerHaloDisc] — drawn for all states except Disconnected.
 * 3. Core disc (150dp): rendered by [ConnectionButtonCore] above this layer.
 *
 * The [haloColor] is [Color.Transparent] for Disconnected (no disc drawn) — this avoids an
 * extra branch here and keeps the call site simple.
 *
 * All children call [clearAndSetSemantics] to stay invisible to TalkBack.
 */
@Composable
internal fun ConnectionButtonDecorations(state: ConnectionButtonState, haloColor: Color) {
    when (state) {
        ConnectionButtonState.Connected -> ConnectedPulseRing()
        ConnectionButtonState.Connecting -> {
            // The M3 indeterminate spinner sits around the core (~172-186dp orbit).
            // It is drawn above the filled halo but below nothing — z-order in Box is
            // determined by composition order; the halo disc is drawn next (below spinner).
            CircularProgressIndicator(
                modifier = Modifier.size(180.dp).clearAndSetSemantics { },
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
            )
        }
        ConnectionButtonState.Disconnected -> DisconnectedDashedRing()
        ConnectionButtonState.Error -> Unit
    }
    if (state != ConnectionButtonState.Disconnected) {
        ContainerHaloDisc(color = haloColor)
    }
}

@Composable
internal fun ConnectedPulseRing() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_DURATION_MS, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_DURATION_MS, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )
    val ringColor = LocalVPNisSemanticColors.current.connected
    Box(
        modifier = Modifier
            .size(200.dp)
            .scale(pulseScale)
            .drawBehind {
                drawCircle(
                    color = ringColor.copy(alpha = pulseAlpha),
                    radius = size.minDimension / 2f,
                )
            }
            .clearAndSetSemantics { },
    )
}

@Composable
internal fun DisconnectedDashedRing() {
    val dashColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(168.dp)
            .drawBehind {
                drawCircle(
                    color = dashColor,
                    radius = size.minDimension / 2f,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                            phase = 0f,
                        ),
                    ),
                )
            }
            .clearAndSetSemantics { },
    )
}

/**
 * Filled circular halo disc (~164dp) drawn between the outer ring decoration and the 150dp core.
 *
 * Replaces the former thin-stroke [ContainerRing]. The design specifies a filled container disc
 * inset ~18px (≈18dp) from the outer 200dp boundary → 200 - 2×18 ≈ 164dp diameter.
 *
 * Entirely decorative — excluded from the a11y tree via [clearAndSetSemantics].
 */
@Composable
internal fun ContainerHaloDisc(color: Color) {
    Box(
        modifier = Modifier
            .size(164.dp)
            .clip(CircleShape)
            .background(color)
            .clearAndSetSemantics { },
    )
}
