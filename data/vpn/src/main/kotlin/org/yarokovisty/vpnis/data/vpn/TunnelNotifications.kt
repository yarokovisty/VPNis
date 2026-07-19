package org.yarokovisty.vpnis.data.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.model.TrafficStats
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

    // -------------------------------------------------------------------------
    // Notification building
    // -------------------------------------------------------------------------

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

        val disconnectPendingIntent = PendingIntent.getService(
            context,
            0, // requestCode — unused; 0 is sufficient for a single action per service
            Intent(VpnTunnelService.ACTION_DISCONNECT)
                .apply { setClass(context, VpnTunnelService::class.java) },
            PendingIntent.FLAG_IMMUTABLE,
        )

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
                disconnectPendingIntent,
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
                // (setUsesChronometer + setWhen) — not embedded in this text (issue #128).
                context.getString(R.string.vpn_notification_text_connected, content.serverName)
        }
        return title to text
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
     * PermissionRequired / Error) map to [NotificationContent.Inactive], which
     * [TunnelNotificationPresenter] filters out so the tunnel notification is never posted for a
     * state without a live tunnel. Exhaustiveness makes a newly added [VpnConnectionState] a
     * compile error here rather than a silent gap.
     */
    internal fun contentFor(state: VpnConnectionState): NotificationContent = when (state) {
        is VpnConnectionState.Loading -> NotificationContent.Inactive
        is VpnConnectionState.Disconnected -> NotificationContent.Inactive
        is VpnConnectionState.PermissionRequired -> NotificationContent.Inactive
        is VpnConnectionState.Error -> NotificationContent.Inactive
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
     * No live tunnel (Loading / Disconnected / PermissionRequired / Error).
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
}
