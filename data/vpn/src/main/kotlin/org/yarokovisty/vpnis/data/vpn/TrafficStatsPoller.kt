package org.yarokovisty.vpnis.data.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Polls Xray's traffic counters on a fixed cadence and pushes derived [org.yarokovisty.vpnis.core.domain.model.TrafficStats]
 * into the state machine via [TrafficSink] (issues #69 / #130).
 *
 * ## Ownership & lifecycle
 *
 * A Koin `single` (process-lifetime), driven explicitly by [VpnTunnelService] — mirroring
 * [TunnelNotificationPresenter]:
 * - [start] is called on the success path, right after `notificationPresenter.start(...)`.
 * - [stop] is called in `finishTeardown` **before** `xrayCore.stop()` (so no in-flight
 *   [XrayCore.queryStats] races the Xray shutdown and produces teardown log-noise) and in
 *   `onDestroy` (the low-memory-kill path).
 *
 * ## Cadence & decoupling
 *
 * The loop polls every [pollIntervalMs] (~2s). This is deliberately **decoupled** from the
 * notification's ≤1/sec budget: [TunnelNotificationPresenter] throttles the *display*, while this
 * governs the *work* (one loopback GET + parse per tick). The first tick fires after one interval,
 * so the calculator's first reading is a clean baseline (rate 0).
 *
 * ## Single sequential loop (correctness invariant)
 *
 * The body runs strictly one iteration at a time — there is never more than one in-flight
 * [XrayCore.queryStats] or [TrafficSink.onTrafficSample]. [ConnectionControllerImpl.onTrafficSample]'s
 * check-then-act relies on this (no concurrent samplers).
 *
 * A **fresh** [TrafficRateCalculator] is created per [start], so each tunnel session baselines from
 * zero rather than carrying the previous session's cumulative totals (the counter-reset guard would
 * self-heal it on the first sample anyway, but a clean start avoids the wasted first tick).
 *
 * @param xrayCore      Source of cumulative counters; [XrayCore.queryStats] returns `null` when
 *                      unavailable (no traffic yet / endpoint down / parse failure) — skipped.
 * @param trafficSink   Destination for derived samples (the controller).
 * @param elapsedNanos  Monotonic elapsed-real-time clock for the rate math; production passes
 *                      `SystemClock.elapsedRealtimeNanos` (see [VpnModule]). Injected for tests.
 * @param pollIntervalMs Poll cadence in ms. Defaults to [DEFAULT_POLL_INTERVAL_MS].
 */
internal class TrafficStatsPoller(
    private val xrayCore: XrayCore,
    private val trafficSink: TrafficSink,
    private val elapsedNanos: () -> Long,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) {

    /**
     * The active polling job, or `null` when idle. Not `@Volatile`: [start]/[stop] are only called
     * from `VpnTunnelService`'s main-thread-serialized lifecycle callbacks (same invariant as
     * [TunnelNotificationPresenter.job]).
     */
    private var job: Job? = null

    /**
     * Starts the polling loop on [scope], replacing any stale loop from a previous service start.
     *
     * @param scope The scope bounding the loop's lifetime — `VpnTunnelService.serviceScope`
     *              (Dispatchers.IO), where the blocking `queryStats()` GET belongs.
     * @return The launched [Job] (returned so tests can assert liveness).
     */
    fun start(scope: CoroutineScope): Job {
        job?.cancel()
        // Fresh calculator per session: baseline from this tunnel's counters, not the last one's.
        val calculator = TrafficRateCalculator(elapsedNanos)
        val j = scope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                val counters = xrayCore.queryStats() ?: continue
                // Cancelled during the (blocking, non-suspending) query — don't emit a late sample.
                if (!isActive) break
                trafficSink.onTrafficSample(calculator.sample(counters.rxBytes, counters.txBytes))
            }
        }
        job = j
        return j
    }

    /**
     * Stops the polling loop. Idempotent — safe to call from both `finishTeardown` and `onDestroy`.
     */
    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        /** Default poll cadence (~2s) — decoupled from the notification's ≤1/sec display budget. */
        const val DEFAULT_POLL_INTERVAL_MS = 2_000L
    }
}
