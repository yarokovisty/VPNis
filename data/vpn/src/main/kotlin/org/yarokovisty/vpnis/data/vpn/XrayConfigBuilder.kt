package org.yarokovisty.vpnis.data.vpn

import android.util.Log
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Translates a VLESS/Reality URI into an Xray-core JSON configuration string.
 *
 * ## Supported URI shape (Milestone-1)
 *
 * ```
 * vless://<uuid>@<host>:<port>?type=tcp&security=reality&pbk=<key>&fp=<fp>&sni=<sni>&sid=<sid>[&flow=<flow>]#<label>
 * ```
 *
 * The URI scheme must be `vless`. Any other scheme, or a URI that cannot be parsed into
 * the expected components, causes [build] to return `null` — no exception is thrown
 * across this seam.
 *
 * ## JSON assembly
 *
 * The output JSON is assembled exclusively with the **kotlinx.serialization element API**
 * (`buildJsonObject`, `putJsonObject`, `putJsonArray`, [JsonPrimitive]). String
 * interpolation and `org.json` are intentionally avoided:
 * - `org.json` is stubbed under `testOptions.unitTests.isReturnDefaultValues = true` and
 *   produces meaningless defaults in unit tests.
 * - String interpolation opens a JSON-injection vector; every URI-derived value is passed
 *   to [JsonPrimitive] so the kotlinx.serialization encoder handles escaping by construction.
 *
 * ## Emitted JSON structure
 *
 * ```json
 * {
 *   "inbounds": [{ "tag":"socks-in", "protocol":"socks", "listen":"127.0.0.1",
 *                  "port":<localSocksPort>,
 *                  "settings":{ "auth":"noauth", "udp":true } }],
 *   "outbounds": [{
 *     "tag":"proxy-out", "protocol":"vless",
 *     "settings":{ "vnext":[{ "address":"<host>", "port":<port>,
 *       "users":[{ "id":"<uuid>", "encryption":"none" [, "flow":"<flow>"] }] }] },
 *     "streamSettings":{
 *       "network":"tcp",
 *       "security":"reality",
 *       "realitySettings":{
 *         "serverName":"<sni>", "fingerprint":"<fp>",
 *         "publicKey":"<pbk>", "shortId":"<sid>"
 *       }
 *     }
 *   }]
 * }
 * ```
 *
 * When `security` is NOT `reality`, [build] returns `null` (broader transport/security
 * coverage is tracked in issue #74).
 *
 * Broader protocol/transport coverage and config validation are tracked in issue #74.
 */
internal object XrayConfigBuilder {

    private const val TAG = "XrayConfigBuilder"

    /**
     * Local SOCKS5 inbound port for the Xray proxy.
     *
     * Sourced from [TunConfig.localSocksPort] (the default parameter value) so that hev
     * and Xray always agree on the port without independent hardcoding. Both sides read
     * from the same authoritative source.
     */
    private val localSocksPort: Int = TunConfig().localSocksPort

    /**
     * Parses [uri] (a VLESS URI string) and returns an Xray-core JSON configuration, or
     * `null` if [uri] is malformed, uses an unsupported scheme, or lacks required parameters.
     *
     * The returned string is always valid JSON when non-null — every URI-derived value is
     * escaped by the serialization layer; no injection is possible.
     *
     * This function is pure (no side effects) and thread-safe.
     */
    fun build(uri: String): String? = try {
        buildInternal(uri)
    } catch (e: IllegalArgumentException) {
        // URLDecoder.decode throws IllegalArgumentException on malformed percent-escapes;
        // any other bad-argument failure likewise means the URI is not a usable config.
        Log.d(TAG, "build: failed to parse URI — returning null (${e.javaClass.simpleName})")
        null
    }

    // ReturnCount: a linear guard-clause parser — each missing/unsupported field returns null
    // early. Flat guards read more clearly here than nested conditionals (matches the codebase
    // idiom used in VpnTunnelService.startTunnel).
    @Suppress("ReturnCount")
    private fun buildInternal(uri: String): String? {
        // Use java.net.URI for parsing — android.net.Uri is stubbed under
        // isReturnDefaultValues=true (unit tests) and would return empty strings.
        val parsed = try {
            java.net.URI(uri)
        } catch (e: java.net.URISyntaxException) {
            Log.d(TAG, "buildInternal: URI syntax invalid — returning null (${e.reason})")
            return null
        }

        if (parsed.scheme != "vless") {
            Log.d(TAG, "buildInternal: unsupported scheme '${parsed.scheme}' — returning null")
            return null
        }

        // UUID is the user-info component (before the '@').
        val uuid = parsed.userInfo?.takeIf { it.isNotBlank() } ?: run {
            Log.d(TAG, "buildInternal: missing UUID in user-info — returning null")
            return null
        }

        val host = parsed.host?.takeIf { it.isNotBlank() } ?: run {
            Log.d(TAG, "buildInternal: missing host — returning null")
            return null
        }

        val port = parsed.port.takeIf { it in 1..65535 } ?: run {
            Log.d(TAG, "buildInternal: missing or invalid port (${parsed.port}) — returning null")
            return null
        }

        val reality = parseReality(parsed.rawQuery ?: "") ?: return null

        return assembleJson(
            uuid = uuid,
            host = host,
            port = port,
            flow = reality.flow,
            pbk = reality.pbk,
            fp = reality.fp,
            sni = reality.sni,
            sid = reality.sid,
        )
    }

    /** The Reality/TCP stream parameters extracted from a VLESS URI query string. */
    private data class RealityParams(
        val pbk: String,
        val fp: String,
        val sni: String,
        val sid: String,
        val flow: String?,
    )

    /**
     * Extracts and validates the `type=tcp` + `security=reality` query parameters, returning
     * `null` when the transport/security is unsupported or a required Reality field is missing.
     */
    // ReturnCount: same linear guard-clause style as [buildInternal].
    @Suppress("ReturnCount")
    private fun parseReality(rawQuery: String): RealityParams? {
        val queryParams = parseQuery(rawQuery)

        val type = queryParams["type"]
        if (type != "tcp") {
            Log.d(TAG, "parseReality: unsupported type '$type' (only 'tcp' supported) — returning null")
            return null
        }

        val security = queryParams["security"]
        if (security != "reality") {
            Log.d(TAG, "parseReality: unsupported security '$security' (only 'reality') — returning null")
            return null
        }

        val pbk = queryParams["pbk"]?.takeIf { it.isNotBlank() } ?: run {
            Log.d(TAG, "parseReality: missing 'pbk' (publicKey) — returning null")
            return null
        }
        val fp = queryParams["fp"]?.takeIf { it.isNotBlank() } ?: run {
            Log.d(TAG, "parseReality: missing 'fp' (fingerprint) — returning null")
            return null
        }
        val sni = queryParams["sni"]?.takeIf { it.isNotBlank() } ?: run {
            Log.d(TAG, "parseReality: missing 'sni' (serverName) — returning null")
            return null
        }
        val sid = queryParams["sid"]?.takeIf { it.isNotBlank() } ?: run {
            Log.d(TAG, "parseReality: missing 'sid' (shortId) — returning null")
            return null
        }

        // Optional flow parameter — present only when non-blank.
        val flow = queryParams["flow"]?.takeIf { it.isNotBlank() }

        return RealityParams(pbk = pbk, fp = fp, sni = sni, sid = sid, flow = flow)
    }

    /**
     * Assembles the Xray-core JSON using the kotlinx.serialization element API.
     *
     * Every string value originates from the URI and is passed to [JsonPrimitive] —
     * the serialization layer handles escaping, so no URI-derived value can break out
     * of its JSON string context.
     */
    private fun assembleJson(
        uuid: String,
        host: String,
        port: Int,
        flow: String?,
        pbk: String,
        fp: String,
        sni: String,
        sid: String,
    ): String {
        val root = buildJsonObject {
            // ---- log (TEMPORARY — issue #111 device diagnostics) ----
            // loglevel=debug surfaces Xray's per-connection dial/REALITY-handshake results in
            // logcat (GoLog tag) so the return-path fix can be verified on-device. REVERT to the
            // Xray default (warning) before merging — debug logging is verbose and may echo
            // destination hostnames. TODO(#111): remove this block after device verification.
            putJsonObject("log") {
                put("loglevel", JsonPrimitive("debug"))
            }

            // ---- inbounds ----
            putJsonArray("inbounds") {
                add(
                    buildJsonObject {
                        put("tag", JsonPrimitive("socks-in"))
                        put("protocol", JsonPrimitive("socks"))
                        put("listen", JsonPrimitive("127.0.0.1"))
                        // Source the port from TunConfig.localSocksPort so hev and Xray never drift.
                        put("port", JsonPrimitive(localSocksPort))
                        putJsonObject("settings") {
                            put("auth", JsonPrimitive("noauth"))
                            put("udp", JsonPrimitive(true))
                        }
                    },
                )
            }

            // ---- outbounds ----
            putJsonArray("outbounds") {
                add(
                    buildJsonObject {
                        put("tag", JsonPrimitive("proxy-out"))
                        put("protocol", JsonPrimitive("vless"))
                        putJsonObject("settings") {
                            putJsonArray("vnext") {
                                add(
                                    buildJsonObject {
                                        put("address", JsonPrimitive(host))
                                        put("port", JsonPrimitive(port))
                                        putJsonArray("users") {
                                            add(
                                                buildJsonObject {
                                                    put("id", JsonPrimitive(uuid))
                                                    put("encryption", JsonPrimitive("none"))
                                                    if (flow != null) {
                                                        put("flow", JsonPrimitive(flow))
                                                    }
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        putJsonObject("streamSettings") {
                            put("network", JsonPrimitive("tcp"))
                            put("security", JsonPrimitive("reality"))
                            putJsonObject("realitySettings") {
                                put("serverName", JsonPrimitive(sni))
                                put("fingerprint", JsonPrimitive(fp))
                                put("publicKey", JsonPrimitive(pbk))
                                put("shortId", JsonPrimitive(sid))
                            }
                        }
                    },
                )
            }
        }

        return root.toString()
    }

    /**
     * Parses a URL query string into a key→value map.
     *
     * Entries without `=` are mapped to an empty-string value. Duplicate keys use the
     * last occurrence. URL percent-decoding is applied to both keys and values via
     * [java.net.URLDecoder] (UTF-8).
     */
    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").associate { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) {
                decode(pair) to ""
            } else {
                decode(pair.substring(0, idx)) to decode(pair.substring(idx + 1))
            }
        }
    }

    private fun decode(value: String): String = java.net.URLDecoder.decode(value, "UTF-8")
}
