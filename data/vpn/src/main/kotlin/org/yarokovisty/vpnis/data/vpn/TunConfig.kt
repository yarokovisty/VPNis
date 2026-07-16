package org.yarokovisty.vpnis.data.vpn

/**
 * Pure, dependency-free configuration holder for a VPN TUN interface.
 *
 * All parameters are validated in [init] so that an invalid [TunConfig] can never be
 * constructed — invalid state is impossible. The class has no Android dependencies, making
 * it fully unit-testable off-device.
 *
 * ## IPv6 fail-closed (issue #106)
 *
 * The TUN interface is configured with both an IPv4 and an IPv6 client address, and the
 * default route list includes `::/0` alongside `0.0.0.0/0`. This ensures all IPv6 traffic
 * is captured by the TUN — if the upstream proxy does not support IPv6, connections time
 * out or are refused inside the tunnel rather than leaking with the device's real address.
 * Full IPv6 transit is a follow-up; the goal here is **fail-closed** (anti-leak).
 *
 * IPv4 and IPv6 prefix lengths are validated against their respective maximums (32 and 128)
 * independently so that neither loosens the other's constraint.
 *
 * @param clientAddress IPv4 address assigned to the TUN interface on the device side.
 *   Must be non-blank and a valid dotted-quad (format is not deep-validated here; the OS
 *   rejects ill-formed values at [android.net.VpnService.Builder.addAddress] time).
 * @param prefixLength Network prefix length for [clientAddress]. Must be in `0..32`.
 *   Default `/30` (four addresses) is sufficient for a point-to-point link.
 * @param ipv6ClientAddress IPv6 address assigned to the TUN interface. Must be non-blank.
 *   Default `fd00::1` is a ULA address — it is routable inside the tunnel but not on the
 *   public Internet, which is appropriate for a point-to-point VPN link.
 * @param ipv6PrefixLength Network prefix length for [ipv6ClientAddress]. Must be in
 *   `0..128`. Default `/128` is a host route (single address), matching the point-to-point
 *   nature of the link.
 * @param mtu Maximum transmission unit for the TUN interface in bytes. Must be positive.
 *   Default `8500` matches hev-socks5-tunnel's sample configuration and avoids
 *   fragmentation on most links.
 * @param dnsServers List of DNS server addresses to push through the tunnel. Each entry
 *   must be non-blank. Default is Cloudflare DNS (`1.1.1.1`, `1.0.0.1`).
 * @param routes List of CIDR routes to capture through the TUN interface, expressed as
 *   `"address/prefixLength"` strings (e.g. `"0.0.0.0/0"`, `"::/0"`). Each entry must be
 *   non-blank. Default captures all IPv4 and IPv6 traffic (fail-closed for IPv6 leak
 *   prevention — see class KDoc).
 * @param session Human-readable session name shown in the Android VPN notification.
 *   Must be non-blank.
 * @param localSocksPort Port on which the local SOCKS5 proxy is listening (the Xray
 *   inbound, wired in issue #63). hev-socks5-tunnel forwards all TUN traffic to this
 *   port. Must be in `1..65535`. Both sides (hev config and Xray inbound) must agree on
 *   this value — expose it here so neither hardcodes it independently.
 */
internal data class TunConfig(
    val clientAddress: String = "10.0.0.2",
    val prefixLength: Int = 30,
    val ipv6ClientAddress: String = "fd00::1",
    val ipv6PrefixLength: Int = 128,
    val mtu: Int = 8500,
    val dnsServers: List<String> = listOf("1.1.1.1", "1.0.0.1"),
    val routes: List<String> = listOf("0.0.0.0/0", "::/0"),
    val session: String = "VPNis",
    val localSocksPort: Int = 10808,
) {
    init {
        require(prefixLength in 0..MAX_PREFIX_LENGTH) {
            "prefixLength must be in 0..32, got $prefixLength"
        }
        require(ipv6PrefixLength in 0..MAX_IPV6_PREFIX_LENGTH) {
            "ipv6PrefixLength must be in 0..128, got $ipv6PrefixLength"
        }
        require(mtu > 0) {
            "mtu must be positive, got $mtu"
        }
        require(localSocksPort in 1..MAX_PORT) {
            "localSocksPort must be in 1..65535, got $localSocksPort"
        }
        require(clientAddress.isNotBlank()) {
            "clientAddress must not be blank"
        }
        require(ipv6ClientAddress.isNotBlank()) {
            "ipv6ClientAddress must not be blank"
        }
        require(dnsServers.all { it.isNotBlank() }) {
            "dnsServers must not contain blank entries"
        }
        require(routes.all { it.isNotBlank() }) {
            "routes must not contain blank entries"
        }
        require(session.isNotBlank()) {
            "session must not be blank"
        }
    }

    internal companion object {
        /** Maximum IPv4 network prefix length (CIDR /32). */
        const val MAX_PREFIX_LENGTH = 32

        /** Maximum IPv6 network prefix length (CIDR /128). */
        const val MAX_IPV6_PREFIX_LENGTH = 128

        /** Maximum valid TCP/UDP port number. */
        const val MAX_PORT = 65_535
    }
}

/**
 * Maps this [TunConfig] to the [Tun2SocksConfig] that hev-socks5-tunnel expects.
 *
 * The SOCKS5 proxy is always on the loopback address (`127.0.0.1`) — Xray binds there
 * (issue #63). [TunConfig.localSocksPort] carries the agreed port so both sides read
 * from the same source.
 */
internal fun TunConfig.toTun2SocksConfig(): Tun2SocksConfig = Tun2SocksConfig(
    socksPort = localSocksPort,
    mtu = mtu,
)
