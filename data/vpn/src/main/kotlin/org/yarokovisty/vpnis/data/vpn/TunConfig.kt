package org.yarokovisty.vpnis.data.vpn

/**
 * Pure, dependency-free configuration holder for a VPN TUN interface.
 *
 * All parameters are validated in [init] so that an invalid [TunConfig] can never be
 * constructed — invalid state is impossible. The class has no Android dependencies, making
 * it fully unit-testable off-device.
 *
 * @param clientAddress IPv4 address assigned to the TUN interface on the device side.
 *   Must be non-blank and a valid dotted-quad (format is not deep-validated here; the OS
 *   rejects ill-formed values at [android.net.VpnService.Builder.addAddress] time).
 * @param prefixLength Network prefix length for [clientAddress]. Must be in `0..32`.
 *   Default `/30` (four addresses) is sufficient for a point-to-point link.
 * @param mtu Maximum transmission unit for the TUN interface in bytes. Must be positive.
 *   Default `8500` matches hev-socks5-tunnel's sample configuration and avoids
 *   fragmentation on most links.
 * @param dnsServers List of DNS server addresses to push through the tunnel. Each entry
 *   must be non-blank. Default is Cloudflare DNS (`1.1.1.1`, `1.0.0.1`).
 * @param routes List of CIDR routes to capture through the TUN interface, expressed as
 *   `"address/prefixLength"` strings (e.g. `"0.0.0.0/0"`). Each entry must be non-blank.
 *   Default is the catch-all route that redirects all IPv4 traffic through the tunnel.
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
    val mtu: Int = 8500,
    val dnsServers: List<String> = listOf("1.1.1.1", "1.0.0.1"),
    val routes: List<String> = listOf("0.0.0.0/0"),
    val session: String = "VPNis",
    val localSocksPort: Int = 10808,
) {
    init {
        require(prefixLength in 0..32) {
            "prefixLength must be in 0..32, got $prefixLength"
        }
        require(mtu > 0) {
            "mtu must be positive, got $mtu"
        }
        require(localSocksPort in 1..65535) {
            "localSocksPort must be in 1..65535, got $localSocksPort"
        }
        require(clientAddress.isNotBlank()) {
            "clientAddress must not be blank"
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
