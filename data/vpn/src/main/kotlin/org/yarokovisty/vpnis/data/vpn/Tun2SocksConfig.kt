package org.yarokovisty.vpnis.data.vpn

/**
 * Configuration for a single hev-socks5-tunnel session.
 *
 * An instance of this class encodes every tuneable option that VPNis supplies to the
 * native tunnel. Fields not relevant to the TUN-fd mode (e.g. tunnel interface name,
 * IPv4/IPv6 addresses, `post-up-script`) are intentionally omitted — hev ignores missing
 * optional sections, and the Android `VpnService` manages the TUN interface directly.
 *
 * Produce the YAML string expected by [Tun2SocksBridge.nativeStart] by calling [toYaml].
 *
 * @param socksAddress IPv4 or IPv6 address of the upstream SOCKS5 proxy (the Xray SOCKS
 *   inbound, typically `127.0.0.1` on the local device).
 * @param socksPort Port of the upstream SOCKS5 proxy. Must be in the range `1..65535`.
 * @param mtu Maximum transmission unit for the TUN interface in bytes. Must be positive.
 *   Default `8500` matches hev's own sample config and is appropriate for most links.
 * @param udpMode hev's SOCKS5 UDP relay mode. Accepted values are `"udp"` (UDP-native
 *   relay, default) and `"tcp"` (UDP-over-TCP relay for firewalled environments).
 * @param taskStackSize Size in bytes of the stack allocated per hev task. Must be
 *   positive. Default `20480` (20 KiB) is intentionally smaller than hev's own default
 *   (`86016`) to reduce per-connection memory on constrained Android devices; raise it if
 *   deep call stacks are observed in native crash reports.
 * @param logLevel hev log verbosity. One of `"debug"`, `"info"`, `"warn"` (default), or
 *   `"error"`. Prefer `"warn"` in production; `"debug"` is useful for capturing native
 *   tunnel traces during development.
 */
internal data class Tun2SocksConfig(
    val socksAddress: String = "127.0.0.1",
    val socksPort: Int,
    val mtu: Int = 8500,
    val udpMode: String = "udp",
    val taskStackSize: Int = 20480,
    val logLevel: String = "warn",
) {
    init {
        require(socksPort in 1..MAX_PORT) {
            "socksPort must be in 1..65535, got $socksPort"
        }
        require(mtu > 0) {
            "mtu must be positive, got $mtu"
        }
        require(taskStackSize > 0) {
            "taskStackSize must be positive, got $taskStackSize"
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
 * uses 2-space indentation, single-quotes the `udp` value as hev requires, and ends with
 * a trailing newline. This makes the string safe for byte-level equality assertions in
 * tests.
 *
 * Example output for `Tun2SocksConfig(socksPort = 10808)`:
 * ```
 * tunnel:
 *   mtu: 8500
 * socks5:
 *   address: 127.0.0.1
 *   port: 10808
 *   udp: 'udp'
 * misc:
 *   task-stack-size: 20480
 *   log-level: warn
 * ```
 */
internal fun Tun2SocksConfig.toYaml(): String = buildString {
    appendLine("tunnel:")
    appendLine("  mtu: $mtu")
    appendLine("socks5:")
    appendLine("  address: $socksAddress")
    appendLine("  port: $socksPort")
    appendLine("  udp: '$udpMode'")
    appendLine("misc:")
    appendLine("  task-stack-size: $taskStackSize")
    append("  log-level: $logLevel")
    appendLine()
}
