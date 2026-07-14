package org.yarokovisty.vpnis.data.vpn

import android.util.Log

/**
 * Seam for the Xray-core proxy lifecycle.
 *
 * The real implementation (backed by the gomobile AAR from issue #72) calls the
 * `Invoke(json)` API from the `libXray` package, which starts an inbound SOCKS5
 * listener on [TunConfig.localSocksPort]. hev-socks5-tunnel then forwards all TUN
 * traffic to that local port.
 *
 * ## Socket protection
 *
 * The real libXray implementation will need to protect every outbound socket it
 * creates via [android.net.VpnService.protect] so that Xray's own traffic bypasses
 * the TUN interface and does not loop. The seam for that is intentionally left out
 * of this interface for now: [NoOpXrayCore] does not create any sockets, and wiring
 * a [VpnSocketProtector] into the real implementation is deferred to issue #72 where
 * the gomobile AAR is introduced. When that follow-up lands, extend [start] with a
 * `protector: VpnSocketProtector` parameter or inject it via the constructor.
 *
 * ## Follow-up: real libXray-backed implementation (issue #72)
 *
 * The AAR is not included in CI builds yet (#72). Once the AAR is available, add a
 * concrete `LibXrayCoreImpl` class in this module that:
 * 1. Receives a [VpnSocketProtector] (injected via constructor or parameter).
 * 2. Calls `com.v2ray.libv2ray.V2RayCore.startV2Ray(configJson)` (or the equivalent
 *    gomobile entrypoint from the chosen AAR build).
 * 3. Registers the protector with the libXray socket factory before starting.
 */
internal interface XrayCore {

    /**
     * Starts the Xray proxy with the given [configJson].
     *
     * [configJson] is a JSON string in Xray-core's configuration format, derived from
     * [Server.config] after protocol-specific parsing (VLESS/Reality URI → JSON).
     *
     * @return `true` if the proxy started successfully; `false` on failure (e.g. invalid
     *   config). The caller ([ConnectionControllerImpl]) should treat `false` as a
     *   [org.yarokovisty.vpnis.core.domain.model.ConnectionError.TunnelSetupFailed].
     */
    fun start(configJson: String): Boolean

    /** Stops the running Xray proxy, releasing the local SOCKS inbound port. */
    fun stop()
}

/**
 * No-op placeholder for [XrayCore] used until the real libXray gomobile AAR lands in
 * issue #72.
 *
 * [start] logs a TODO and returns `true` so that the tunnel flow proceeds end-to-end
 * (hev-socks5-tunnel starts, the TUN is established) even without a working proxy.
 * Traffic forwarding will fail at the SOCKS layer, but state-machine transitions and
 * notification updates work correctly for development and CI builds.
 *
 * Replace this binding in [vpnModule] with `LibXrayCoreImpl(get())` once #72 is done.
 */
internal class NoOpXrayCore : XrayCore {

    override fun start(configJson: String): Boolean {
        // TODO(libXray #72): replace with real gomobile AAR call —
        //   LibXrayCoreImpl.start(configJson) calls Invoke(json) from the libXray package.
        Log.d(TAG, "start: no-op — real libXray AAR wired in issue #72 (configJson length=${configJson.length})")
        return true
    }

    override fun stop() {
        // TODO(libXray #72): replace with real gomobile AAR teardown call.
        Log.d(TAG, "stop: no-op — real libXray AAR wired in issue #72")
    }

    private companion object {
        const val TAG = "NoOpXrayCore"
    }
}
