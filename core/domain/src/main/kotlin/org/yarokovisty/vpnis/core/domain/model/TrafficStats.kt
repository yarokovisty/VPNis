package org.yarokovisty.vpnis.core.domain.model

/**
 * Instantaneous traffic snapshot for an active VPN tunnel.
 *
 * All byte counts are cumulative since the tunnel was established.
 * Rate fields ([rxBps], [txBps]) are per-second averages computed by the data layer.
 */
public data class TrafficStats(val rxBytes: Long, val txBytes: Long, val rxBps: Long, val txBps: Long) {
    public companion object {
        /** Sentinel value used when traffic counters are not yet available. */
        public val EMPTY: TrafficStats = TrafficStats(
            rxBytes = 0L,
            txBytes = 0L,
            rxBps = 0L,
            txBps = 0L,
        )
    }
}
