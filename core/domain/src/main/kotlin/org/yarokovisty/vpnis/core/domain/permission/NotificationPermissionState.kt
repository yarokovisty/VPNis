package org.yarokovisty.vpnis.core.domain.permission

import kotlinx.coroutines.flow.Flow

/**
 * Read-only gate that surfaces whether the OS will display the app's status notifications.
 *
 * ## Pull semantics
 *
 * The OS does not push permission changes to the app. Implementations back [isGranted] with a
 * [kotlinx.coroutines.flow.StateFlow] that is updated **only** when [refresh] is called — a
 * deliberate PULL, not a push. Callers (e.g. the route layer on `ON_RESUME`) are responsible for
 * calling [refresh] at the right lifecycle moment to surface a change the user made in system
 * settings.
 *
 * ## Contract invariants
 *
 * - [isGranted] always has a seed value (set at construction); collectors never block waiting for
 *   the first emission.
 * - [refresh] is idempotent and cheap — the cost is a couple of system-service reads; callers need
 *   not debounce it.
 * - This interface is **channel-agnostic**: it reports whether the OS will display the app's status
 *   notifications as a whole. The two-part OS check is an implementation detail of the Android
 *   layer, not part of this contract.
 *
 * @see kotlinx.coroutines.flow.StateFlow
 */
public interface NotificationPermissionState {

    /**
     * Emits `true` when the OS will display the app's status notifications, `false` otherwise.
     *
     * Backed by a [kotlinx.coroutines.flow.StateFlow] in all implementations; the current value is
     * always available via [kotlinx.coroutines.flow.StateFlow.value] on the concrete type, and
     * [kotlinx.coroutines.flow.Flow.collect] never suspends waiting for the first item.
     *
     * The emitted value reflects the state **at the last [refresh] call**, not necessarily the
     * current OS state. Collect this flow and call [refresh] on lifecycle events (e.g. `ON_RESUME`)
     * to keep it in sync.
     */
    public val isGranted: Flow<Boolean>

    /**
     * Re-reads the OS notification-permission state and updates [isGranted] if the value changed.
     *
     * This is a PULL operation — it queries the OS synchronously (on the calling coroutine's
     * dispatcher) and pushes the result into the backing [kotlinx.coroutines.flow.StateFlow].
     * Implementations may hop to a background dispatcher internally for defensive threading.
     *
     * Safe to call on any dispatcher; idempotent; lightweight.
     */
    public suspend fun refresh()
}
