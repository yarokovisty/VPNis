package org.yarokovisty.vpnis.data.vpn

import android.content.Context
import android.net.VpnService

/**
 * Production implementation of [VpnConsentChecker] backed by [VpnService.prepare].
 *
 * [VpnService.prepare] returns a non-null [android.content.Intent] when the OS requires
 * the user to approve VPN usage. This class discards that Intent — only the boolean is
 * needed here. The presentation layer (`feature/home`) obtains its own Intent directly
 * from `VpnService.prepare()` when it needs to launch the system consent dialog,
 * preserving the I1 invariant (Intent does not cross module boundaries).
 *
 * @param context Application context used to invoke [VpnService.prepare]. Must be the
 *   application context (not an Activity context) to avoid leaking an Activity reference —
 *   injected via Koin's `androidContext()`.
 */
internal class AndroidVpnConsentChecker(private val context: Context) : VpnConsentChecker {

    /**
     * Returns `true` if [VpnService.prepare] returns a non-null Intent, meaning the OS
     * VPN consent has not yet been granted.
     */
    override fun isConsentRequired(): Boolean = VpnService.prepare(context) != null
}
