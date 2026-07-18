package org.yarokovisty.vpnis.feature.home

// ---------------------------------------------------------------------------
// Number formatters used by HomeScreen
// ---------------------------------------------------------------------------

private const val KBPS_THRESHOLD = 1_000L
private const val MBPS_THRESHOLD = 1_000_000L
private const val KBPS_DIVISOR = 1_000.0
private const val MBPS_DIVISOR = 1_000_000.0

/**
 * Bitrate magnitude bucket. The concrete unit label (localised "B/s" / "Б/с" …) is resolved
 * from string resources at the UI layer — the formatter stays free of Android/i18n concerns.
 */
internal enum class BitrateUnit { BYTES, KILOBYTES, MEGABYTES }

/**
 * A formatted bitrate split into its numeric [value] and its [unit] bucket, so the UI can render
 * the number prominently and the unit as a separate, de-emphasised suffix (per the design canvas).
 */
internal data class FormattedBitrate(val value: String, val unit: BitrateUnit)

/**
 * Formats a bytes-per-second rate for the traffic tiles, splitting the number from its unit.
 *
 * Rules (MVP — minimal precision):
 * - 0 bps              → "0"    + [BitrateUnit.BYTES]
 * - < 1 000 bps        → "NNN"  + [BitrateUnit.BYTES]
 * - < 1 000 000 bps    → "N.N"  + [BitrateUnit.KILOBYTES]
 * - ≥ 1 000 000 bps    → "N.N"  + [BitrateUnit.MEGABYTES]
 *
 * The unit is returned as a [BitrateUnit] rather than a literal string so the caller can resolve
 * the localised label (RU "Б/с" / "КБ/с" / "МБ/с", EN "B/s" / "KB/s" / "MB/s") from resources.
 *
 * Traffic precision beyond single-decimal MB/s is deferred to #69.
 *
 * @param bps Bytes per second (not bits). Negative values are treated as zero.
 */
internal fun formatBitrate(bps: Long): FormattedBitrate {
    val v = maxOf(0L, bps)
    return when {
        v < KBPS_THRESHOLD -> FormattedBitrate("$v", BitrateUnit.BYTES)
        v < MBPS_THRESHOLD -> FormattedBitrate("%.1f".format(v / KBPS_DIVISOR), BitrateUnit.KILOBYTES)
        else -> FormattedBitrate("%.1f".format(v / MBPS_DIVISOR), BitrateUnit.MEGABYTES)
    }
}
