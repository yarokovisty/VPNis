package org.yarokovisty.vpnis.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [formatBitrate] — verifies the numeric value / unit-bucket split and the
 * threshold boundaries. The unit is a [BitrateUnit] bucket (localised at the consuming layer), so
 * these tests are locale-independent for the bucket; the numeric string uses the default locale's
 * decimal separator and is only asserted on integer (B/s) cases to stay locale-agnostic.
 */
class BitrateFormatterTest {

    @Test
    fun `zero bps - renders 0 bytes bucket`() {
        assertEquals(FormattedBitrate("0", BitrateUnit.BYTES), formatBitrate(0))
    }

    @Test
    fun `negative bps - clamped to 0 bytes`() {
        assertEquals(FormattedBitrate("0", BitrateUnit.BYTES), formatBitrate(-1))
    }

    @Test
    fun `below 1000 bps - bytes bucket`() {
        assertEquals(FormattedBitrate("999", BitrateUnit.BYTES), formatBitrate(999))
    }

    @Test
    fun `exactly 1000 bps - crosses to kilobytes bucket`() {
        assertEquals(BitrateUnit.KILOBYTES, formatBitrate(1_000).unit)
    }

    @Test
    fun `below 1000000 bps - kilobytes bucket`() {
        assertEquals(BitrateUnit.KILOBYTES, formatBitrate(999_999).unit)
    }

    @Test
    fun `exactly 1000000 bps - crosses to megabytes bucket`() {
        assertEquals(BitrateUnit.MEGABYTES, formatBitrate(1_000_000).unit)
    }

    @Test
    fun `above 1000000 bps - megabytes bucket`() {
        assertEquals(BitrateUnit.MEGABYTES, formatBitrate(12_400_000).unit)
    }
}
