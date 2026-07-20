package org.yarokovisty.vpnis.data.vpn

import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sole owner of the tunnel foreground service notification slot ([TunnelNotifications.NOTIFICATION_ID]).
 *
 * Subscribes to [ConnectionController.state], maps each state to a [NotificationContent] via
 * [TunnelNotifications.contentFor], and calls [NotificationManager.notify] for every content change
 * that represents an active tunnel state.
 *
 * ## Lifecycle
 *
 * This class is a Koin `single` — it lives for the process lifetime and may outlive individual
 * [VpnTunnelService] starts. Lifecycle is driven explicitly by the service:
 * - [start] is called by the service on the success path (after `stateSink.onTunnelEstablished()`).
 * - [stop] is called by the service in `finishTeardown` (before `stateSink.onTunnelStopped()`)
 *   and in `onDestroy` (the low-memory-kill path that may skip `finishTeardown`).
 *
 * **Invariant:** [start] and [stop] are only called from `VpnTunnelService`'s lifecycle callbacks,
 * which are serialized on the Android main thread. `job` is therefore thread-confined and is
 * intentionally **not** `@Volatile`.
 *
 * ## Zombie-notification defence (layered)
 *
 * Three guards are layered so the design is correct even when coroutine cancellation ordering is
 * adversarial:
 *
 * 1. **Active-state filter (primary):** `filter { it !is NotificationContent.Inactive }` drops
 *    non-tunnel states before any notification is posted. This is the primary guard because
 *    `ConnectionControllerImpl.disconnect()` emits `Disconnected` **synchronously** at the start
 *    of teardown, well before `finishTeardown` — an ordering-only fix cannot catch it.
 * 2. **[AtomicBoolean] gate (secondary):** `if (active.get())` immediately before `notify()` drops
 *    any emission that passed the filter but was already in the `flowOn` hand-off when [stop] was
 *    called. The `AtomicBoolean` provides the happens-before edge across the Default→collector
 *    dispatcher hop.
 * 3. **Final sweep (tertiary):** `VpnTunnelService.finishTeardown` calls
 *    `notificationManager.cancel(NOTIFICATION_ID)` immediately after `stopForeground` as an
 *    idempotent sweep that self-heals the narrow sub-frame race where a notification was posted
 *    between steps 1 and 2.
 *
 * ## Threading
 *
 * - `map / filter / distinctUntilChanged` run on [mapDispatcher] (default: [Dispatchers.Default])
 *   via `flowOn`.
 * - `onEach { notify() }` runs on the **collector's dispatcher**, which is the `serviceScope`
 *   dispatcher (i.e. [Dispatchers.IO]) — off Main, so `notify()` never blocks the UI thread.
 * - **Precondition:** [start] requires a **non-Main** scope. A Main-dispatched scope would move
 *   `notify()` onto the Main thread, which is unnecessary and potentially janky.
 *
 * ## `distinctUntilChanged` + throttle scope (issue #130)
 *
 * `distinctUntilChanged` collapses repeated identical [NotificationContent] values (e.g. state-machine
 * churn mapping to the same `Connecting("Server")` twice). On its own it does **not** rate-limit
 * live-traffic updates once [NotificationContent.Connected.traffic] changes every tick, so the
 * pipeline additionally `sample(1s)`s the **Connected sub-stream only**: the deduped stream is split
 * into `Connected` (sampled → ≤1 `notify()`/sec, epic #126 DoD) and everything else (`Connecting` /
 * `Error`, passed through immediately so lifecycle transitions and the #129 drop alert are never
 * delayed by a sample window), then merged. The sampler sits upstream of `flowOn(mapDispatcher)` so
 * its ticker runs on Default, off the collector.
 *
 * @param connectionController Source of [org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState].
 * @param appContext            Application [Context]; used to resolve [NotificationManager] once at
 *                             construction and passed to [TunnelNotifications.build] for string
 *                             resolution. Must be the application context (outlives the service).
 * @param mapDispatcher        Dispatcher for the `map / filter / distinct` pipeline stages.
 *                             Defaults to [Dispatchers.Default]. Inject a [kotlinx.coroutines.test.StandardTestDispatcher]
 *                             in tests to make time controllable.
 */
