package org.yarokovisty.vpnis.data.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TrafficRateCalculator] — the pure cumulative-counter → rate math. The clock is a
 * controllable lambda so cadence (including a simulated Doze gap) is deterministic; no Android.
 */
class TrafficRateCalculatorTest {

    private var nowNanos = 0L
    private val calc = TrafficRateCalculator { nowNanos }

    private fun advanceSeconds(seconds: Long) {
        nowNanos += seconds * 1_000_000_000L
    }

    @Test
    fun `first sample - rates are zero and cumulative echoed`() {
        val stats = calc.sample(rxCumulative = 500, txCumulative = 200)

        assertEquals(500L, stats.rxBytes)
        assertEquals(200L, stats.txBytes)
        assertEquals(0L, stats.rxBps)
        assertEquals(0L, stats.txBps)
    }

    @Test
    fun `steady one-second interval - rate equals delta bytes per second`() {
        calc.sample(rxCumulative = 0, txCumulative = 0)
        advanceSeconds(1)

        val stats = calc.sample(rxCumulative = 125_000, txCumulative = 25_000)

        assertEquals(125_000L, stats.rxBps)
        assertEquals(25_000L, stats.txBps)
        // Cumulative totals are echoed unchanged.
        assertEquals(125_000L, stats.rxBytes)
        assertEquals(25_000L, stats.txBytes)
    }

    @Test
    fun `counter reset - direction rebaselines to zero rate, never negative`() {
        calc.sample(rxCumulative = 1_000_000, txCumulative = 500_000)
        advanceSeconds(1)

        // rx counter reset (new < previous); tx still climbing.
        val stats = calc.sample(rxCumulative = 10_000, txCumulative = 600_000)

        assertEquals(0L, stats.rxBps)
        assertEquals(100_000L, stats.txBps)
    }

    @Test
    fun `large elapsed gap (Doze wake) - rate divides by the true interval, no spike`() {
        calc.sample(rxCumulative = 0, txCumulative = 0)
        advanceSeconds(60) // screen-off for a minute

        // Only 125 KB moved across the whole 60s window.
        val stats = calc.sample(rxCumulative = 125_000, txCumulative = 0)

        // 125_000 bytes / 60 s ≈ 2083 B/s — NOT a spike from a stalled clock.
        assertEquals(2_083L, stats.rxBps)
        assertEquals(0L, stats.txBps)
    }

    @Test
    fun `clock does not advance - rates are zero (no divide-by-zero)`() {
        calc.sample(rxCumulative = 0, txCumulative = 0)
        // no advanceSeconds() — same timestamp.

        val stats = calc.sample(rxCumulative = 999_999, txCumulative = 999_999)

        assertEquals(0L, stats.rxBps)
        assertEquals(0L, stats.txBps)
    }
}
