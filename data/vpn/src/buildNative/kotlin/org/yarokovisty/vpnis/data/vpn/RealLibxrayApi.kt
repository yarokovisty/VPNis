package org.yarokovisty.vpnis.data.vpn

// libXray is the gomobile-generated package from XrayCore.aar (ADR 0001, pinned SHA f6ce612).
// This import only resolves when -Pvpnis.buildNative=true supplies the AAR via the flatDir
// repository declared in settings.gradle.kts. On default builds this file is not compiled
// (the `buildNative` source set is not added by the onVariants hook), so the missing class
// is never a problem for ./gradlew assembleDebug without the flag.
//
// Names below are CONFIRMED against the first CI bind (PR #97) by inspecting the built AAR:
// gomobile capitalises the package name `libXray` → Java class `LibXray` (NOT `Libxray`),
// and Go `int` maps to Java `long`, so DialerController.protectFd takes a `long`.
import libXray.DialerController
import libXray.LibXray

/**
 * Production [LibxrayApi] implementation that wraps the gomobile-generated [LibXray] class.
 *
 * This is the **only** class in `:data:vpn` that references `XrayCore.aar`. It lives in
 * the `buildNative` source set and is never compiled in default builds. All other
 * security-critical logic (register-before-start ordering, CallResponse decoding) lives
 * in [LibXrayCoreImpl] inside `main`, where it is JVM-unit-testable.
 *
 * ## gomobile name mapping (confirmed against the built AAR)
 *
 * | Kotlin call | Generated Java |
 * |---|---|
 * | `LibXray.registerDialerController(controller)` | `LibXray.registerDialerController(DialerController)` |
 * | `LibXray.newXrayRunFromJSONRequest(datDir, configJson)` | `LibXray.newXrayRunFromJSONRequest(String, String)` |
 * | `LibXray.runXrayFromJSON(req)` | `LibXray.runXrayFromJSON(String)` |
 * | `LibXray.queryStats(base64Url)` | `LibXray.queryStats(String)` (Go `QueryStats`) |
 * | `LibXray.stopXray()` | `LibXray.stopXray()` |
 * | `DialerController.protectFd(fd)` | `protectFd(long): boolean` (Go `int` → Java `long`) |
 */
internal class RealLibxrayApi : LibxrayApi {

    /**
     * Registers [onProtect] as the socket-protection callback by building a
     * [DialerController] adapter and passing it to [LibXray.registerDialerController].
     *
     * [onProtect] is called by libXray for every outbound socket file descriptor it creates.
     * Returning `true` protects the socket from the TUN (it communicates on the physical
     * network directly); `false` leaves the socket unprotected, which causes a routing loop.
     *
     * The generated [DialerController.protectFd] takes a `long` (gomobile maps Go `int` →
     * Java `long`); a file descriptor always fits in an `Int`, so the narrowing is safe.
     */
    override fun registerDialerController(onProtect: (fd: Int) -> Boolean) {
        val controller = object : DialerController {
            override fun protectFd(fd: Long): Boolean = onProtect(fd.toInt())
        }
        LibXray.registerDialerController(controller)
    }

    /**
     * Starts the Xray proxy from [configJson] using [datDir] for asset files.
     *
     * Builds the request via [LibXray.newXrayRunFromJSONRequest] and passes it to
     * [LibXray.runXrayFromJSON], which returns a base64-encoded `CallResponse` JSON.
     *
     * [configJson] is NOT logged — it contains server credentials.
     */
    override fun runFromJson(datDir: String, configJson: String): String {
        val request = LibXray.newXrayRunFromJSONRequest(datDir, configJson)
        return LibXray.runXrayFromJSON(request)
    }

    /**
     * Queries the Xray metrics endpoint at [server] (a full `http://…/debug/vars` URL).
     *
     * The gomobile `QueryStats` entrypoint base64-**decodes** its argument to recover the URL, so
     * the URL is base64-**encoded** here first (standard encoding, matching Go's
     * `base64.StdEncoding`). The return value is the base64 `CallResponse` envelope, decoded and
     * parsed in [LibXrayCoreImpl.queryStats] — no parsing happens behind the AAR seam.
     */
    override fun queryStats(server: String): String {
        val base64Url = java.util.Base64.getEncoder().encodeToString(server.toByteArray(Charsets.UTF_8))
        return LibXray.queryStats(base64Url)
    }

    /**
     * Stops the running Xray proxy by calling [LibXray.stopXray].
     */
    override fun stop() {
        LibXray.stopXray()
    }
}
