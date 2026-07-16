package org.yarokovisty.vpnis.data.vpn

/**
 * Seam interface for checking whether the OS VPN consent dialog must be shown before
 * a tunnel can be established.
 *
 * Abstracting this behind an interface keeps [ConnectionControllerImpl] free of any
 * Android framework imports, so the controller can be unit-tested without a real
 * [android.content.Context] or a live [android.net.VpnService.prepare] call.
 *
 * The Android implementation ([AndroidVpnConsentChecker]) delegates to
 * [android.net.VpnService.prepare]: a non-null return value means the system requires
 * user consent before the VPN tunnel can be built. The returned [android.content.Intent]
 * is intentionally discarded — only the boolean is surfaced here — so that the I1
 * invariant is preserved: the Intent never leaves `feature/home`, which obtains its own
 * Intent directly from `VpnService.prepare()` when it needs to launch the consent dialog.
 *
 * ## Idempotency
 *
 * [android.net.VpnService.prepare] is idempotent: calling it twice (once here for the
 * boolean, once in `feature/home` for the Intent) produces consistent results and does
 * not consume the permission — it is safe to call before and after the dialog.
 */
internal interface VpnConsentChecker {

    /**
     * Returns `true` if the OS VPN consent dialog must be shown before a tunnel can be
     * established (i.e. [android.net.VpnService.prepare] returns a non-null Intent),
     * `false` if consent has already been granted.
     */
    fun isConsentRequired(): Boolean
}
