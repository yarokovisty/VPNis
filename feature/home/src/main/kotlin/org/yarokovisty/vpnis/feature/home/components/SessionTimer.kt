package org.yarokovisty.vpnis.feature.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.yarokovisty.vpnis.design.theme.VPNisTheme
import java.time.Duration
import java.time.Instant

private const val TICK_INTERVAL_MS = 1_000L
private const val PREVIEW_ELAPSED_SECONDS = 5_025L // 01:23:45

/**
 * Displays an elapsed-time counter that ticks every second.
 *
 * Internally computes [Duration.between] the [since] instant and now, formatting the result
 * as `HH:MM:SS`. Negative durations (clock skew, incorrect instant) are clamped to zero.
 *
 * The ticking `LaunchedEffect` is keyed on [since] — a new session [Instant] restarts the effect
 * and resets the display.
 *
 * Accessibility: when [contentDescription] is non-null it is applied to the [Text] node so
 * TalkBack reads a meaningful label instead of the raw formatted string. Pass e.g.
 * "Session duration 00:01:23". Keep this NOT a live region — the [StatusIndicator] owns
 * connection-state announcements. Do NOT hardcode language strings here; pass i18n text from
 * the caller.
 *
 * @param since The moment the VPN tunnel became active.
 * @param modifier Optional [Modifier].
 * @param contentDescription Optional TalkBack label for the timer node. When null, TalkBack
 *   reads the formatted time string directly.
 */
@Composable
public fun SessionTimer(since: Instant, modifier: Modifier = Modifier, contentDescription: String? = null) {
    var totalSeconds by remember(since) { mutableLongStateOf(0L) }

    LaunchedEffect(since) {
        while (true) {
            val elapsed = Duration.between(since, Instant.now()).seconds
            totalSeconds = maxOf(0L, elapsed)
            delay(TICK_INTERVAL_MS)
        }
    }

    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Text(
        text = formatDuration(totalSeconds),
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.then(semanticsModifier),
    )
}

/**
 * Formats a total-seconds count as `HH:MM:SS`.
 *
 * Negative values are clamped to 0. Can be called from tests or the screen without needing
 * a running composition.
 *
 * @param totalSeconds Non-negative elapsed seconds.
 * @return Formatted string, e.g. `"01:23:45"`.
 */
public fun formatDuration(totalSeconds: Long): String {
    val clamped = maxOf(0L, totalSeconds)
    val hours = clamped / 3600
    val minutes = (clamped % 3600) / 60
    val seconds = clamped % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun SessionTimerZeroLightPreview() {
    VPNisTheme(darkTheme = false) {
        SessionTimer(
            since = Instant.now(),
            contentDescription = "Session duration 00:00:00",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionTimerElapsedLightPreview() {
    VPNisTheme(darkTheme = false) {
        // Static preview: show a representative elapsed label via the helper
        Text(
            text = formatDuration(PREVIEW_ELAPSED_SECONDS),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionTimerZeroDarkPreview() {
    VPNisTheme(darkTheme = true) {
        SessionTimer(
            since = Instant.now(),
            contentDescription = "Session duration 00:00:00",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionTimerElapsedDarkPreview() {
    VPNisTheme(darkTheme = true) {
        Text(
            text = formatDuration(PREVIEW_ELAPSED_SECONDS),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
