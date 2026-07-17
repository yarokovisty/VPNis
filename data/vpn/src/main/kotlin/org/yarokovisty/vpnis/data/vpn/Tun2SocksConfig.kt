package org.yarokovisty.vpnis.data.vpn

/**
 * Configuration for a single hev-socks5-tunnel session.
 *
 * An instance of this class encodes every tuneable option that VPNis supplies to the
 * native tunnel. The field set and YAML shape mirror v2rayNG's `TProxyService` config
 * (issue #111) so our tunnel behaves like the reference client on the same networks.
 *
 * Produce the YAML string expected by [Tun2SocksBridge.nativeStart] by calling [toYaml].
 *
 * @param socksAddress IPv4 or IPv6 address of the upstream SOCKS5 proxy (the Xray SOCKS
 *   inbound, typically `127.0.0.1` on the local device).
 * @param socksPort Port of the upstream SOCKS5 proxy. Must be in the range `1..65535`.
 * @param ipv4Address IPv4 address of the TUN interface, emitted as hev's `tunnel.ipv4`.
 *   v2rayNG sets this so hev's internal lwip stack agrees with the address the Android
 *   `VpnService.Builder` assigned; kept in sync with [TunConfig.clientAddress].
 * @param ipv6Address IPv6 address of the TUN interface, emitted as hev's `tunnel.ipv6` when
 *   non-null. Kept in sync with [TunConfig.ipv6ClientAddress].
 * @param mtu Maximum transmission unit for the TUN interface in bytes. Must be positive.
 *   Default `1500` (standard Ethernet MTU), matching v2rayNG's default VPN MTU. Kept in sync
 *   with [TunConfig.mtu] via [TunConfig.toTun2SocksConfig].
 * @param udpMode hev's SOCKS5 UDP relay mode. Accepted values are `"udp"` (UDP-native
 *   relay, default) and `"tcp"` (UDP-over-TCP relay for firewalled environments).
 * @param tcpTimeoutMs hev `tcp-read-write-timeout` in milliseconds. Must be positive.
 *   Default `300000` (300s) matches v2rayNG.
 * @param udpTimeoutMs hev `udp-read-write-timeout` in milliseconds. Must be positive.
 *   Default `60000` (60s) matches v2rayNG.
 * @param logLevel hev log verbosity. One of `"debug"`, `"info"`, `"warn"` (default), or
 *   `"error"`. Prefer `"warn"` in production; `"debug"` is useful for capturing native
 *   tunnel traces during development.
 *
 * The per-task stack size is intentionally NOT emitted so hev uses its own default
 * (`86016`); the earlier `20480` override deviated from both hev and v2rayNG (issue #111).
 */
internal data class Tun2SocksConfig(
    val socksAddress: String = "127.0.0.1",
    val socksPort: Int,
    val ipv4Address: String = "10.0.0.2",
    val ipv6Address: String? = null,
    val mtu: Int = 1500,
    val udpMode: String = "udp",
    val tcpTimeoutMs: Int = 300_000,
    val udpTimeoutMs: Int = 60_000,
    val logLevel: String = "warn",
) {
    init {
        require(socksPort in 1..MAX_PORT) {
            "socksPort must be in 1..65535, got $socksPort"
        }
        require(mtu > 0) {
            "mtu must be positive, got $mtu"
        }
        require(ipv4Address.isNotBlank()) {
            "ipv4Address must not be blank"
        }
        require(tcpTimeoutMs > 0) {
            "tcpTimeoutMs must be positive, got $tcpTimeoutMs"
        }
        require(udpTimeoutMs > 0) {
            "udpTimeoutMs must be positive, got $udpTimeoutMs"
        }
    }

    internal companion object {
        /** Maximum valid TCP/UDP port number. */
        const val MAX_PORT = 65_535
    }
}

/**
 * Serialises this [Tun2SocksConfig] to the YAML format expected by hev-socks5-tunnel's
 * `hev_socks5_tunnel_main_from_str` entry point.
 *
 * The output is deterministic: fields are emitted in a fixed order (no map iteration),
 * uses 2-space indentation, single-quotes the `udp`/`ipv6` values as hev requires, and ends
 * with a trailing newline. This makes the string safe for byte-level equality assertions in
 * tests. `tunnel.ipv6` is emitted only when [Tun2SocksConfig.ipv6Address] is non-null.
 *
 * Example output for `Tun2SocksConfig(socksPort = 10808)`:
 * ```
 * tunnel:
 *   mtu: 1500
 *   ipv4: 10.0.0.2
 * socks5:
 *   address: 127.0.0.1
 *   port: 10808
 *   udp: 'udp'
 * misc:
 *   tcp-read-write-timeout: 300000
 *   udp-read-write-timeout: 60000
 *   log-level: warn
 * ```
 */
internal fun Tun2SocksConfig.toYaml(): String = buildString {
    appendLine("tunnel:")
    appendLine("  mtu: $mtu")
    appendLine("  ipv4: $ipv4Address")
    if (ipv6Address != null) {
        appendLine("  ipv6: '$ipv6Address'")
    }
    appendLine("socks5:")
    appendLine("  address: $socksAddress")
    appendLine("  port: $socksPort")
    appendLine("  udp: '$udpMode'")
    appendLine("misc:")
    appendLine("  tcp-read-write-timeout: $tcpTimeoutMs")
    appendLine("  udp-read-write-timeout: $udpTimeoutMs")
    append("  log-level: $logLevel")
    appendLine()
}
