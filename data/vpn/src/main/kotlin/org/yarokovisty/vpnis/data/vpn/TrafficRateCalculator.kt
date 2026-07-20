package org.yarokovisty.vpnis.data.vpn

import org.yarokovisty.vpnis.core.domain.model.TrafficStats

/**
 * Turns successive **cumulative** byte counters (as read from the Xray stats API) into a
 * [TrafficStats] snapshot carrying both the cumulative totals and per-second rates (issues #69/#130).
 *
 * Pure arithmetic — no Android, no AAR — so it is JVM-unit-testable without Robolectric (like
 * [XrayConfigBuilder] / [TunnelNotifications.contentFor]). The clock is injected as [elapsedNanos]
 * so tests control cadence deterministically; production passes `SystemClock.elapsedRealtimeNanos`
 * (wired in [VpnModule]) — **not** `System.nanoTime`, which pauses in deep sleep and would divide
 * real bytes by a stalled interval, spiking the first post-Doze reading.
 *
 * ## Behaviour
 * - **First sample:** rates are `0` (no prior point to diff against); cumulative totals echoed.
 * - **Steady:** `rate = deltaBytes * 1e9 / deltaNanos`, computed per direction over the *true*
 *   elapsed interval.
 * - **Counter reset** (a direction's new total < its previous, e.g. after `counter.Set(0)` or a
 *   proxy restart): that direction rebaselines to rate `0` — never negative.
 * - **Clock non-advance** (`deltaNanos <= 0`): both rates `0`, guarding against divide-by-zero and
 *   a backwards clock.
 *
 * ## Threading
 * Holds mutable "previous sample" state and is **not** synchronised. Correctness relies on the sole
 * caller — [TrafficStatsPoller] — being a single sequential loop (one [sample] in flight at a time).
 */
internal class TrafficRateCalculator(private val elapsedNanos: () -> Long) {

    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastNanos = 0L
    private var hasPrevious = false

    /**
     * Records the latest cumulative counters and returns the derived [TrafficStats].
     *
     * @param rxCumulative cumulative downlink bytes since the tunnel (or last reset) began.
     * @param txCumulative cumulative uplink bytes since the tunnel (or last reset) began.
     */
    fun sample(rxCumulative: Long, txCumulative: Long): TrafficStats {
        val now = elapsedNanos()
        val deltaNanos = now - lastNanos

        val rxBps: Long
        val txBps: Long
        if (!hasPrevious || deltaNanos <= 0L) {
            rxBps = 0L
            txBps = 0L
        } else {
            rxBps = rateOf(rxCumulative - lastRxBytes, deltaNanos)
            txBps = rateOf(txCumulative - lastTxBytes, deltaNanos)
        }

        lastRxBytes = rxCumulative
        lastTxBytes = txCumulative
        lastNanos = now
        hasPrevious = true

        return TrafficStats(
            rxBytes = rxCumulative,
            txBytes = txCumulative,
            rxBps = rxBps,
            txBps = txBps,
        )
    }

    /**
     * Bytes-per-second for one direction over [deltaNanos]. A negative [deltaBytes] means the
     * counter reset — that is not a real transfer, so the rate rebaselines to `0`.
     */
    private fun rateOf(deltaBytes: Long, deltaNanos: Long): Long =
        if (deltaBytes < 0L) 0L else deltaBytes * NANOS_PER_SECOND / deltaNanos

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
