package org.yarokovisty.vpnis.data.vpn

// libXray is the gomobile-generated package from XrayCore.aar (ADR 0001, pinned SHA f6ce612).
// This import only resolves when -Pvpnis.buildNative=true supplies the AAR via the flatDir
// repository declared in settings.gradle.kts. On default builds this file is not compiled
// (the `buildNative` source set is not added by the onVariants hook), so the missing class
// is never a problem for ./gradlew assembleDebug without the flag.
//
// NOTE: The exact generated Java class names (package, class, method casing) must be confirmed
// at the first CI bind against the real AAR. gomobile generates `libXray.Libxray` and the
// method names below mirror the Go function names — adjust if the CI bind reveals differences.
import libXray.DialerController
import libXray.Libxray

/**
 * Production [LibxrayApi] implementation that wraps the gomobile-generated [Libxray] class.
 *
 * This is the **only** class in `:data:vpn` that references `XrayCore.aar`. It lives in
 * the `buildNative` source set and is never compiled in default builds. All other
 * security-critical logic (register-before-start ordering, CallResponse decoding) lives
 * in [LibXrayCoreImpl] inside `main`, where it is JVM-unit-testable.
 *
 * ## gomobile name mapping
 *
 * | Kotlin call | Generated Java | Go function |
 * |---|---|---|
 * | `Libxray.registerDialerController(controller)` | `Libxray.registerDialerController` | `RegisterDialerController` |
 * | `Libxray.runXrayFromJSON(req)` | `Libxray.runXrayFromJSON` | `RunXrayFromJSON` |
 * | `Libxray.newXrayRunFromJSONRequest(datDir, configJson)` | `Libxray.newXrayRunFromJSONRequest` | `NewXrayRunFromJSONRequest` |
 * | `Libxray.stopXray()` | `Libxray.stopXray` | `StopXray` |
 *
 * **Confirm these names at the first CI bind** — gomobile lowercases the first letter of
 * exported Go identifiers when producing the JVM binding; the exact casing may differ from
 * the Go source.
 */
internal class RealLibxrayApi : LibxrayApi {

    /**
     * Registers [onProtect] as the socket-protection callback by building a
     * [DialerController] adapter and passing it to [Libxray.registerDialerController].
     *
     * [onProtect] is called by libXray for every outbound socket file descriptor it creates.
     * Returning `true` protects the socket from the TUN (it communicates on the physical
     * network directly); `false` leaves the socket unprotected, which causes a routing loop.
     */
    override fun registerDialerController(onProtect: (fd: Int) -> Boolean) {
        val controller = object : DialerController {
            override fun protectFd(fd: Int): Boolean = onProtect(fd)
        }
        Libxray.registerDialerController(controller)
    }

    /**
     * Starts the Xray proxy from [configJson] using [datDir] for asset files.
     *
     * Builds the request via [Libxray.newXrayRunFromJSONRequest] and passes it to
     * [Libxray.runXrayFromJSON], which returns a base64-encoded `CallResponse` JSON.
     *
     * [configJson] is NOT logged — it contains server credentials.
     */
    override fun runFromJson(datDir: String, configJson: String): String {
        val request = Libxray.newXrayRunFromJSONRequest(datDir, configJson)
        return Libxray.runXrayFromJSON(request)
    }

    /**
     * Stops the running Xray proxy by calling [Libxray.stopXray].
     */
    override fun stop() {
        Libxray.stopXray()
    }
}
