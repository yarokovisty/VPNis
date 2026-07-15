package org.yarokovisty.vpnis.data.vpn

import android.util.Log

/**
 * Seam for the Xray-core proxy lifecycle.
 *
 * The real implementation is backed by `XrayCore.aar` — the gomobile binding of the
 * pinned SaeedDev94/libXray submodule (ADR 0001). That AAR is built by the native-build
 * CI job (#72) and consumed by [LibXrayCoreImpl] via [LibxrayApi] ([RealLibxrayApi] in
 * the `buildNative` source set). Wiring the concrete impl into Koin via
 * [XrayCoreProvider] is the #66 swap.
 *
 * Starting the proxy opens an inbound SOCKS5 listener on [TunConfig.localSocksPort];
 * hev-socks5-tunnel then forwards all TUN traffic to that local port.
 *
 * ## Socket protection
 *
 * libXray exposes the `DialerController` callback interface so every outbound socket it
 * creates can be protected from being routed back through the TUN. The protector is
 * passed to [start] at call time (not at construction) so that:
 * - [VpnTunnelService] — the framework-created [VpnSocketProtector] — can be passed in
 *   directly without Koin involvement.
 * - The registration (`Libxray.registerDialerController`) happens atomically before the
 *   proxy start, with no window between construction and use.
 * - [NoOpXrayCore] simply ignores the protector (it opens no sockets).
 *
 * [LibXrayCoreImpl] calls `api.registerDialerController { fd -> protector.protect(fd) }`
 * **before** `api.runFromJson(datDir, configJson)` — this ordering is mandatory and is
 * asserted by `LibXrayCoreImplTest`.
 */
internal interface XrayCore {

    /**
     * Starts the Xray proxy.
     *
     * [configJson] is a JSON string in Xray-core's configuration format, produced by
     * [XrayConfigBuilder.build] from the target [Server.config] (a VLESS/Reality URI).
     *
     * [protector] is the [VpnSocketProtector] that will be registered with libXray's
     * `DialerController` before the proxy starts, so Xray's own outbound sockets bypass
     * the TUN interface and do not loop. [NoOpXrayCore] ignores [protector].
     *
     * Implementations MUST register [protector] strictly before starting the proxy.
     *
     * @return `true` if the proxy started successfully; `false` on failure (e.g. invalid
     *   config or libXray start error). The caller ([VpnTunnelService]) treats `false` as
     *   [org.yarokovisty.vpnis.core.domain.model.ConnectionError.TunnelSetupFailed].
     */
    fun start(configJson: String, protector: VpnSocketProtector): Boolean

    /** Stops the running Xray proxy, releasing the local SOCKS inbound port. */
    fun stop()
}

/**
 * No-op placeholder for [XrayCore] used until the real libXray gomobile AAR lands.
 *
 * [start] logs a TODO and returns `true` so that the tunnel flow proceeds end-to-end
 * (hev-socks5-tunnel starts, the TUN is established) even without a working proxy.
 * Traffic forwarding will fail at the SOCKS layer, but state-machine transitions and
 * notification updates work correctly for development and CI builds.
 *
 * The [protector] argument is intentionally ignored — [NoOpXrayCore] opens no sockets
 * and therefore needs no protection.
 *
 * Replace this binding in [vpnModule] with [XrayCoreProvider.create] once #66 is done.
 */
internal class NoOpXrayCore : XrayCore {

    override fun start(configJson: String, protector: VpnSocketProtector): Boolean {
        // no-op: real libXray AAR is wired via XrayCoreProvider/LibXrayCoreImpl in issue #66.
        // Protector is intentionally ignored — this impl opens no sockets.
        Log.d(TAG, "start: no-op — real libXray AAR wired in issue #66 (configJson length=${configJson.length})")
        return true
    }

    override fun stop() {
        // no-op: real libXray AAR is wired via XrayCoreProvider/LibXrayCoreImpl in issue #66.
        Log.d(TAG, "stop: no-op — real libXray AAR wired in issue #66")
    }

    private companion object {
        const val TAG = "NoOpXrayCore"
    }
}
