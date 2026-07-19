package org.yarokovisty.vpnis.feature.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.yarokovisty.vpnis.core.domain.permission.NotificationPermissionState

/**
 * Test double for [NotificationPermissionState].
 *
 * Backed by a [MutableStateFlow] so tests can inspect [isGranted] emissions and control
 * the gate via [setGranted]. [refresh] publishes [backing] into the flow and returns it —
 * matching the pull semantics of the real implementation (red-team 6: computes the value
 * fresh and returns the same computed value, not a read-back of the already-stored value).
 *
 * Kept minimal and internal — scoped to `:feature:home` tests. `:data:fake`'s
 * `FakeNotificationPermissionState` is NOT on the home test classpath (the modules share
 * no test dependency), so this local double is necessary.
 */
internal class FakeNotificationPermissionState(
    initialGranted: Boolean = true,
) : NotificationPermissionState {

    private companion object {
        // Duplicate the literal — a shared reference would require either a forbidden
        // `:data:vpn` import or a domain constant this plan intentionally rejects (red-team 10h).
        private const val CHANNEL_ID = "vpnis_tunnel"
    }

    /** Current backing value that [refresh] will publish and return. */
    var backing: Boolean = initialGranted

    private val _isGranted = MutableStateFlow(initialGranted)

    override val isGranted: Flow<Boolean> = _isGranted

    override val channelId: String = CHANNEL_ID

    /** Convenience setter — updates [backing] so the next [refresh] call picks it up. */
    fun setGranted(value: Boolean) {
        backing = value
    }

    /**
     * Publishes [backing] into [isGranted] and returns the same value.
     *
     * This mirrors the real pull-semantics contract: the caller must use the return value
     * for an atomic read rather than separately reading [isGranted] after the call.
     */
    override suspend fun refresh(): Boolean {
        _isGranted.value = backing
        return backing
    }
}
