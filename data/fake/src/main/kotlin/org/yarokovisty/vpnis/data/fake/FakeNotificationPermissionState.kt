package org.yarokovisty.vpnis.data.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.yarokovisty.vpnis.core.domain.permission.NotificationPermissionState

/**
 * Configurable fake implementation of [NotificationPermissionState] for use in the showcase build
 * and in unit/integration tests that exercise the [NotificationPermissionState] contract.
 *
 * ## Pull semantics — honest modelling
 *
 * The Android OS does NOT push permission changes to the app. The real
 * [AndroidNotificationPermissionState][org.yarokovisty.vpnis.data.vpn.AndroidNotificationPermissionState]
 * reads from system services only when [refresh] is called.
 *
 * This fake mirrors that pull model explicitly:
 * - [setGranted] writes a `private var backing` field (simulating a change in OS state) but does
 *   **NOT** emit anything on [isGranted]. Calling [setGranted] alone has no observable effect on
 *   collectors.
 * - [refresh] reads `backing` and pushes its value into the [MutableStateFlow] — exactly the
 *   same as the real impl reading from the OS on `ON_RESUME`.
 *
 * This design makes `refresh()` non-trivial (it is NOT a no-op): a test that calls
 * `setGranted(false)` and then checks `isGranted` before calling `refresh()` will still see `true`.
 * Only after `refresh()` will collectors see `false`. This falsifies a naïve implementation that
 * writes the flow directly from `setGranted`.
 *
 * ## Default
 *
 * `backing = true` at construction — notifications are considered granted by default, which is the
 * expected state in the showcase build and in happy-path tests that do not explicitly configure
 * permission denial.
 *
 * ## Thread safety
 *
 * This class is not thread-safe. It is designed for single-threaded test and showcase use. Do not
 * call [setGranted] and [refresh] concurrently from multiple threads.
 */
public class FakeNotificationPermissionState : NotificationPermissionState {

    /**
     * Backing field representing the "OS-side" permission state.
     *
     * Written by [setGranted]; read by [refresh]. Not observable until [refresh] is called —
     * this is intentional to model the pull semantics of the OS permission check.
     */
    private var backing = true

    private val _isGranted = MutableStateFlow(backing)

    override val isGranted: Flow<Boolean> = _isGranted.asStateFlow()

    /**
     * Opaque channel id returned as the deep-link target.
     *
     * Intentional literal duplicate of `TunnelNotifications.CHANNEL_ID` from `:data:vpn` —
     * `:data:fake` must NOT depend on `:data:vpn`, so a shared constant reference is a forbidden
     * cross-module dependency. The duplicate is deliberate and acceptable here.
     */
    @Suppress("MayBeConstant")
    private val CHANNEL_ID = "vpnis_tunnel"

    override val channelId: String get() = CHANNEL_ID

    /**
     * Simulates a change in OS notification permission state.
     *
     * Does **not** emit on [isGranted] — callers must subsequently call [refresh] to surface
     * the new value, exactly as the `ON_RESUME` lifecycle event triggers a [refresh] in
     * production.
     *
     * @param value The new OS-side permission state to be surfaced on the next [refresh] call.
     */
    public fun setGranted(value: Boolean) {
        backing = value
    }

    /**
     * Reads [backing], pushes it into [isGranted], and returns the same value.
     *
     * Mirrors the real implementation's OS read: a change made via [setGranted] is NOT visible
     * to [isGranted] collectors until this method is called. The return value is the same value
     * written to the backing [kotlinx.coroutines.flow.StateFlow] — not a read-back.
     */
    override suspend fun refresh(): Boolean {
        _isGranted.value = backing
        return backing
    }
}
