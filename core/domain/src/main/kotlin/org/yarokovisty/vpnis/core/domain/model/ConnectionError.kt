package org.yarokovisty.vpnis.core.domain.model

/**
 * Typed failure reasons for VPN connection operations.
 */
public sealed interface ConnectionError {

    /** The user declined (or revoked) the OS VPN permission dialog. */
    public data object PermissionDenied : ConnectionError

    /** The selected server could not be reached (DNS failure, TCP timeout, etc.). */
    public data object ServerUnreachable : ConnectionError

    /** The VPN tunnel was configured but the OS rejected it during setup. */
    public data object TunnelSetupFailed : ConnectionError

    /**
     * The OS revoked an already-active VPN tunnel (e.g. another VPN app took over,
     * or the user manually disconnected via the system notification).
     */
    public data object Revoked : ConnectionError

    /** Catch-all for failures that do not map to a known category. */
    public data class Unknown(val message: String?) : ConnectionError
}
