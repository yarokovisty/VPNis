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
 * - The **permission-evaluation logic** ([isGranted]) is channel-agnostic: the two-part OS check
 *   (app-level grant AND channel importance) is an implementation detail of the Android layer, not
 *   part of this contract. [channelId], by contrast, is a deep-link navigation target — it is
 *   orthogonal to the permission evaluation and intentionally kept separate from [isGranted].
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
     * Opaque identifier for the notification channel associated with this permission gate.
     *
     * Intended **only** as a deep-link navigation target — for example, building an
     * `ACTION_CHANNEL_NOTIFICATION_SETTINGS` Intent that lands directly on the tunnel channel in
     * system settings. It is NOT a permission-logic signal and MUST NOT be used to infer anything
     * about channel state (importance, whether the channel is silenced, etc.). Do not add derived
     * properties such as `importance` or `isSilenced` to this contract — those concerns belong
     * to [isGranted]'s two-part evaluation, which is an implementation detail of the Android layer.
     */
    public val channelId: String

    /**
     * Re-reads the OS notification-permission state, updates [isGranted], and returns the freshly
     * computed gate value.
     *
     * This is a PULL operation — it queries the OS (on the calling coroutine's dispatcher, or an
     * internal background dispatcher), stores the result into the backing
     * [kotlinx.coroutines.flow.StateFlow], and returns that **same computed value** directly.
     * Callers that need the current grant status after a refresh MUST use the return value rather
     * than reading [isGranted] separately, to avoid a stale-read race under interleaved refreshes.
     *
     * Safe to call on any dispatcher; idempotent; lightweight.
     *
     * @return `true` if the OS will display the app's status notifications after this refresh,
     *   `false` otherwise — identical to the value just pushed into [isGranted].
     */
    public suspend fun refresh(): Boolean
}
