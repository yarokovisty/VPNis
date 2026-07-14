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
     * Signals the OS to start the VPN tunnel for [server].
     *
     * Implementations send a foreground-service start intent carrying the server
     * identity so the service can retrieve the target server if needed.
     */
    fun launch(server: org.yarokovisty.vpnis.core.domain.model.Server)

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

    override fun launch(server: org.yarokovisty.vpnis.core.domain.model.Server) {
        Log.d(TAG, "launch: starting VpnTunnelService for server id=${server.id.value}")
        val intent = VpnTunnelService.connectIntent(context).apply {
            // Carry the server id as an extra so the service can retrieve the active
            // server from the repository if it needs to (e.g. for notification content).
            // The real server lookup belongs to ConnectionControllerImpl which already
            // holds currentTarget; this extra is informational only.
            putExtra(VpnTunnelService.EXTRA_SERVER_ID, server.id.value)
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
