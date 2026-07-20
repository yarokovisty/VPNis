package org.yarokovisty.vpnis.data.vpn

import org.yarokovisty.vpnis.core.domain.model.TrafficStats

/**
 * Narrow callback for pushing live traffic samples into the [ConnectionControllerImpl] state
 * machine (issues #69 / #130).
 *
 * Deliberately **separate** from [TunnelStateSink]: that interface models discrete *lifecycle
 * events* (established / stopped / error), whereas this is a continuous ~0.5 Hz telemetry feed
 * from [TrafficStatsPoller]. Keeping them apart preserves the lifecycle sink's cohesion (ISP) and
 * lets the poller depend only on this one-method surface.
 *
 * [ConnectionControllerImpl] implements this alongside [org.yarokovisty.vpnis.core.domain.connection.ConnectionController]
 * and [TunnelStateSink].
 *
 * ## Thread-safety
 *
 * [onTrafficSample] may be called from the poller's background coroutine
 * ([kotlinx.coroutines.Dispatchers.IO]). Implementations must be thread-safe;
 * [ConnectionControllerImpl] satisfies this by writing exclusively to a thread-safe
 * [kotlinx.coroutines.flow.MutableStateFlow].
 */
internal interface TrafficSink {

    /**
     * Delivers a fresh traffic snapshot. The implementation applies it **only** while the tunnel is
     * [org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState.Connected]; a sample arriving
     * in any other state (e.g. after teardown) is dropped.
     */
    fun onTrafficSample(stats: TrafficStats)
}
