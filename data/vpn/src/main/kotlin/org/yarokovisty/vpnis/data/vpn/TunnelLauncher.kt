package org.yarokovisty.vpnis.data.vpn

import android.content.Context
import android.util.Log

/**
 * Seam between [ConnectionControllerImpl] and the Android [VpnTunnelService].
 *
 * This interface exists to keep [ConnectionControllerImpl] free of Android framework
 * types ([android.app.Service], [android.content.Context]) so the controller's state
 * machine can be unit-tested on the JVM without Robolectric.
 *
 * The production binding ([AndroidTunnelLauncher]) sends explicit intents to
 * [VpnTunnelService]. A test double can replace it with a recording fake.
 */
internal interface TunnelLauncher {

    /**
     * Signals the OS to start the VPN tunnel for [server] using [configJson].
     *
     * [configJson] is the Xray-core JSON produced by [XrayConfigBuilder.build]. It is
     * attached to the explicit connect intent as [VpnTunnelService.EXTRA_CONFIG_JSON]
     * and must NOT be logged — it may contain server credentials.
     *
     * Implementations send a foreground-service start intent carrying the server
     * identity and the config so the service can start Xray without any repository lookup.
     */
    fun launch(server: org.yarokovisty.vpnis.core.domain.model.Server, configJson: String)

    /**
     * Signals the OS to stop the VPN tunnel.
     *
     * The service tears down hev + Xray and eventually calls back into
     * [TunnelStateSink.onTunnelStopped].
     */
    fun stop()
}

/**
 * Production [TunnelLauncher] that sends explicit intents to [VpnTunnelService].
 *
 * [launch] calls [Context.startForegroundService] so the service can call
 * [android.app.Service.startForeground] within 5 seconds (Android 8+ requirement).
 *
 * [stop] calls [Context.startService] with [VpnTunnelService.ACTION_DISCONNECT];
 * the service handles the action in [android.app.Service.onStartCommand] and calls
 * [VpnTunnelService.stopTunnel] → [android.app.Service.stopSelf].
 *
 * @param context Application context. Must not be an Activity context — the service
 *   outlives any single screen.
 */
internal class AndroidTunnelLauncher(private val context: Context) : TunnelLauncher {

    override fun launch(server: org.yarokovisty.vpnis.core.domain.model.Server, configJson: String) {
        Log.d(TAG, "launch: starting VpnTunnelService for server id=${server.id.value}")
        // configJson length is logged for diagnostic purposes; the value itself is NOT logged
        // — it may contain server credentials (security plan issue 1).
        Log.d(TAG, "launch: configJson length=${configJson.length}")
        val intent = VpnTunnelService.connectIntent(context).apply {
            putExtra(VpnTunnelService.EXTRA_SERVER_ID, server.id.value)
            putExtra(VpnTunnelService.EXTRA_CONFIG_JSON, configJson)
        }
        context.startForegroundService(intent)
    }

    override fun stop() {
        Log.d(TAG, "stop: sending DISCONNECT intent to VpnTunnelService")
        context.startService(VpnTunnelService.disconnectIntent(context))
    }

    private companion object {
        const val TAG = "AndroidTunnelLauncher"
    }
}
