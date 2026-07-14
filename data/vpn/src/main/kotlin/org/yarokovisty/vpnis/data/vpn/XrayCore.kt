package org.yarokovisty.vpnis.data.vpn

import android.util.Log

/**
 * Seam for the Xray-core proxy lifecycle.
 *
 * The real implementation is backed by `XrayCore.aar` — the gomobile binding of the
 * pinned SaeedDev94/libXray submodule (ADR 0001, commit `f6ce612`). That AAR is built
 * by the native-build CI job (#72); wiring the concrete impl into Koin is the #66 swap.
 * Starting the proxy opens an inbound SOCKS5 listener on [TunConfig.localSocksPort];
 * hev-socks5-tunnel then forwards all TUN traffic to that local port.
 *
 * ## Socket protection
 *
 * The real implementation must protect every outbound socket libXray creates via
 * [android.net.VpnService.protect] so Xray's own traffic bypasses the TUN and does not
 * loop. libXray exposes this as the **`DialerController`** callback interface
 * (`fun ProtectFd(fd: Int): Boolean`): register an Android implementation that calls
 * [VpnSocketProtector]/[android.net.VpnService.protect] via
 * `Libxray.registerDialerController(controller)` **before** starting the proxy.
 * [NoOpXrayCore] creates no sockets, so it needs no protector; the real impl takes a
 * [VpnSocketProtector] via its constructor.
 *
 * ## Follow-up: real libXray-backed implementation (issue #66)
 *
 * Add a concrete `LibXrayCoreImpl(protector: VpnSocketProtector)` in a
 * `vpnis.buildNative`-only source set (so default builds without the AAR still compile)
 * that drives the fork's base64 `CallResponse` envelope API (package `libXray`,
 * gomobile class `Libxray`). Verify the exact generated Java names at the first CI bind.
 * 1. Register protection: `Libxray.registerDialerController { fd -> protector.protect(fd) }`.
 * 2. (TUN-in-core path, optional) `Libxray.setTunFd(fd)` before start.
 * 3. Build the request: `val req = Libxray.newXrayRunFromJSONRequest(datDir, configJson)`
 *    — returns a base64(JSON `{datDir, configJSON}`) string.
 * 4. Start: `val resp = Libxray.runXrayFromJSON(req)` — returns base64(JSON
 *    `{success, data, error}`). Decode base64 → parse JSON → treat `success == false`
 *    (non-empty `error`) as [org.yarokovisty.vpnis.core.domain.model.ConnectionError.TunnelSetupFailed].
 * 5. Stop: `Libxray.stopXray()` (same envelope); `Libxray.getXrayState()` for liveness.
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
 * Replace this binding in [vpnModule] with `LibXrayCoreImpl(get())` once #66 is done.
 */
internal class NoOpXrayCore : XrayCore {

    override fun start(configJson: String): Boolean {
        // TODO(libXray #66): replace with LibXrayCoreImpl.start(configJson) —
        //   Libxray.newXrayRunFromJSONRequest(datDir, configJson) → Libxray.runXrayFromJSON(req).
        Log.d(TAG, "start: no-op — real libXray AAR wired in issue #66 (configJson length=${configJson.length})")
        return true
    }

    override fun stop() {
        // TODO(libXray #66): replace with LibXrayCoreImpl.stop() → Libxray.stopXray().
        Log.d(TAG, "stop: no-op — real libXray AAR wired in issue #66")
    }

    private companion object {
        const val TAG = "NoOpXrayCore"
    }
}
