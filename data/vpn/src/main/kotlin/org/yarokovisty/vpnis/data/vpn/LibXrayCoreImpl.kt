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

    /**
     * Polls the Xray expvar `/debug/vars` endpoint and extracts the proxy outbound's cumulative
     * byte counters (issues #69/#130).
     *
     * The URL is built from [TunConfig.metricsPort] — the same value [XrayConfigBuilder] binds the
     * `metrics` dokodemo-door inbound to — so the config and the query never drift. Loopback only.
     * Returns `null` on any failure or when the counters are not yet present (see [parseStats]).
     */
    override fun queryStats(): TrafficCounters? {
        val url = "http://127.0.0.1:${TunConfig().metricsPort}/debug/vars"
        val responseBase64 = api.queryStats(url)
        return parseStats(responseBase64)
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

    /**
     * Decodes the base64 `CallResponse` from [LibxrayApi.queryStats] and extracts the proxy
     * outbound's cumulative `uplink`/`downlink` counters from the nested expvar body.
     *
     * The envelope's `data` field carries the raw `/debug/vars` body as a JSON-encoded string, so
     * this is a two-level parse: decode the envelope, then parse `data` and navigate
     * `stats.outbound.<PROXY_OUTBOUND_TAG>.{uplink,downlink}` (the nested expvar shape confirmed
     * against Xray-core metrics docs — NOT the gRPC `>>>` naming).
     *
     * Returns `null` — a signal the poller skips — when the response is a failure, the body is
     * blank, the counters are absent (no traffic since Xray start), or anything fails to parse.
     */
    // TooGenericExceptionCaught: base64/JSON decode throw heterogeneous exceptions all handled as
    //   "stats unavailable". ReturnCount: linear guard-style extraction reads clearer as early nulls.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun parseStats(responseBase64: String): TrafficCounters? {
        return try {
            val decoded = java.util.Base64.getDecoder().decode(responseBase64)
            val envelope = Json.parseToJsonElement(String(decoded, Charsets.UTF_8)).jsonObject
            val success = envelope["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
            if (!success) {
                Log.d(TAG, "queryStats: CallResponse success=false — stats unavailable")
                return null
            }
            // `data` holds the expvar /debug/vars body as a JSON-encoded string.
            val body = envelope["data"]?.jsonPrimitive?.content
            if (body.isNullOrBlank()) return null

            val outbound = Json.parseToJsonElement(body).jsonObject["stats"]?.jsonObject
                ?.get("outbound")?.jsonObject
                ?.get(XrayConfigBuilder.PROXY_OUTBOUND_TAG)?.jsonObject
            val uplink = outbound?.get("uplink")?.jsonPrimitive?.content?.toLongOrNull()
            val downlink = outbound?.get("downlink")?.jsonPrimitive?.content?.toLongOrNull()
            if (uplink == null || downlink == null) {
                // Counters not present yet (no traffic since Xray start) — skip this tick.
                return null
            }
            TrafficCounters(rxBytes = downlink, txBytes = uplink)
        } catch (e: Exception) {
            Log.d(TAG, "queryStats: failed to decode/parse stats — treating as unavailable", e)
            null
        }
    }

    private companion object {
        const val TAG = "LibXrayCoreImpl"
    }
}
