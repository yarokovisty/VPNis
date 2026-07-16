package org.yarokovisty.vpnis.data.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

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
 *     - A monochrome small icon ([R.drawable.ic_stat_vpn])
 *     - Static content title/text (issue #63 provides live content via [contentFor])
 *     - A Disconnect action whose [PendingIntent] targets [VpnTunnelService] with
 *       [VpnTunnelService.ACTION_DISCONNECT]
 *
 * ## Pure content selection seam
 *
 * [contentFor] maps a [NotificationContent] model to a `(title, text)` pair. Keeping this
 * logic in a standalone function (not mixed into [build]) means:
 * - It is pure Kotlin — no Android context required — so it is directly unit-testable.
 * - Issue #63's ConnectionController can supply a [NotificationContent] with live server
 *   name / session timer and call [build] without touching channel or action logic.
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
     * Kept as a small positive constant so [VpnTunnelService] and any future
     * [updateNotification] callers use the same slot.
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
     * Builds a [Notification] for the tunnel foreground service.
     *
     * Content (title, text) is derived by [contentFor] from [content], making the
     * content model the only thing that changes between [build] calls during a live session.
     * Issue #63 will call [build] with a populated [NotificationContent] after the
     * ConnectionController updates the session state.
     *
     * @param context Used to create the [PendingIntent] and resolve string resources.
     * @param content Provides the title and body text. Defaults to [NotificationContent.Default]
     *   which shows the static "VPNis / Tunnel active" placeholder.
     */
    fun build(context: Context, content: NotificationContent = NotificationContent.Default): Notification {
        val (title, text) = contentFor(content)

        val disconnectPendingIntent = PendingIntent.getService(
            context,
            0, // requestCode — unused; 0 is sufficient for a single action per service
            Intent(VpnTunnelService.ACTION_DISCONNECT)
                .apply { setClass(context, VpnTunnelService::class.java) },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Suppress the "Tap to see all XXX notifications" footer in notification shade.
            .setShowWhen(false)
            .addAction(
                0, // icon — action icons are deprecated in API 23+; pass 0 per Material guidance
                context.getString(R.string.vpn_notification_action_disconnect),
                disconnectPendingIntent,
            )
            .build()
    }

    // -------------------------------------------------------------------------
    // Pure content-selection seam (unit-testable — no Context required)
    // -------------------------------------------------------------------------

    /**
     * Maps a [NotificationContent] model to a `(title, text)` string pair.
     *
     * This function is intentionally free of Android Context dependencies so it can be
     * unit-tested without Robolectric or a running device. Localisation of the strings
     * happens at the [build] call site where a Context is available.
     *
     * Issue #63 will expand [NotificationContent] with server name and session duration,
     * and add branches here — no changes to [build] will be needed.
     */
    internal fun contentFor(content: NotificationContent): Pair<String, String> = when (content) {
        is NotificationContent.Default -> Pair("VPNis", "Tunnel active")
        // TODO(#63): NotificationContent.Connected(serverName, elapsedSeconds) ->
        //   Pair("VPNis — Connected", "$serverName · ${formatDuration(elapsedSeconds)}")
    }
}

// =============================================================================
// NotificationContent — sealed model consumed by TunnelNotifications
// =============================================================================

/**
 * Represents the displayable content state for the tunnel foreground service notification.
 *
 * Keeping this as a sealed hierarchy decouples the notification text from both
 * [TunnelNotifications] (which builds the [android.app.Notification]) and from
 * [VpnTunnelService] (which just passes the latest content down).
 *
 * ## Seam for issue #63
 *
 * Issue #63's ConnectionController will produce instances of this class as its state
 * transitions, passing them to [VpnTunnelService.updateNotification]. Add new subtypes
 * here and handle them in [TunnelNotifications.contentFor] — no other file needs changing.
 *
 * @see TunnelNotifications.contentFor
 */
internal sealed interface NotificationContent {

    /**
     * Static placeholder used until issue #63's ConnectionController provides live state.
     *
     * Rendered as: title = "VPNis", text = "Tunnel active".
     */
    data object Default : NotificationContent

    // TODO(#63): data class Connected(val serverName: String, val elapsedSeconds: Long) : NotificationContent
}
