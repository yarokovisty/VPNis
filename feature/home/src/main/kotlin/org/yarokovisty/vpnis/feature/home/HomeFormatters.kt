package org.yarokovisty.vpnis.feature.home

// ---------------------------------------------------------------------------
// Number formatters used by HomeScreen
// ---------------------------------------------------------------------------

private const val KBPS_THRESHOLD = 1_000L
private const val MBPS_THRESHOLD = 1_000_000L
private const val KBPS_DIVISOR = 1_000.0
private const val MBPS_DIVISOR = 1_000_000.0

/**
 * Formats a bytes-per-second rate as a compact string for the traffic tiles.
 *
 * Rules (MVP — minimal precision):
 * - 0 bps              → "0 B/s"
 * - < 1 000 bps        → "NNN B/s"
 * - < 1 000 000 bps    → "N.N KB/s"
 * - ≥ 1 000 000 bps    → "N.N MB/s"
 *
 * Traffic precision beyond single-decimal MB/s is deferred to #69.
 *
 * @param bps Bytes per second (not bits). Negative values are treated as zero.
 */
internal fun formatBitrate(bps: Long): String {
    val v = maxOf(0L, bps)
    return when {
        v < KBPS_THRESHOLD -> "$v B/s"
        v < MBPS_THRESHOLD -> "${"%.1f".format(v / KBPS_DIVISOR)} KB/s"
        else -> "${"%.1f".format(v / MBPS_DIVISOR)} MB/s"
    }
}
