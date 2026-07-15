package org.yarokovisty.vpnis.data.vpn

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Real [XrayCore] implementation backed by the libXray gomobile binding.
 *
 * This class lives in `main` and depends only on [LibxrayApi] — the thin interface that
 * abstracts the AAR. The only code that references the AAR directly is [RealLibxrayApi]
 * in the `buildNative` source set, making this class JVM-unit-testable without an AAR.
 *
 * ## start() ordering contract
 *
 * [start] calls [LibxrayApi.registerDialerController] **strictly before**
 * [LibxrayApi.runFromJson]. This ordering is mandatory: without prior registration,
 * every outbound socket libXray creates is routed back through the TUN, causing an
 * infinite traffic loop. The ordering is asserted by `LibXrayCoreImplTest`.
 *
 * ## CallResponse decoding
 *
 * [LibxrayApi.runFromJson] returns a base64-encoded JSON string of the form:
 * ```json
 * { "success": true, "data": "...", "error": "" }
 * ```
 * [start] decodes it via `java.util.Base64` (not `android.util.Base64` — the android
 * class is stubbed under `isReturnDefaultValues = true` and returns null/empty in unit
 * tests; `java.util.Base64` is on the JVM classpath for both unit tests and Android)
 * and parses with the kotlinx.serialization element API (`parseToJsonElement` /
 * `jsonObject` / `jsonPrimitive`). No `@Serializable` classes are used — the serialization
 * compiler plugin is not applied to `:data:vpn`.
 *
 * @param api Abstraction over the gomobile libXray calls. Injected for testability.
 * @param datDir Path to the directory containing Xray `geoip.dat` / `geosite.dat` files.
 *   Typically `Context.filesDir.path`, supplied by [XrayCoreProvider].
 */
internal class LibXrayCoreImpl(private val api: LibxrayApi, private val datDir: String) : XrayCore {

    /**
     * Starts the Xray proxy.
     *
     * Registers the socket-protection callback from [protector] via
     * [LibxrayApi.registerDialerController] **before** calling [LibxrayApi.runFromJson].
     * Decodes and parses the `CallResponse` envelope to determine success.
     *
     * @return `true` if libXray reported `success = true`; `false` otherwise.
     *   The caller ([VpnTunnelService]) treats `false` as
     *   [org.yarokovisty.vpnis.core.domain.model.ConnectionError.TunnelSetupFailed].
     */
    override fun start(configJson: String, protector: VpnSocketProtector): Boolean {
        Log.d(TAG, "start: registering dialer controller")
        // Register protection BEFORE runFromJson — this is the ordering invariant.
        api.registerDialerController { fd -> protector.protect(fd) }

        Log.d(TAG, "start: calling runFromJson (configJson length=${configJson.length})")
        // configJson value is NOT logged — it contains server credentials.
        val responseBase64 = api.runFromJson(datDir = datDir, configJson = configJson)

        return parseCallResponse(responseBase64)
    }

    override fun stop() {
        Log.d(TAG, "stop: calling api.stop()")
        api.stop()
    }

    /**
     * Decodes the base64 [CallResponse] envelope returned by [LibxrayApi.runFromJson] and
     * extracts the `success` boolean.
     *
     * Uses `java.util.Base64` (not `android.util.Base64`) so the decoding works in both
     * unit tests (JVM classpath, no Android stubs) and on-device (Android includes the JDK
     * Base64 class since API 26, our minSdk).
     *
     * If decoding or parsing fails, logs the error and returns `false` so the service can
     * treat it as [org.yarokovisty.vpnis.core.domain.model.ConnectionError.TunnelSetupFailed]
     * rather than crashing.
     */
    // TooGenericExceptionCaught: decode + parse throw heterogeneous, unrelated exceptions
    //   (Base64 IllegalArgumentException, kotlinx SerializationException, cast failures) that
    //   are all handled identically — treat any failure as a false CallResponse.
    @Suppress("TooGenericExceptionCaught")
    private fun parseCallResponse(responseBase64: String): Boolean = try {
        val decoded = java.util.Base64.getDecoder().decode(responseBase64)
        val json = String(decoded, Charsets.UTF_8)
        val jsonObject = Json.parseToJsonElement(json).jsonObject
        val success = jsonObject["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
        if (!success) {
            // Log the error field if present, but not the full response (may contain config).
            val error = jsonObject["error"]?.jsonPrimitive?.content
            Log.e(TAG, "start: CallResponse success=false, error=$error")
        }
        success
    } catch (e: Exception) {
        Log.e(TAG, "start: failed to decode/parse CallResponse — treating as failure", e)
        false
    }

    private companion object {
        const val TAG = "LibXrayCoreImpl"
    }
}
