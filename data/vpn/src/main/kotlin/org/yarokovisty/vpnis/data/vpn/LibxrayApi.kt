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
 * - [queryStats] HTTP-GETs an Xray metrics endpoint and returns the same base64 `CallResponse`
 *   envelope, whose `data` field carries the raw expvar body.
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
     * Queries an Xray metrics endpoint over HTTP and returns the raw response (issues #69/#130).
     *
     * @param server The full metrics URL to GET, e.g. `http://127.0.0.1:10809/debug/vars`.
     * @return Base64-encoded `CallResponse` JSON: `{ "success": Boolean, "data": String, "error": String }`
     *   where `data` is the expvar `/debug/vars` body. Decoded and parsed by [LibXrayCoreImpl.queryStats];
     *   the raw call throws nothing across this seam (a transport error surfaces as `success=false`).
     */
    fun queryStats(server: String): String

    /**
     * Stops the running Xray proxy, releasing the local SOCKS inbound port.
     */
    fun stop()
}
