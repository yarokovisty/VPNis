package org.yarokovisty.vpnis.data.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.model.ConnectionError
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
import org.yarokovisty.vpnis.core.format.BitrateUnit
import org.yarokovisty.vpnis.core.format.FormattedBitrate
import org.yarokovisty.vpnis.core.format.formatBitrate
import java.time.Instant

/**
 * Encapsulates all notification logic for the VPNis tunnel foreground service.
 *
 * ## Responsibilities
 *
 * - Defines the notification channel constants ([CHANNEL_ID], [NOTIFICATION_ID]).
 * - Creates the notification channel on API 26+ — our minSdk IS 26, so the channel API is
 *   always available; no runtime version guard is needed, but this is called in `onCreate`
 *   (before [build]) so the channel exists before the first notification is posted.
 * - Builds the [Notification] object via [NotificationCompat.Builder] with:
 *     - `ongoing = true` (cannot be dismissed while the tunnel runs)
 *     - `PRIORITY_LOW` (low-importance status-bar slot; no sound, no heads-up)
 *     - `setOnlyAlertOnce(true)` so live-content re-`notify()`s (issue #127's presenter, #128's
 *       server name / timer, #130's traffic) never re-alert — epic #126 DoD "0 heads-up/sound"
 *     - A monochrome small icon ([R.drawable.ic_stat_vpn])
 *     - State-driven content resolved by [contentFor] + rendered by [build]
 *     - A Disconnect action whose [PendingIntent] targets [VpnTunnelService] with
 *       [VpnTunnelService.ACTION_DISCONNECT]
 *
 * ## Pure content-selection seam (issue #127)
 *
 * [contentFor] maps a [VpnConnectionState] to a data-carrying [NotificationContent]. It is a pure
 * Kotlin function — no Android context — so it is directly unit-testable without Robolectric.
 * Crucially it carries **data** ([NotificationContent.Connected.since], server name, traffic), NOT
 * pre-formatted strings: localisation happens in [build] via `getString`, so the mapper stays pure
 * and Context-free while the displayed strings are still localisable. [TunnelNotificationPresenter]
 * (issue #127) drives the pipeline `state.map { contentFor(it) }.distinctUntilChanged()` → [build] →
 * `notify()`; #128/#130 extend the [NotificationContent] subtypes without touching [build]'s wiring.
 *
 * ## Issue #106 / #67 dependency
 *
 * The [PendingIntent.FLAG_IMMUTABLE] flag is used; `FLAG_MUTABLE` is intentionally avoided
 * (it requires an explicit justification on API 31+). The manifest `<service>` declaration
 * with `android:foregroundServiceType="specialUse"` and the
 * `FOREGROUND_SERVICE_SPECIAL_USE` permission are registered in issue #106.
 */
internal object TunnelNotifications {

    /**
     * Notification channel ID for the tunnel foreground service.
     *
     * The channel ID must remain stable across app upgrades because Android stores the
     * user's per-channel settings (importance, sound, etc.) against this string.
     * Changing it would reset any customisation the user made.
     */
    const val CHANNEL_ID = "vpnis_tunnel"

    /**
     * Stable notification ID for the tunnel foreground service notification.
     *
     * Must not be 0 — the system rejects 0 for foreground service notifications.
     * Kept as a small positive constant so [VpnTunnelService] and [TunnelNotificationPresenter]
     * (the sole owner of this slot, issue #127) use the same ID.
     */
    const val NOTIFICATION_ID = 1001

    /**
     * Notification channel ID for the interrupting error alert (issue #129).
     *
     * Separate from [CHANNEL_ID] because the tunnel channel is [NotificationManager.IMPORTANCE_LOW]
     * (no heads-up / no sound) and its notification is `ongoing` (cannot be dismissed). An
     * unexpected mid-session drop must produce a **dismissible heads-up**, which requires a
     * dedicated [NotificationManager.IMPORTANCE_DEFAULT] channel.
     */
    const val ALERT_CHANNEL_ID = "vpnis_alerts"

    /**
     * Stable notification ID for the error alert (issue #129).
     *
     * Distinct from [NOTIFICATION_ID] so the alert lives in its own slot: the FGS teardown's
     * `stopForeground(REMOVE)` + `cancel(NOTIFICATION_ID)` sweep does not remove it, and it stays
     * until the user dismisses it (`setAutoCancel(true)`, not `ongoing`).
     */
    const val ALERT_NOTIFICATION_ID = 1002

    // -------------------------------------------------------------------------
    // Channel
    // -------------------------------------------------------------------------

