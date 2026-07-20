package org.yarokovisty.vpnis.data.vpn

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.yarokovisty.vpnis.core.domain.model.TrafficStats

/**
 * Unit tests for [TrafficStatsPoller] — cadence, null-skip, stop semantics, and the fresh-calculator
 * -per-session baseline. Virtual time via `runTest` + `advanceTimeBy`; the rate clock is tied to the
 * scheduler's `currentTime` so rates are deterministic. No Android, no AAR.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrafficStatsPollerTest {

    @Test
    fun `poller emits a sample each interval with computed rates`() = runTest {
        val counters = ArrayDeque(listOf(TrafficCounters(1000, 200), TrafficCounters(3000, 600)))
        val xray = FakeXrayCore { counters.removeFirstOrNull() }
        val sink = RecordingTrafficSink()
        val poller = makePoller(xray, sink)
        poller.start(backgroundScope)

        // First poll at t=1000: baseline (rate 0), cumulative echoed.
        testScheduler.advanceTimeBy(1001)
        assertEquals(1, sink.samples.size)
        assertEquals(1000L, sink.samples[0].rxBytes)
        assertEquals(0L, sink.samples[0].rxBps)

        // Second poll at t=2000, 1s later: rate = delta bytes / 1s.
        testScheduler.advanceTimeBy(1000)
        assertEquals(2, sink.samples.size)
        assertEquals(3000L, sink.samples[1].rxBytes)
        assertEquals(2000L, sink.samples[1].rxBps) // (3000-1000)/1s
        assertEquals(400L, sink.samples[1].txBps) // (600-200)/1s

        poller.stop()
    }

    @Test
    fun `poller skips ticks where queryStats returns null`() = runTest {
        val results = ArrayDeque<TrafficCounters?>(listOf(null, TrafficCounters(500, 100)))
        val xray = FakeXrayCore { if (results.isEmpty()) null else results.removeFirst() }
        val sink = RecordingTrafficSink()
        val poller = makePoller(xray, sink)
        poller.start(backgroundScope)

        // t=1000: null → no sample.
        testScheduler.advanceTimeBy(1001)
        assertEquals(0, sink.samples.size)

        // t=2000: real counters → one sample.
        testScheduler.advanceTimeBy(1000)
        assertEquals(1, sink.samples.size)

        poller.stop()
    }

    @Test
    fun `no onTrafficSample fires after stop`() = runTest {
        val xray = FakeXrayCore { TrafficCounters(100, 100) }
        val sink = RecordingTrafficSink()
        val poller = makePoller(xray, sink)
        poller.start(backgroundScope)

        testScheduler.advanceTimeBy(1001)
        assertEquals(1, sink.samples.size)

        poller.stop()
        testScheduler.advanceTimeBy(10_000) // several intervals — none should produce a sample.
        assertEquals(1, sink.samples.size)
    }

    @Test
    fun `restart uses a fresh calculator so each session baselines to rate zero`() = runTest {
        // Counters keep climbing across sessions; a fresh calculator per start means the first
        // reading of the SECOND session is still rate 0 (not a spike from the previous baseline).
        val seq = ArrayDeque(listOf(TrafficCounters(1000, 1000), TrafficCounters(9000, 9000)))
        val xray = FakeXrayCore { seq.removeFirstOrNull() ?: TrafficCounters(9000, 9000) }
        val sink = RecordingTrafficSink()
        val poller = makePoller(xray, sink)

        poller.start(backgroundScope)
        testScheduler.advanceTimeBy(1001) // session 1, first reading → rate 0
        poller.stop()

        poller.start(backgroundScope)
        testScheduler.advanceTimeBy(1001) // session 2, first reading → rate 0 despite the counter jump
        poller.stop()

        assertEquals(2, sink.samples.size)
        assertEquals(0L, sink.samples.first().rxBps)
        assertEquals(0L, sink.samples.last().rxBps)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Poll interval 1000ms; rate clock tied to the test scheduler's virtual time. */
    private fun kotlinx.coroutines.test.TestScope.makePoller(xray: XrayCore, sink: TrafficSink): TrafficStatsPoller =
        TrafficStatsPoller(
            xrayCore = xray,
            trafficSink = sink,
            elapsedNanos = { testScheduler.currentTime * 1_000_000L },
            pollIntervalMs = 1000,
        )
}

/** Fake [XrayCore] whose [queryStats] yields successive values from [next]. */
private class FakeXrayCore(private val next: () -> TrafficCounters?) : XrayCore {
    override fun start(configJson: String, protector: VpnSocketProtector): Boolean = true
    override fun queryStats(): TrafficCounters? = next()
    override fun stop() = Unit
}

/** Recording [TrafficSink] capturing every delivered sample. */
private class RecordingTrafficSink : TrafficSink {
    val samples: MutableList<TrafficStats> = mutableListOf()
    override fun onTrafficSample(stats: TrafficStats) {
        samples += stats
    }
}
