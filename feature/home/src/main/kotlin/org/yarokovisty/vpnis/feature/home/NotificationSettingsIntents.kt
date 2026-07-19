package org.yarokovisty.vpnis.feature.home

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Builds the system-settings deep-link [Intent]s used by the notification-permission denial UX
 * (issue #114). A stateless Intent factory — no permission logic, no Compose, no reference to any
 * `:data:vpn` type (the channel id is passed in as an opaque parameter).
 *
 * ## Why a deep-link at all
 *
 * On Android 13+ a permanently-denied `POST_NOTIFICATIONS`, or a tunnel channel silenced to
 * [android.app.NotificationManager.IMPORTANCE_NONE], suppresses the foreground-service notification
 * and its shade "Disconnect" action. The runtime dialog cannot be shown again once the permission is
 * permanently denied (the OS silently denies it), so the only remaining recovery path is to send the
 * user to the system notification settings. For a persistent, security-critical VPN state this is a
 * deliberate, justified exception to the "don't route users to settings" guideline (epic #126).
 *
 * ## Reuse
 *
 * Kept local to `:feature:home` for now (issue #114 is the only consumer). Extraction to a shared
 * presentation module is deferred to #131 (the Settings "Notifications" section), at which point the
 * second consumer justifies the move — this object is intentionally self-contained (only [Context] +
 * a `channelId` string) so that extraction is a mechanical move.
 */
internal object NotificationSettingsIntents {

    /**
     * Deep-links straight to the tunnel channel's notification settings
     * ([Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS], API 26+).
     *
     * Preferred primary target: it lands on the exact channel the user needs to re-enable (the common
     * "channel silenced" case), and still exposes the app-level toggle from there.
     *
     * @param context   any [Context]; [Context.getPackageName] identifies this app.
     * @param channelId the tunnel notification channel id (opaque, from
     *                  `NotificationPermissionState.channelId`).
     */
    fun channelNotificationSettings(context: Context, channelId: String): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Deep-links to the app-level notification settings page
     * ([Settings.ACTION_APP_NOTIFICATION_SETTINGS], API 26+).
     *
     * Fallback target used when the whole app is blocked at the app level (the channel page may be
     * unreachable). The app page lists every channel, so the user can re-enable from here too.
     *
     * @param context any [Context]; [Context.getPackageName] identifies this app.
     */
    fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
