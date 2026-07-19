package org.yarokovisty.vpnis.data.vpn

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.yarokovisty.vpnis.core.domain.permission.NotificationPermissionState

/**
 * Android production implementation of [NotificationPermissionState].
 *
 * ## Two-part gate
 *
 * A notification is suppressed when **either** of the following is true:
 * 1. The user has disabled notifications for the app at the system level
 *    (`NotificationManagerCompat.areNotificationsEnabled() == false`).
 * 2. The user has silenced the specific notification channel by setting its importance to
 *    [NotificationManager.IMPORTANCE_NONE] (Settings → App → Notifications → [channel name]).
 *
 * Both conditions must pass for [isGranted] to emit `true`. Using both APIs is necessary because
 * [NotificationManagerCompat] does NOT expose `getNotificationChannel`, so the channel check
 * requires the framework [NotificationManager] (the same one [TunnelNotifications.createChannel]
 * already uses).
 *
 * The [TunnelNotifications.CHANNEL_ID] constant is referenced here — it is `internal` to
 * `:data:vpn`, keeping channel-identity knowledge out of `:core:domain`. The domain contract
 * ([NotificationPermissionState]) remains channel-agnostic.
 *
 * ## Pull semantics
 *
 * The OS does not push permission changes to the app. [isGranted] is backed by a
 * [MutableStateFlow] that is updated **only** on [refresh] calls. The initial seed value is
 * computed at construction so collectors always receive an immediate emission.
 *
 * ## Threading
 *
 * The system-service reads in [refresh] are fast (typically < 1 ms), but [withContext] hops to
 * [Dispatchers.Default] defensively to keep the call off the Main thread regardless of the
 * calling dispatcher. `getSystemService` is obtained from the application Context, which is safe
 * to call from any thread.
 *
 * @param context Application [Context]; must outlive this object. Inject via `androidContext()`.
 */
internal class AndroidNotificationPermissionState(private val context: Context) : NotificationPermissionState {

    private val notificationManagerCompat = NotificationManagerCompat.from(context)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val _isGranted = MutableStateFlow(computeIsGranted())

    override val isGranted: Flow<Boolean> = _isGranted.asStateFlow()

    /**
     * Returns [TunnelNotifications.CHANNEL_ID] as an opaque deep-link target.
     *
     * This value is used by the UI layer to build a channel-level notification-settings Intent.
     * [TunnelNotifications.CHANNEL_ID] remains `internal` to `:data:vpn`; the domain contract
     * only exposes it as an opaque [String], keeping channel-identity knowledge out of `:core:domain`.
     */
    override val channelId: String get() = TunnelNotifications.CHANNEL_ID

    override suspend fun refresh(): Boolean {
        val granted = withContext(Dispatchers.Default) { computeIsGranted() }
        _isGranted.value = granted
        return granted
    }

    /**
     * Returns `true` when the OS will display the app's tunnel notifications:
     * - app-level notifications are enabled, AND
     * - the tunnel channel ([TunnelNotifications.CHANNEL_ID]) has importance other than
     *   [NotificationManager.IMPORTANCE_NONE] (i.e. the user has not silenced it individually).
     *
     * A missing channel (not yet created) is treated as *not silenced* — [getNotificationChannel]
     * returns `null` before [TunnelNotifications.createChannel] is called. The channel is created
     * in `VpnTunnelService.onCreate()` before [refresh] is first called from a consumer.
     */
    private fun computeIsGranted(): Boolean {
        val appLevelEnabled = notificationManagerCompat.areNotificationsEnabled()
        val channelEnabled = notificationManager
            .getNotificationChannel(TunnelNotifications.CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE
        return appLevelEnabled && channelEnabled
    }
}
