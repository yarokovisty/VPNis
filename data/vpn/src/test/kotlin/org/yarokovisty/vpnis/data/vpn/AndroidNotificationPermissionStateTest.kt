package org.yarokovisty.vpnis.data.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Robolectric unit tests for [AndroidNotificationPermissionState] (issue #127, task T-4; updated T-1).
 *
 * Verifies the two-part AND gate: [AndroidNotificationPermissionState.isGranted] is `true` only
 * when app-level notifications are enabled AND the tunnel channel's importance is not
 * [NotificationManager.IMPORTANCE_NONE].
 *
 * Also verifies that [AndroidNotificationPermissionState.refresh] returns the same value that is
 * subsequently emitted by [AndroidNotificationPermissionState.isGranted], and that
 * [AndroidNotificationPermissionState.channelId] equals the expected tunnel channel id.
 *
 * Robolectric's [ShadowNotificationManager] allows controlling both dimensions without a
 * real device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidNotificationPermissionStateTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        notificationManager = context.getSystemService(NotificationManager::class.java)
    }

    // -------------------------------------------------------------------------
    // Helper — create a channel with the given importance
    // -------------------------------------------------------------------------

    private fun createChannel(importance: Int) {
        val channel = NotificationChannel(
            TunnelNotifications.CHANNEL_ID,
            "VPNis Tunnel",
            importance,
        )
        notificationManager.createNotificationChannel(channel)
    }

    // -------------------------------------------------------------------------
    // Case 1: app-level enabled + channel importance != NONE → granted
    // -------------------------------------------------------------------------

    @Test
    fun `app enabled and channel importance not NONE EXPECT isGranted true after refresh`() = runTest {
        // Given — app-level notifications are enabled by default in Robolectric;
        //         create the channel with IMPORTANCE_LOW (the production default).
        createChannel(NotificationManager.IMPORTANCE_LOW)
        val state = AndroidNotificationPermissionState(context)

        // When
        val returned = state.refresh()

        // Then — refresh() return value matches the subsequent isGranted emission (no stale-read)
        assertTrue(returned)
        assertTrue(state.isGranted.first())
        assertEquals(returned, state.isGranted.first())
    }

    @Test
    fun `channelId EXPECT vpnis_tunnel`() {
        val state = AndroidNotificationPermissionState(context)
        assertEquals("vpnis_tunnel", state.channelId)
    }

    // -------------------------------------------------------------------------
    // Case 2: app-level enabled but channel importance == IMPORTANCE_NONE → not granted
    //         (I6: user silences the specific channel after app-level grant)
    // -------------------------------------------------------------------------

    @Test
    fun `app enabled but channel importance NONE EXPECT isGranted false after refresh`() = runTest {
        // Given — create the channel explicitly with IMPORTANCE_NONE (channel disabled).
        createChannel(NotificationManager.IMPORTANCE_NONE)
        val state = AndroidNotificationPermissionState(context)

        // When
        val returned = state.refresh()

        // Then — the two-part AND fails on the channel check;
        //         refresh() return value matches the subsequent isGranted emission
        assertFalse(returned)
        assertFalse(state.isGranted.first())
        assertEquals(returned, state.isGranted.first())
    }

    // -------------------------------------------------------------------------
    // Case 3: app-level notifications disabled → not granted (regardless of channel)
    // -------------------------------------------------------------------------

    @Test
    fun `app level notifications disabled EXPECT isGranted false after refresh`() = runTest {
        // Given — disable app-level notifications via the Robolectric shadow, then create
        //         a valid channel so the channel check alone would pass.
        val shadow = Shadows.shadowOf(notificationManager)
        shadow.setNotificationsEnabled(false)
        createChannel(NotificationManager.IMPORTANCE_LOW)
        val state = AndroidNotificationPermissionState(context)

        // When
        val returned = state.refresh()

        // Then — the two-part AND fails on the app-level check;
        //         refresh() return value matches the subsequent isGranted emission
        assertFalse(returned)
        assertFalse(state.isGranted.first())
        assertEquals(returned, state.isGranted.first())
    }
}
