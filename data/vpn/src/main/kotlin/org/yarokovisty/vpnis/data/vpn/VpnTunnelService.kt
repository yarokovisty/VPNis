package org.yarokovisty.vpnis.data.vpn

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import org.yarokovisty.vpnis.core.domain.model.ConnectionError

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
internal class VpnTunnelService :
    VpnService(),
    VpnSocketProtector {

    // -------------------------------------------------------------------------
    // Koin injections (issue #63)
    // -------------------------------------------------------------------------

    /**
     * The process-scoped state-machine sink. Injected as [TunnelStateSink] so the
     * service depends only on the narrow callback interface, not on [ConnectionControllerImpl].
     * [ConnectionControllerImpl] implements [TunnelStateSink] alongside [ConnectionController].
     */
    private val stateSink: TunnelStateSink by inject()

    /**
     * Xray-core proxy lifecycle. [NoOpXrayCore] in all builds until issue #72 lands.
     * Start before the hev loop so the SOCKS inbound is ready when hev begins forwarding.
     */
    private val xrayCore: XrayCore by inject()

    // -------------------------------------------------------------------------
    // Companion — actions and intent factories
    // -------------------------------------------------------------------------

    companion object {

        private const val TAG = "VpnTunnelService"

        /** Intent action to start the VPN tunnel. */
        const val ACTION_CONNECT = "org.yarokovisty.vpnis.data.vpn.action.CONNECT"

        /** Intent action to stop the VPN tunnel. */
        const val ACTION_DISCONNECT = "org.yarokovisty.vpnis.data.vpn.action.DISCONNECT"

        /**
         * Optional String extra on [ACTION_CONNECT] intents carrying the target server's id
         * ([org.yarokovisty.vpnis.core.domain.model.ServerId.value]).
         *
         * The service does not need the id for its own operation — [ConnectionControllerImpl]
         * already holds [ConnectionControllerImpl.currentTarget]. This extra is informational:
         * future issues may use it to display the server name in the notification before
         * [TunnelStateSink.onTunnelEstablished] fires.
         */
        const val EXTRA_SERVER_ID = "org.yarokovisty.vpnis.data.vpn.extra.SERVER_ID"

        /**
         * String extra on [ACTION_CONNECT] intents carrying the Xray-core JSON configuration.
         *
         * Built by [XrayConfigBuilder.build] inside [ConnectionControllerImpl.connect] from the
         * target server's VLESS URI, and transported here via [AndroidTunnelLauncher.launch].
         *
         * **This value MUST NOT be logged** — it contains server credentials (security plan
         * issue 1). Only its length may be logged for diagnostic purposes.
         *
         * If this extra is absent or blank (e.g. on a sticky-restart where the OS drops extras
         * because [ACTION_CONNECT] uses [START_NOT_STICKY]), [startTunnel] treats it as a
         * [org.yarokovisty.vpnis.core.domain.model.ConnectionError.TunnelSetupFailed] and stops
         * the service rather than starting with an empty or missing config.
         */
        const val EXTRA_CONFIG_JSON = "org.yarokovisty.vpnis.data.vpn.extra.CONFIG_JSON"

        /**
         * Grace period after the hev native loop returns before the TUN fd and SOCKS port are
         * released, so a fast disconnect→reconnect does not hit an "address already in use"
         * (issue #64, v2rayNG pitfall). Kept short — the real wait is the [Job.join] below.
         */
        private const val GRACE_STOP_DELAY_MS = 100L

        /**
         * Upper bound on waiting for the hev native loop to return after [Tun2SocksBridge.nativeStop].
         * If hev misbehaves and never returns, teardown proceeds anyway rather than hanging the
         * service forever.
         */
        private const val STOP_JOIN_TIMEOUT_MS = 2_000L

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

    /**
     * Set for the duration of a teardown so a second [stopTunnel] (e.g. onRevoke racing
     * onDestroy, or ACTION_DISCONNECT arriving twice) becomes a no-op — guards against
     * double-close of the fd and a duplicate [TunnelStateSink.onTunnelStopped].
     */
    @Volatile
    private var isStopping = false

    /**
     * Guards the small critical sections that mutate [isRunning] / [isStopping] / [tunPfd] /
     * [tunnelJob]. Lifecycle callbacks can arrive on different threads — onStartCommand /
     * onDestroy on main, onRevoke on a binder thread, and the teardown coroutine on IO — so
     * the transitions are synchronized while the long-running work (join, native calls) runs
     * outside the lock.
     */
    private val lifecycleLock = Any()

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
            // Pass the intent so startTunnel() can read EXTRA_CONFIG_JSON.
            // START_NOT_STICKY: avoids persisting the credential-bearing EXTRA_CONFIG_JSON in
            // the OS sticky-intent store. If the service is killed and restarted by the OS, the
            // extras are dropped — startTunnel() treats a missing config as TunnelSetupFailed
            // and stops cleanly. The controller (#64 reconnect logic) drives re-establishment.
            startTunnel(connectIntent = intent)
            START_NOT_STICKY
        }
    }

    override fun onRevoke() {
        // The system revoked consent (user toggled VPN off in Settings, or another VPN app
        // started). onRevoke is delivered on a BINDER thread, NOT the main thread — but
        // stopTunnel() only does thread-safe synchronous work here (guard flip + nativeStop +
        // callback unregister) and hands the blocking teardown to serviceScope, so it is safe
        // to call from any thread. The UI transition is driven by stateSink.onTunnelStopped().
        Log.i(TAG, "onRevoke: VPN permission revoked by system, stopping tunnel")
        stopTunnel()
    }

    override fun onDestroy() {
        // Best-effort SYNCHRONOUS cleanup for the case where the service is destroyed without a
        // completed stopTunnel() (e.g. a low-memory kill). If the async teardown already ran,
        // tunPfd is null and the close is a no-op — guarded against double-close. Cancelling the
        // scope last stops any in-flight teardown coroutine (which, if it ran, already finished).
        Tun2SocksBridge.nativeStop()
        synchronized(lifecycleLock) {
            runCatching { tunPfd?.close() }
            tunPfd = null
            isRunning = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Tunnel management
    // -------------------------------------------------------------------------

    // ReturnCount: guard-clause early returns (config-missing / foreground / xray / establish
    //   failures) read more clearly than nested conditionals for this linear startup sequence.
    // TooGenericExceptionCaught: startForeground() can throw SecurityException,
    //   ForegroundServiceStartNotAllowedException, or IllegalStateException — all handled
    //   identically (log, report error, tear down), so catching the common base is intentional.
    // LongMethod: the linear connect sequence (guard → read config → foreground → xray →
    //   establish → hev → notify) is clearest kept together; each step is short and commented.
    @Suppress("ReturnCount", "TooGenericExceptionCaught", "LongMethod")
    private fun startTunnel(connectIntent: Intent?) {
        synchronized(lifecycleLock) {
            if (isRunning || isStopping) {
                // Ignore a start that arrives while running, or while a teardown is still
                // releasing the fd/port (a too-fast reconnect) — the caller retries once the
                // state settles to Disconnected.
                Log.d(TAG, "startTunnel: already running or stopping, ignoring start")
                return
            }
        }

        // Read the Xray JSON config from the connect intent. A null or blank value means the
        // intent was either missing (framework-restarted with no extras after a kill, which
        // cannot happen here because we use START_NOT_STICKY) or the caller did not attach
        // the extra. In either case, starting with no config would open a listening SOCKS port
        // pointing at nothing — treat it as TunnelSetupFailed and stop cleanly.
        //
        // EXTRA_CONFIG_JSON is NOT logged — it contains server credentials.
        val configJson = connectIntent?.getStringExtra(EXTRA_CONFIG_JSON)
        if (configJson.isNullOrBlank()) {
            Log.e(TAG, "startTunnel: EXTRA_CONFIG_JSON is null or blank — aborting tunnel setup")
            stateSink.onTunnelError(reason = ConnectionError.TunnelSetupFailed)
            stopSelf()
            return
        }
        Log.d(TAG, "startTunnel: configJson length=${configJson.length}")

        val config = TunConfig()

        // 1. Promote to foreground BEFORE establish() so the OS cannot kill us
        //    while we are building the TUN interface.
        //
        //    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED requires:
        //      - FOREGROUND_SERVICE_SYSTEM_EXEMPTED uses-permission  ─┐ both from issue #65
        //      - android:foregroundServiceType="systemExempted"       ─┘
        //
        //    On API 34+ (Android 14) startForeground throws if the manifest type is not
        //    declared. We catch that here and tear down cleanly instead of crashing the process.
        //    Once #65 adds the manifest entry this catch branch will never execute.
        //
        //    NOTE: POST_NOTIFICATIONS runtime permission is NOT checked here. On API 33+,
        //    if the permission is absent the notification simply will not display in the
        //    shade, but the foreground service itself still runs. Requesting the permission
        //    at the Activity layer belongs to issue #67.
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
            stateSink.onTunnelError(reason = ConnectionError.TunnelSetupFailed)
            stopSelf()
            return
        }

        // 2. Start Xray-core BEFORE establishing the TUN and starting hev, so the SOCKS5
        //    inbound port is ready when hev starts forwarding traffic.
        //
        //    configJson was read from EXTRA_CONFIG_JSON above (built by XrayConfigBuilder in
        //    ConnectionControllerImpl.connect). Passing `this` as the VpnSocketProtector so
        //    LibXrayCoreImpl can register it with libXray's DialerController before starting
        //    the proxy — every outbound socket Xray creates will then bypass the TUN.
        //    NoOpXrayCore ignores the protector (it opens no sockets).
        val xrayStarted = xrayCore.start(configJson = configJson, protector = this)
        if (!xrayStarted) {
            Log.e(TAG, "startTunnel: xrayCore.start() returned false — aborting tunnel setup")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stateSink.onTunnelError(reason = ConnectionError.TunnelSetupFailed)
            stopSelf()
            return
        }

        // 3. Establish the TUN interface (calls VpnService.Builder.establish()).
        val pfd: ParcelFileDescriptor = buildTun(config) ?: run {
            // null from establish() means VPN permission was not granted.
            // Issue #57's permission-request flow is the gate that should prevent reaching
            // here without consent. Treat it defensively and signal the controller so the
            // presentation layer can trigger VpnService.prepare() again.
            Log.e(TAG, "startTunnel: establish() returned null — VPN permission not granted")
            xrayCore.stop() // roll back Xray — TUN never came up
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stateSink.onPermissionRequired()
            stopSelf()
            return
        }

        synchronized(lifecycleLock) {
            tunPfd = pfd
            isRunning = true
        }

        // 4. Start monitoring the underlying (non-VPN) network so we can call
        //    setUnderlyingNetworks() as the physical network changes.
        networkMonitor = UnderlyingNetworkMonitor(
            service = this,
            connectivityManager = getSystemService(ConnectivityManager::class.java),
        ).also { it.register() }

        // 5. Launch the blocking hev event loop on a background thread (Dispatchers.IO).
        //    pfd.fd is borrowed; pfd itself is kept alive in tunPfd and closed in stopTunnel()
        //    after this job completes, guaranteeing the fd remains valid for the native loop.
        val tunFd = pfd.fd
        val yamlConfig = config.toTun2SocksConfig().toYaml()

        tunnelJob = serviceScope.launch {
            Log.i(TAG, "startTunnel: starting hev native loop (tunFd=$tunFd)")
            val exitCode = Tun2SocksBridge.nativeStart(configYaml = yamlConfig, tunFd = tunFd)
            Log.i(TAG, "startTunnel: hev native loop exited (code=$exitCode)")
        }

        // 6. Notify the ConnectionController that the TUN is up and the tunnel is active.
        //    This drives Connecting → Connected in the state machine and updates the
        //    notification with live content.
        stateSink.onTunnelEstablished()
        updateNotification(content = NotificationContent.Default)
    }

    private fun stopTunnel() {
        synchronized(lifecycleLock) {
            if (isStopping || (!isRunning && tunnelJob == null)) {
                // Already stopped, or a teardown is already in progress — idempotent. This is
                // the double-close guard: onRevoke, onDestroy and a duplicate ACTION_DISCONNECT
                // can all race, but only the first drives the teardown.
                return
            }
            isStopping = true
        }

        Log.i(TAG, "stopTunnel: signalling hev to quit")

        // 1. Signal hev to exit its event loop. Thread-safe and a no-op if nothing is running,
        //    so this is safe to invoke from onRevoke's binder thread.
        Tun2SocksBridge.nativeStop()

        // 2. Unregister the underlying-network callback now — no further updates are wanted,
        //    and doing it before the async release avoids callbacks landing on a torn service.
        networkMonitor?.unregister()
        networkMonitor = null

        // 3. Finish teardown OFF the calling thread. We await the hev loop's actual return
        //    (Job.join, bounded by STOP_JOIN_TIMEOUT_MS) BEFORE releasing the fd and the SOCKS
        //    port, then a short grace delay. This is the correct form of v2rayNG's
        //    stopSelf -> sleep -> close ordering: closing the fd while hev still holds it, or
        //    reclaiming the SOCKS port too early, is exactly what leaves a busy port on the
        //    next connect. Crucially we never block the caller (main / binder), so no ANR.
        serviceScope.launch {
            withTimeoutOrNull(STOP_JOIN_TIMEOUT_MS) { tunnelJob?.join() }
            delay(GRACE_STOP_DELAY_MS)
            finishTeardown()
        }
    }

    /**
     * Releases tunnel resources after the hev loop has returned. Runs on [serviceScope] (IO).
     *
     * The body has no suspension points, so once it begins it runs to completion even if
     * [serviceScope] is cancelled concurrently by [onDestroy] — no half-torn state. The
     * fd-close is guarded by [lifecycleLock] against a concurrent [onDestroy] double-close.
     */
    private fun finishTeardown() {
        synchronized(lifecycleLock) {
            tunnelJob = null
            runCatching { tunPfd?.close() }
            tunPfd = null
        }

        // Stop Xray-core last: releases the local SOCKS inbound port for the next connect.
        xrayCore.stop()

        // Drive the state machine to Disconnected (isLegalTransition makes it a no-op if already so).
        stateSink.onTunnelStopped()

        // Drop the foreground notification; stopSelf() then destroys the service.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        synchronized(lifecycleLock) {
            isRunning = false
            isStopping = false
        }
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
                if (prefix != null && prefix in 0..TunConfig.MAX_PREFIX_LENGTH) {
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

    // ACCESS_NETWORK_STATE is declared in this module's AndroidManifest (issue #65),
    // so registerNetworkCallback() is permitted.
    fun register() {
        connectivityManager.registerNetworkCallback(networkRequest, callback)
    }

    fun unregister() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        availableNetworks.clear()
        service.setUnderlyingNetworks(null)
    }
}
