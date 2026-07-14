package org.yarokovisty.vpnis.data.vpn

/**
 * Seam that allows issue #63's libXray / `DialerController` to protect raw sockets from
 * being routed back through the VPN tunnel (which would cause an infinite loop).
 *
 * The Android platform provides this functionality via [android.net.VpnService.protect],
 * but libXray must not depend on the Android `Service` type directly. This interface
 * decouples the native dialer from the service lifecycle:
 *
 * - [VpnTunnelService] implements this interface by delegating to the inherited
 *   [android.net.VpnService.protect] method.
 * - Issue #63 wires the running instance into the libXray `ProtectFd` callback, typically
 *   by passing the [VpnSocketProtector] reference through a shared object or an injected
 *   callback registered at connect-time.
 * - A test fake can implement this interface without any Android framework dependency,
 *   enabling unit tests for the dialer in isolation.
 *
 * The interface is `internal` — it is a contract within `:data:vpn` and must not be
 * exported to other modules.
 */
internal interface VpnSocketProtector {

    /**
     * Protects [socket] from being routed through the VPN tunnel.
     *
     * After this call the socket communicates directly with the underlying network,
     * bypassing the TUN interface. This is required for any socket that the tunnel
     * infrastructure itself creates (e.g. the SOCKS5 connection to the proxy server)
     * so that traffic is not re-tunnelled in a loop.
     *
     * @param socket File descriptor of the socket to protect.
     * @return `true` if the socket was successfully protected; `false` if the VPN
     *   connection is not active or the system call failed.
     */
    fun protect(socket: Int): Boolean
}
