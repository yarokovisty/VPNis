package org.yarokovisty.vpnis.data.vpn

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Establishes and manages the Android TUN interface for the VPNis tunnel.
 *
 * ## Responsibility scope
 *
 * This service covers issues #61 and #62:
 * - Building the TUN via [VpnService.Builder]
 * - Protecting sockets via [VpnSocketProtector]
 * - Tracking the underlying (non-VPN) network via [ConnectivityManager]
 * - Running the hev-socks5-tunnel native loop in a background coroutine
 * - Foreground service with a user notification that has a Disconnect action (issue #62)
 *
 * Intentionally OUT OF SCOPE for this service:
 * - ConnectionController state machine, libXray start/stop → issue #63. See [TODO #63]
 *   comments marking the seam points. Live notification content also arrives via #63
 *   through [updateNotification].
 * - `<service>` AndroidManifest entry, `BIND_VPN_SERVICE` permission, and
 *   `android:foregroundServiceType="systemExempted"` → issue #65.
 *
 * ## Foreground service (issue #62)
 *
 * [startTunnel] calls [ServiceCompat.startForeground] with
 * [ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED] BEFORE [VpnService.Builder.establish]
 * so the OS does not kill the service when the user navigates away.
 *
 * **Android 14+ caveat:** [ServiceCompat.startForeground] will throw if the manifest
 * `android:foregroundServiceType="systemExempted"` attribute is absent (issue #65 adds it).
 * Until #65 lands, the throw is caught, logged, and [stopTunnel] is called so the process
 * does not crash — the tunnel simply will not start on API 34+ without the manifest entry.
 *
 * ## Threading model
 *
 * The service owns a [CoroutineScope] backed by [SupervisorJob] + [Dispatchers.IO].
 * A single child [Job] ([tunnelJob]) runs [Tun2SocksBridge.nativeStart], which blocks
 * the thread for the entire tunnel lifetime. All other work (network callbacks, cleanup)
 * executes on the service's main thread or inside [serviceScope] coroutines.
 *
 * ## fd ownership
 *
 * The [ParcelFileDescriptor] returned by [VpnService.Builder.establish] is owned by this
 * service. Its raw fd is passed to [Tun2SocksBridge.nativeStart] by value (not
 * transferred — the native side borrows it and does NOT close it). [stopTunnel] closes
 * the [ParcelFileDescriptor] only after [tunnelJob] completes, ensuring the fd stays
 * valid for the entire native loop lifetime.
 *
 * ## Library availability
 *
 * `libhev_tun2socks.so` is only present when the project is built with
 * `-Pvpnis.buildNative=true` (issue #72). On developer builds without the native library,
 * [Tun2SocksBridge]'s `init` block will throw [UnsatisfiedLinkError]. This is expected
 * and documented — the service cannot function until #72 lands on a device.
 */
@Suppress("Registered") // issue #65 registers <service> + BIND_VPN_SERVICE in the manifest
internal class VpnTunnelService :
    VpnService(),
    VpnSocketProtector {

    // -------------------------------------------------------------------------
    // Companion — actions and intent factories
    // -------------------------------------------------------------------------

    companion object {

        private const val TAG = "VpnTunnelService"

        /** Intent action to start the VPN tunnel. */
        const val ACTION_CONNECT = "org.yarokovisty.vpnis.data.vpn.action.CONNECT"

        /** Intent action to stop the VPN tunnel. */
        const val ACTION_DISCONNECT = "org.yarokovisty.vpnis.data.vpn.action.DISCONNECT"

        /** Creates a start-tunnel intent pre-filled with [ACTION_CONNECT]. */
        fun connectIntent(context: Context): Intent =
            Intent(ACTION_CONNECT).apply { setClass(context, VpnTunnelService::class.java) }

        /** Creates a stop-tunnel intent pre-filled with [ACTION_DISCONNECT]. */
        fun disconnectIntent(context: Context): Intent =
            Intent(ACTION_DISCONNECT).apply { setClass(context, VpnTunnelService::class.java) }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /**
     * Service-owned coroutine scope. Cancelled in [onDestroy] to guarantee cleanup even
     * if [stopTunnel] was not called explicitly (e.g. after a low-memory kill).
     *
     * [SupervisorJob] ensures that a failure in [tunnelJob] does not cancel other child
     * coroutines that may be added in future issues.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Active tunnel coroutine. Non-null while hev's event loop is running. */
    private var tunnelJob: Job? = null

    /** Open TUN file descriptor. Closed in [stopTunnel] after [tunnelJob] joins. */
    private var tunPfd: ParcelFileDescriptor? = null

    /** Tracks whether a tunnel is already running to prevent double-start. */
    private var isRunning = false

    /** Manages the underlying (non-VPN) network callback. */
    private var networkMonitor: UnderlyingNetworkMonitor? = null

    // -------------------------------------------------------------------------
    // VpnSocketProtector
    // -------------------------------------------------------------------------

    /**
     * Protects [socket] by delegating to [VpnService.protect].
     *
     * After this call the socket bypasses the TUN interface and communicates directly
     * with the underlying network. Issue #63's libXray dialer calls this for every
     * outbound socket it creates, preventing infinite traffic loops.
     *
     * Must call `super.protect(socket)` — `protect(socket)` alone would re-enter this
     * override and recurse infinitely (this method overrides `VpnService.protect(int)`).
     */
    override fun protect(socket: Int): Boolean = super.protect(socket)

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        // Create the notification channel once at service start.
        // NotificationChannel is available since API 26 (our minSdk) — no version guard needed.
        // createNotificationChannel is idempotent: safe to call on every onCreate.
        TunnelNotifications.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = when (intent?.action) {
        ACTION_DISCONNECT -> {
            stopTunnel()
            START_NOT_STICKY
        }
        else -> {
            startTunnel()
            START_STICKY
        }
    }

    override fun onRevoke() {
        // The system revoked consent (e.g. user toggled VPN off from Settings, or another
        // VPN app was started). Tear down cleanly.
        // NOTE: connect/disconnect race hardening and automatic reconnect are issue #64.
        // Keep this minimal — just stop.
        Log.i(TAG, "onRevoke: VPN permission revoked by system, stopping tunnel")
        stopTunnel()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure cleanup even if onRevoke / stopTunnel was not called (e.g. process kill).
        stopTunnel()
        serviceScope.cancel()
    }

    // -------------------------------------------------------------------------
    // Tunnel management
    // -------------------------------------------------------------------------

    private fun startTunnel() {
        if (isRunning) {
            Log.d(TAG, "startTunnel: tunnel already running, ignoring duplicate start")
            return
        }

        val config = TunConfig()

        // Promote to foreground BEFORE establish() so the OS cannot kill us
        // while we are building the TUN interface.
        //
        // ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED requires:
        //   - FOREGROUND_SERVICE_SYSTEM_EXEMPTED uses-permission  ─┐ both from issue #65
        //   - android:foregroundServiceType="systemExempted"       ─┘
        //
        // On API 34+ (Android 14) startForeground throws if the manifest type is not
        // declared. We catch that here and tear down cleanly instead of crashing the process.
        // Once #65 adds the manifest entry this catch branch will never execute.
        //
        // NOTE: POST_NOTIFICATIONS runtime permission is NOT checked here. On API 33+,
        // if the permission is absent the notification simply will not display in the
        // shade, but the foreground service itself still runs. Requesting the permission
        // at the Activity layer belongs to issue #67.
        try {
            val notification = TunnelNotifications.build(context = this)
            ServiceCompat.startForeground(
                this,
                TunnelNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } catch (e: Exception) {
            // Covers SecurityException (manifest foregroundServiceType missing on API 34+),
            // ForegroundServiceStartNotAllowedException (API 31+, background-start restriction),
            // and any other startForeground failure. Log and abort — no tunnel without FGS
            // on modern API levels since the service would be immediately killed.
            Log.e(TAG, "startTunnel: startForeground failed — manifest type from #65 required on API 34+", e)
            stopSelf()
            return
        }

        val pfd: ParcelFileDescriptor = buildTun(config) ?: run {
            // null from establish() means VPN permission was not granted. Issue #57's
            // permission-request flow is the gate that should prevent us from reaching here
            // without consent. Treat it as a defensive fallback.
            Log.e(TAG, "startTunnel: establish() returned null — VPN permission not granted")
            stopSelf()
            return
        }

        tunPfd = pfd
        isRunning = true

        // Start monitoring the underlying (non-VPN) network so we can call
        // setUnderlyingNetworks() as the physical network changes.
        networkMonitor = UnderlyingNetworkMonitor(
            service = this,
            connectivityManager = getSystemService(ConnectivityManager::class.java),
        ).also { it.register() }

        // TODO(#63): Notify ConnectionController that the TUN is established and the
        //   tunnel is now in the Connecting → Connected transition. A hook here might look
        //   like:
        //     connectionController.onTunnelEstablished(tunFd = pfd.fd)
        //   and the controller would then start Xray-core and update VpnConnectionState.
        //   Do NOT start Xray here — the ConnectionController owns that lifecycle.

        // Launch the blocking hev event loop on a background thread (Dispatchers.IO).
        // pfd.fd is borrowed; pfd itself is kept alive in tunPfd and closed in stopTunnel()
        // after this job completes, guaranteeing the fd remains valid for the native loop.
        val tunFd = pfd.fd
        val yamlConfig = config.toTun2SocksConfig().toYaml()

        tunnelJob = serviceScope.launch {
            Log.i(TAG, "startTunnel: starting hev native loop (tunFd=$tunFd)")
            val exitCode = Tun2SocksBridge.nativeStart(configYaml = yamlConfig, tunFd = tunFd)
            Log.i(TAG, "startTunnel: hev native loop exited (code=$exitCode)")
        }
    }

    private fun stopTunnel() {
        if (!isRunning && tunnelJob == null) {
            // Already stopped — idempotent.
            return
        }

        Log.i(TAG, "stopTunnel: signalling hev to quit")

        // 1. Signal hev to exit its event loop asynchronously.
        //    nativeStop() is safe to call from any thread and is a no-op if no tunnel runs.
        Tun2SocksBridge.nativeStop()

        // 2. Cancel the coroutine wrapper and wait for hev to drain in-flight events.
        //    We do NOT use runBlocking here — stopTunnel() may be called from the main
        //    thread (onRevoke, onDestroy) and blocking it would risk an ANR if hev takes
        //    too long. Instead we cancel the job; the fd is closed after the job's
        //    cooperative cancellation (hev returns from nativeStart once nativeStop() is
        //    processed). A future issue (#64) may add a timeout + forced fd close here.
        tunnelJob?.cancel()
        tunnelJob = null

        // 3. Unregister the network callback before closing the fd to avoid callbacks
        //    arriving on a torn-down service.
        networkMonitor?.unregister()
        networkMonitor = null

        // 4. Close the ParcelFileDescriptor. The native side has already returned from
        //    nativeStart (or will when the cancelled coroutine resumes), so this is safe.
        //    Closing the PFD closes the underlying fd and releases the TUN interface.
        tunPfd?.close()
        tunPfd = null

        // TODO(#63): Notify ConnectionController that the tunnel has stopped so it can
        //   update VpnConnectionState to Disconnected (or Error if the exit was unexpected).
        //   A hook here might look like:
        //     connectionController.onTunnelStopped()

        // Remove the foreground notification now that the tunnel is stopped.
        // STOP_FOREGROUND_REMOVE dismisses the notification immediately; the service
        // continues running until stopSelf() below causes it to be destroyed.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        isRunning = false
        stopSelf()
    }

    // -------------------------------------------------------------------------
    // Notification update seam (issue #63)
    // -------------------------------------------------------------------------

    /**
     * Re-posts the foreground service notification with updated [content].
     *
     * Must be called from the main thread (or any thread — [NotificationManager.notify]
     * is thread-safe). Callers on a background coroutine can invoke this directly.
     *
     * ## Seam for issue #63
     *
     * ConnectionController will call this as the VPN connection state changes
     * (e.g. Connecting → Connected → with server name and session timer).
     * The notification is only re-posted when the tunnel is already running; calling
     * this when [isRunning] is false is a no-op so callers do not need to guard against
     * timing races during startup.
     *
     * Example from issue #63:
     * ```kotlin
     * // TODO(#63): connectionController.state.onEach { state ->
     * //     updateNotification(state.toNotificationContent())
     * // }.launchIn(serviceScope)
     * ```
     *
     * @param content New content to display. Defaults to [NotificationContent.Default].
     */
    internal fun updateNotification(content: NotificationContent = NotificationContent.Default) {
        if (!isRunning) return
        val notification = TunnelNotifications.build(context = this, content = content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(TunnelNotifications.NOTIFICATION_ID, notification)
    }

    // -------------------------------------------------------------------------
    // TUN builder
    // -------------------------------------------------------------------------

    /**
     * Builds and establishes the TUN interface from [config].
     *
     * Returns the [ParcelFileDescriptor] on success, or `null` if the OS denies the
     * VPN permission ([VpnService.Builder.establish] returns null when consent is absent).
     *
     * Route strings in [TunConfig.routes] must be in `"address/prefixLength"` format.
     * Malformed entries are logged and skipped rather than crashing the service.
     */
    private fun buildTun(config: TunConfig): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(config.session)
            .addAddress(config.clientAddress, config.prefixLength)
            .setMtu(config.mtu)
            .setBlocking(true)

        config.dnsServers.forEach { dns ->
            builder.addDnsServer(dns)
        }

        config.routes.forEach { cidr ->
            val parts = cidr.split("/")
            if (parts.size == 2) {
                val address = parts[0]
                val prefix = parts[1].toIntOrNull()
                if (prefix != null && prefix in 0..32) {
                    builder.addRoute(address, prefix)
                } else {
                    Log.w(TAG, "buildTun: skipping malformed route prefix '$cidr'")
                }
            } else {
                Log.w(TAG, "buildTun: skipping malformed route '$cidr'")
            }
        }

        return builder.establish()
    }
}

// =============================================================================
// UnderlyingNetworkMonitor — internal helper
// =============================================================================

/**
 * Registers a [ConnectivityManager.NetworkCallback] to track the physical (non-VPN)
 * network and keeps [VpnService.setUnderlyingNetworks] up to date.
 *
 * ## Why a filtered [NetworkRequest] instead of [ConnectivityManager.registerDefaultNetworkCallback]
 *
 * [registerDefaultNetworkCallback] would deliver the VPN's own virtual network as the
 * "default" once it is active, causing [setUnderlyingNetworks] to be called with the TUN
 * interface — exactly the opposite of what we want. By requesting `NET_CAPABILITY_INTERNET`
 * + `NET_CAPABILITY_NOT_VPN` we filter to physical networks (Wi-Fi, cellular) only.
 *
 * NOTE: [ConnectivityManager.requestNetwork] with a [NetworkRequest] requires the
 * `android.permission.CHANGE_NETWORK_STATE` permission, which issue #65 adds to the
 * AndroidManifest alongside `<uses-permission android:name="android.permission.INTERNET" />`
 * and the `<service>` entry.
 */
private class UnderlyingNetworkMonitor(
    private val service: VpnService,
    private val connectivityManager: ConnectivityManager,
) {

    private val availableNetworks = mutableSetOf<Network>()

    private val callback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            availableNetworks += network
            service.setUnderlyingNetworks(availableNetworks.toTypedArray())
        }

        override fun onLost(network: Network) {
            availableNetworks -= network
            service.setUnderlyingNetworks(availableNetworks.toTypedArray())
        }
    }

    private val networkRequest: NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .build()

    // ACCESS_NETWORK_STATE is declared in :app's AndroidManifest — issue #65.
    // The library manifest intentionally omits permissions; the merged manifest at
    // build time includes them from :app. Suppress here; the permission IS present
    // at runtime when the service runs inside the assembled APK.
    @SuppressLint("MissingPermission")
    fun register() {
        connectivityManager.registerNetworkCallback(networkRequest, callback)
    }

    fun unregister() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        availableNetworks.clear()
        service.setUnderlyingNetworks(null)
    }
}
