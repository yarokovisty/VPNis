package org.yarokovisty.vpnis.data.vpn

/**
 * Abstraction over the gomobile-generated `libXray.Libxray` class.
 *
 * This interface lives in `main` so that [LibXrayCoreImpl] — and its security-critical
 * `registerDialerController`-before-`runFromJson` ordering — is JVM-unit-testable without
 * requiring the native AAR. The only implementation that touches the AAR is [RealLibxrayApi]
 * in the `buildNative` source set.
 *
 * ## API semantics
 *
 * - [registerDialerController] registers the socket-protection callback globally in libXray.
 *   It MUST be called **before** [runFromJson]; otherwise Xray's outbound sockets are routed
 *   back through the TUN, causing an infinite traffic loop.
 * - [runFromJson] starts the Xray proxy and returns a **base64-encoded** JSON string of the
 *   form `{ "success": <Boolean>, "data": <String?>, "error": <String?> }`.
 *   The caller ([LibXrayCoreImpl]) decodes and parses this `CallResponse` envelope.
 * - [stop] stops the running Xray proxy.
 */
internal interface LibxrayApi {

    /**
     * Registers [onProtect] as the dialer protection callback.
     *
     * libXray invokes [onProtect] with the raw socket file descriptor of each outbound
     * connection it creates. The callback must call `VpnService.protect(fd)` (via
     * [VpnSocketProtector]) and return its result so the socket bypasses the TUN.
     *
     * Must be called strictly before [runFromJson].
     *
     * @param onProtect Lambda receiving an fd; returns `true` if protection succeeded.
     */
    fun registerDialerController(onProtect: (fd: Int) -> Boolean)

    /**
     * Starts the Xray proxy from the given JSON configuration.
     *
     * @param datDir Path to the directory containing Xray `geoip.dat` and `geosite.dat` asset
     *   files. Typically `Context.filesDir.path`.
     * @param configJson Xray-core JSON config string produced by [XrayConfigBuilder].
     * @return Base64-encoded `CallResponse` JSON: `{ "success": Boolean, "error": String? }`.
     *   Decode with `java.util.Base64` and parse with the kotlinx.serialization element API.
     */
    fun runFromJson(datDir: String, configJson: String): String

    /**
     * Stops the running Xray proxy, releasing the local SOCKS inbound port.
     */
    fun stop()
}