    /**
     * Creates the notification channel for the tunnel service.
     *
     * Safe to call multiple times — [NotificationManager.createNotificationChannel] is
     * idempotent for a given channel ID (the OS merges settings the user has not changed).
     *
     * [NotificationChannel] is available since API 26 which is our minSdk, so no version
     * guard is needed here.
     *
     * @param context Any context; used to retrieve [NotificationManager] system service.
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.vpn_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.vpn_notification_channel_description)
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Creates the notification channel for the interrupting error alert (issue #129).
     *
     * [NotificationManager.IMPORTANCE_DEFAULT] so the alert produces a heads-up — deliberately
     * distinct from the [CHANNEL_ID] tunnel channel ([NotificationManager.IMPORTANCE_LOW]) whose
     * epic-DoD contract is "0 heads-up/sound". Idempotent, so safe to call on every `onCreate`.
     *
     * @param context Any context; used to retrieve [NotificationManager] and resolve strings.
     */
    fun createAlertChannel(context: Context) {
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            context.getString(R.string.vpn_alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.vpn_alert_channel_description)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    // -------------------------------------------------------------------------
    // Notification building
    // -------------------------------------------------------------------------

    /**
     * Cached disconnect [PendingIntent] (issue #130).
     *
     * The action target (service + [VpnTunnelService.ACTION_DISCONNECT]) and flags
     * ([PendingIntent.FLAG_IMMUTABLE]) are constant, and [build] now runs up to once per second on
     * a live-traffic session — so building it once avoids a per-`notify()` binder round-trip through
     * [PendingIntent.getService]. Built from the **application** context so the cached instance is
     * safe for the process lifetime. `@Volatile` for visibility across the presenter's Default/IO
     * dispatchers; a benign double-create is harmless (the OS returns an equivalent PendingIntent).
     */
    @Volatile
    private var cachedDisconnectPendingIntent: PendingIntent? = null

    /**
     * Returns the cached disconnect [PendingIntent], creating it on first use from the application
     * context (see [cachedDisconnectPendingIntent]).
     */
    private fun disconnectPendingIntent(context: Context): PendingIntent =
        cachedDisconnectPendingIntent ?: PendingIntent.getService(
            context.applicationContext,
            0, // requestCode — unused; 0 is sufficient for a single action per service
            Intent(VpnTunnelService.ACTION_DISCONNECT)
                .apply { setClass(context.applicationContext, VpnTunnelService::class.java) },
            PendingIntent.FLAG_IMMUTABLE,
        ).also { cachedDisconnectPendingIntent = it }

    /**
     * Clears the [cachedDisconnectPendingIntent]. Test-only: this is a process-lifetime `object`, so
     * without a reset the cache (built from the first test's Robolectric application context) leaks
     * across test classes in the same JVM run. Call from a Robolectric test `@Before`.
     */
    @VisibleForTesting
    internal fun resetCachesForTest() {
        cachedDisconnectPendingIntent = null
    }

    /**
     * Builds a [Notification] for the tunnel foreground service from a [NotificationContent].
     *
     * [content] is rendered to a localised `(title, text)` pair here (Context available), keeping
     * [contentFor] pure. The default [NotificationContent.Inactive] renders the static
     * "VPNis / Tunnel active" copy used for the initial [android.app.Service.startForeground] post —
     * byte-for-byte identical to the pre-#127 notification. Live states ([NotificationContent.Connected]
     * / [NotificationContent.Connecting]) are posted by [TunnelNotificationPresenter] as the connection
     * state changes.
     *
     * @param context Used to create the [PendingIntent] and resolve string resources.
     * @param content The data-carrying content model. Defaults to [NotificationContent.Inactive].
     */
    fun build(context: Context, content: NotificationContent = NotificationContent.Inactive): Notification {
        val (title, text) = render(context, content)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Live content re-posts the notification (presenter, #128 timer, #130 traffic); alert
            // only on the first post so the low-importance channel never produces sound/heads-up.
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                0, // icon — action icons are deprecated in API 23+; pass 0 per Material guidance
                context.getString(R.string.vpn_notification_action_disconnect),
                disconnectPendingIntent(context),
            )

        // Session timer (issue #128) — Context-side wiring; the mapper stays pure and carries only
        // `since: Instant`. Only the Connected state shows a running chronometer.
        applyTimer(builder, content)

        return builder.build()
    }

    /**
     * Applies the session-timer fields to [builder] for the [NotificationContent.Connected] state,
     * and suppresses the timestamp footer for every other state.
     *
     * For [NotificationContent.Connected] this sets the notification `when` to
     * [NotificationContent.Connected.since] and enables the chronometer, so the system renders a
     * live "counting up" session timer. Crucially the **system** ticks the chronometer once per
     * second on its own — the presenter does **not** re-`notify()` per tick (epic #126 DoD: ≤1
     * `notify()`/sec). Because `since` is captured once at `onTunnelEstablished` and never recomputed
     * on a `Connected → Connected` traffic refresh, the timer is monotonic and never jumps or resets
     * while the session is alive.
     *
     * `java.time.Instant.toEpochMilli()` is available natively on API 26 (our minSdk), so no core
     * library desugaring is required.
     *
     * For non-Connected states, [NotificationCompat.Builder.setShowWhen] is disabled to suppress the
     * "Tap to see all XXX notifications" timestamp footer in the notification shade.
     */
    private fun applyTimer(builder: NotificationCompat.Builder, content: NotificationContent) {
        if (content is NotificationContent.Connected) {
            builder
                .setWhen(content.since.toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
        } else {
            builder.setShowWhen(false)
        }
    }

    /**
     * Renders a [NotificationContent] to a localised `(title, text)` pair.
     *
     * The only place strings are resolved — [contentFor] stays Context-free. #128 replaces the
     * [NotificationContent.Connected] branch with server name + a session timer.
     */
    private fun render(context: Context, content: NotificationContent): Pair<String, String> {
        val title = context.getString(R.string.vpn_notification_title)
        val text = when (content) {
            is NotificationContent.Inactive ->
                context.getString(R.string.vpn_notification_text_active)
            is NotificationContent.Connecting ->
                context.getString(R.string.vpn_notification_text_connecting, content.serverName)
            is NotificationContent.Connected ->
                // The monotonic session timer is rendered as a chronometer via [applyTimer]
                // (setUsesChronometer + setWhen) — not embedded in this text (issue #128). When live
                // traffic is available (issue #130) the body shows down/up rates; otherwise it falls
                // back to the plain "Connected · <server>" copy until the first sample lands.
                content.traffic?.let { traffic ->
                    context.getString(
                        R.string.vpn_notification_text_connected_traffic,
                        content.serverName,
                        rateText(context, traffic.rxBps),
                        rateText(context, traffic.txBps),
                    )
                } ?: context.getString(R.string.vpn_notification_text_connected, content.serverName)
            is NotificationContent.Error ->
                // Error content is surfaced out-of-band by [buildAlert] on the [ALERT_CHANNEL_ID]
                // channel (issue #129), never as the ongoing slot-1001 notification. The presenter's
                // onEach routes Error to buildAlert, so this branch is unreachable — fail loudly if
                // a future caller ever puts Error through build()/render().
                error("Error content must be routed to buildAlert, not rendered as ongoing")
        }
        return title to text
    }

    /**
     * Formats a bytes-per-second rate as a localised "value unit" string (e.g. "1.2 MB/s", RU
     * "1.2 МБ/с") for the notification traffic line (issue #130).
     *
     * Uses the shared [formatBitrate] so the bucketing/rounding matches the Home traffic tiles
     * exactly; the [BitrateUnit] → label mapping is resolved from this module's own string resources
     * ([unitLabel]) — keeping [formatBitrate] free of Android/i18n while the displayed copy stays
     * localisable (the same data-carrying pattern as [contentFor]).
     */
    private fun rateText(context: Context, bps: Long): String {
        val formatted: FormattedBitrate = formatBitrate(bps)
        return "${formatted.value} ${unitLabel(context, formatted.unit)}"
    }

    /** Resolves a [BitrateUnit] bucket to its localised label from this module's resources. */
    private fun unitLabel(context: Context, unit: BitrateUnit): String = when (unit) {
        BitrateUnit.BYTES -> context.getString(R.string.vpn_traffic_unit_bps)
        BitrateUnit.KILOBYTES -> context.getString(R.string.vpn_traffic_unit_kbps)
        BitrateUnit.MEGABYTES -> context.getString(R.string.vpn_traffic_unit_mbps)
    }

    // -------------------------------------------------------------------------
    // Error alert (issue #129) — separate dismissible heads-up on ALERT_CHANNEL_ID
    // -------------------------------------------------------------------------

    /**
     * Builds the interrupting error alert for an unexpected mid-session tunnel drop (issue #129).
     *
     * Unlike the ongoing FGS notification this is **dismissible** (`setAutoCancel(true)`, not
     * `setOngoing`) and lives on the [ALERT_CHANNEL_ID] ([NotificationManager.IMPORTANCE_DEFAULT])
     * channel so it produces a heads-up. Tapping it opens the app via the launcher intent —
     * resolved through [android.content.pm.PackageManager.getLaunchIntentForPackage] so `:data:vpn`
     * never references `:app`'s Activity (epic #126 dependency-direction guard). If no launchable
     * activity resolves (`null`), the alert is built without a content intent rather than crashing.
     *
     * @param context Application context (as [build]); resolves strings, the launch intent, and the
     *                [NotificationManager]. Rebuilt per `notify()` — nothing is cached.
     * @param reason  The failure reason, rendered to localized copy by [alertTextFor]. A
     *                [ConnectionError.Unknown] never surfaces its raw `message` (logs only).
     */
    fun buildAlert(context: Context, reason: ConnectionError): Notification {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(context.getString(R.string.vpn_alert_title))
            .setContentText(alertTextFor(context, reason))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        return builder.build()
    }

    /**
     * Resolves a [ConnectionError] to localized alert body copy (issue #129).
     *
     * Exhaustive over [ConnectionError]. [ConnectionError.Unknown]'s `message` is **never** rendered
     * into the notification (it may carry technical/credential detail) — it maps to the generic body,
     * as do the non-drop reasons that cannot realistically reach an active-tunnel alert.
     */
    private fun alertTextFor(context: Context, reason: ConnectionError): String = when (reason) {
        is ConnectionError.ServerUnreachable ->
            context.getString(R.string.vpn_alert_text_server_unreachable)
        is ConnectionError.TunnelSetupFailed ->
            context.getString(R.string.vpn_alert_text_tunnel_failed)
        is ConnectionError.Revoked,
        is ConnectionError.PermissionDenied,
        is ConnectionError.Unknown,
        ->
            context.getString(R.string.vpn_alert_text_generic)
    }

    // -------------------------------------------------------------------------
    // Pure content-selection seam (unit-testable — no Context required)
    // -------------------------------------------------------------------------

    /**
     * Maps a [VpnConnectionState] to a data-carrying [NotificationContent].
     *
     * Pure and Context-free so it is unit-testable without Robolectric. Localisation happens in
     * [build]/[render] where a Context is available; this function carries only data.
     *
     * Total `when` over [VpnConnectionState]: non-active states (Loading / Disconnected /
     * PermissionRequired) map to [NotificationContent.Inactive], which [TunnelNotificationPresenter]
     * filters out so the tunnel notification is never posted for a state without a live tunnel.
     * [VpnConnectionState.Error] maps to [NotificationContent.Error] carrying the reason, which the
     * presenter routes to the out-of-band alert (issue #129). Exhaustiveness makes a newly added
     * [VpnConnectionState] a compile error here rather than a silent gap.
     */
    internal fun contentFor(state: VpnConnectionState): NotificationContent = when (state) {
        is VpnConnectionState.Loading -> NotificationContent.Inactive
        is VpnConnectionState.Disconnected -> NotificationContent.Inactive
        is VpnConnectionState.PermissionRequired -> NotificationContent.Inactive
        // Error carries the reason so the presenter can render the out-of-band alert (issue #129).
        // Only a running presenter (active tunnel) sees this — setup-time errors never reach it.
        is VpnConnectionState.Error -> NotificationContent.Error(reason = state.reason)
        is VpnConnectionState.Connecting -> NotificationContent.Connecting(serverName = state.server.name)
        is VpnConnectionState.Connected -> NotificationContent.Connected(
            serverName = state.server.name,
            since = state.since,
            traffic = state.traffic,
        )
    }
}

// =============================================================================
// NotificationContent — sealed model consumed by TunnelNotifications
// =============================================================================

/**
 * The displayable content state for the tunnel foreground service notification.
 *
 * Carries **data only** — no pre-formatted or localised strings. [TunnelNotifications.render]
 * turns this into localised title/text at build time. This decouples the notification copy from
 * both [TunnelNotifications] (which builds the [android.app.Notification]) and the connection
 * state machine, and keeps [TunnelNotifications.contentFor] pure.
 *
 * @see TunnelNotifications.contentFor
 */
internal sealed interface NotificationContent {

    /**
     * No live tunnel (Loading / Disconnected / PermissionRequired). [Error] is a distinct subtype
     * routed to the out-of-band alert, not folded in here (issue #129).
     *
     * Rendered as the static "VPNis / Tunnel active" copy for the initial `startForeground` post;
     * [TunnelNotificationPresenter] filters this out and never `notify()`s it during a session.
     */
    data object Inactive : NotificationContent

    /** A connection attempt is in progress for [serverName]. */
    data class Connecting(val serverName: String) : NotificationContent

    /**
     * The tunnel is active.
     *
     * @param serverName the connected server's display name.
     * @param since       the moment the tunnel became active (issue #128 renders it as a timer).
     * @param traffic     live traffic counters, or `null` when not yet available (issue #130).
     */
    data class Connected(val serverName: String, val since: Instant, val traffic: TrafficStats?) : NotificationContent

    /**
     * The active tunnel dropped unexpectedly with [reason] (issue #129).
     *
     * Routed by [TunnelNotificationPresenter] to a **separate** dismissible heads-up alert
     * ([TunnelNotifications.buildAlert] on [TunnelNotifications.ALERT_CHANNEL_ID]) — never rendered
     * on the ongoing slot ([TunnelNotifications.render] rejects it). Only a running presenter (i.e.
     * an already-established tunnel) observes this; setup-time errors never reach it.
     */
    data class Error(val reason: ConnectionError) : NotificationContent
}