internal class TunnelNotificationPresenter(
    private val connectionController: ConnectionController,
    appContext: Context,
    private val mapDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * Cached [NotificationManager] handle. Fetched once at construction from the application
     * context — safe to cache because the manager is a process-singleton system service.
     */
    private val notificationManager: NotificationManager =
        appContext.getSystemService(NotificationManager::class.java)

    /**
     * The application context kept for passing to [TunnelNotifications.build].
     *
     * Holding the application context (not a service context) is safe: the application outlives
     * any individual service instance, so there is no leak.
     */
    private val context: Context = appContext

    /**
     * The active collection job, or `null` when the presenter is idle.
     *
     * Not `@Volatile` — see the class-level invariant: [start] and [stop] are always called from
     * `VpnTunnelService`'s main-thread-serialized lifecycle callbacks.
     */
    private var job: Job? = null

    /**
     * Gate that prevents `notify()` calls after [stop] has been called.
     *
     * [AtomicBoolean] is used (rather than a plain `Boolean`) to provide a happens-before edge
     * across the [mapDispatcher] → collector-dispatcher thread hop so that a [stop] on the main
     * thread is immediately visible to an `onEach` body running on [Dispatchers.IO].
     */
    private val active = AtomicBoolean(false)

    /**
     * Dedup gate for the error alert (issue #129): at most one alert per reconnect chain.
     *
     * A "chain" today equals one presenter session ([start] → drop → [stop]) — there is no
     * auto-reconnect yet, so the tunnel is torn down on the first drop. Reset to `false` in [start]
     * (a fresh session begins a fresh chain — this is what stops a process-lifetime `single` from
     * carrying a stale `true` into a new session and swallowing its first genuine error) and,
     * defensively, on a `Connected` emission (future-proofs an in-session reconnect once a
     * `Reconnecting` domain state / #64 land).
     *
     * `@Volatile` (not [AtomicBoolean] — unlike [active], no cross-dispatcher CAS is needed): the
     * only in-flight writer/reader is the single collector `onEach`, and the [start] reset runs after
     * `job?.cancel()` has cancelled any prior collector, so there is no *concurrent* second writer —
     * only a visibility barrier is required.
     */
    @Volatile
    private var alertPosted = false

    /**
     * Starts the notification pipeline, replacing any stale pipeline from a previous service start.
     *
     * The defensive `job?.cancel()` at the top makes [start] re-entrant across service
     * start→low-memory-kill→start cycles: a process-lifetime `single` may retain a non-null `job`
     * and a dead `serviceScope` reference if a prior `onDestroy` did not fully clean up. Cancelling
     * the stale job first prevents double-collection / orphaned collectors.
     *
     * @param scope The [CoroutineScope] that bounds the collection lifetime. Must NOT be a
     *              Main-dispatched scope (see class KDoc). Typically `VpnTunnelService.serviceScope`
     *              (backed by [Dispatchers.IO]).
     * @return The launched [Job], returned so tests can assert liveness (no-leak acceptance).
     */
    fun start(scope: CoroutineScope): Job {
        // Cancel any stale job from a previous (possibly un-stopped) service start.
        job?.cancel()
        active.set(true)
        // Fresh session = fresh reconnect chain: a prior session must not carry a stale gate
        // that would swallow this session's first error (issue #129).
        alertPosted = false

        val j = contentPipeline()
            .flowOn(mapDispatcher)
            .onEach { content ->
                // Secondary guard: drops an emission that was in the flowOn hand-off when stop() fired.
                if (active.get()) {
                    // Exhaustive over all four NotificationContent subtypes (not relying on the
                    // upstream filter to type-narrow, which Kotlin does not do) so a future subtype
                    // is a compile error. Inactive is dropped by the filter above → unreachable.
                    when (content) {
                        is NotificationContent.Connecting ->
                            postOngoing(content)
                        is NotificationContent.Connected -> {
                            // A healthy session ends the previous reconnect chain (issue #129).
                            alertPosted = false
                            postOngoing(content)
                        }
                        is NotificationContent.Error ->
                            // Out-of-band dismissible alert on ALERT_CHANNEL_ID (slot 1002), never
                            // the ongoing slot 1001 — at most one per chain (issue #129).
                            if (!alertPosted) {
                                alertPosted = true
                                notificationManager.notify(
                                    TunnelNotifications.ALERT_NOTIFICATION_ID,
                                    TunnelNotifications.buildAlert(context, content.reason),
                                )
                            }
                        is NotificationContent.Inactive ->
                            error("Inactive is filtered upstream")
                    }
                }
            }
            .launchIn(scope)

        job = j
        return j
    }

    /**
     * Builds the throttled notification-content stream consumed by [start] (issue #130).
     *
     * The deduped active-tunnel content is split by type and re-merged so that:
     * - `Connecting` / `Error` pass through **immediately** (lifecycle transitions and the #129 drop
     *   alert are never delayed by a sample window — review R3);
     * - the `Connected` traffic-refresh sub-stream is `sample`d to at most one emission per
     *   [NOTIFY_THROTTLE_MS], bounding the ongoing notification to ≤1 `notify()`/sec (epic #126 DoD)
     *   independently of the poll cadence.
     *
     * `contents` is cold and collected twice (once per branch); the source is a conflated StateFlow
     * and `contentFor` is pure, so re-collection is cheap. Extracted as an `internal` seam so the
     * throttle is unit-testable by counting emissions directly (Robolectric cannot count re-`notify()`s
     * to the same slot). Callers apply their own `flowOn`; `sample` stays upstream of it so the ticker
     * runs off the collector.
     */
    @OptIn(FlowPreview::class) // Flow.sample is @FlowPreview
    internal fun contentPipeline(): Flow<NotificationContent> {
        val contents = connectionController.state
            .map { TunnelNotifications.contentFor(it) }
            .filter { it !is NotificationContent.Inactive }
            .distinctUntilChanged()

        return merge(
            contents.filter { it !is NotificationContent.Connected },
            contents.filterIsInstance<NotificationContent.Connected>().sample(NOTIFY_THROTTLE_MS),
        )
    }

    /**
     * Posts the ongoing foreground-service notification to slot [TunnelNotifications.NOTIFICATION_ID]
     * for an active-tunnel [content] ([NotificationContent.Connecting] / [NotificationContent.Connected]).
     */
    private fun postOngoing(content: NotificationContent) {
        notificationManager.notify(
            TunnelNotifications.NOTIFICATION_ID,
            TunnelNotifications.build(context, content),
        )
    }

    /**
     * Stops the notification pipeline and prevents any further `notify()` calls.
     *
     * The gate is flipped to `false` **before** the job is cancelled so that any emission already
     * past the filter and suspended in the `flowOn` hand-off cannot slip through and call
     * `notify()` after this method returns. Idempotent — safe to call from both
     * `finishTeardown` and `onDestroy`.
     */
    fun stop() {
        active.set(false)
        job?.cancel()
        job = null
    }

    private companion object {
        /**
         * Sample window for the Connected traffic-refresh sub-stream — bounds the ongoing
         * notification to ≤1 `notify()`/sec (epic #126 DoD). Decoupled from the poll cadence
         * ([TrafficStatsPoller.DEFAULT_POLL_INTERVAL_MS]); the poll may be faster or slower without
         * changing the notify rate.
         */
        const val NOTIFY_THROTTLE_MS = 1_000L
    }
}
