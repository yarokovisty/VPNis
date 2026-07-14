package org.yarokovisty.vpnis.data.vpn

/**
 * JNI bridge to the hev-socks5-tunnel native library (`libhev_tun2socks`).
 *
 * This object is an implementation detail of `:data:vpn`. It is `internal` and must not
 * be referenced from any other module.
 *
 * ## Threading contract
 *
 * [nativeStart] **BLOCKS** the calling thread for the entire lifetime of the tunnel. The
 * native side runs hev's event loop and does not return until either [nativeStop] is called
 * or the tunnel encounters a fatal error. Callers **must** invoke [nativeStart] on a
 * background thread or a coroutine launched on a non-main dispatcher (e.g.
 * `Dispatchers.IO`). Calling it on the main thread will ANR the app.
 *
 * ## File-descriptor ownership
 *
 * The TUN fd passed to [nativeStart] is created and owned by the `VpnService` (a future
 * issue). The fd must remain open and valid for the entire duration of the tunnel session.
 * The native library does **not** close the fd — the `VpnService` is responsible for
 * closing it after [nativeStart] returns.
 *
 * ## Library loading
 *
 * The shared library `libhev_tun2socks.so` is built from `src/main/jni/` via ndk-build
 * when the Gradle property `vpnis.buildNative=true` is set (issue #72). The `init` block
 * below loads it at class initialisation time; any `UnsatisfiedLinkError` indicates either
 * a missing native build or an ABI mismatch.
 */
internal object Tun2SocksBridge {

    init {
        System.loadLibrary("hev_tun2socks")
    }

    /**
     * Starts the hev-socks5-tunnel event loop.
     *
     * Parses [configYaml] (the output of [Tun2SocksConfig.toYaml]) and begins forwarding
     * traffic from the TUN interface identified by [tunFd] through the configured SOCKS5
     * proxy. This call **blocks** until the tunnel exits — either because [nativeStop] was
     * called, or because hev itself encountered an unrecoverable error.
     *
     * **Must be called on a background thread / non-main coroutine dispatcher.**
     *
     * @param configYaml YAML configuration string accepted by hev-socks5-tunnel
     *   (`hev_socks5_tunnel_main_from_str`). Use [Tun2SocksConfig.toYaml] to produce it.
     * @param tunFd File descriptor of the TUN interface opened by the `VpnService`. The fd
     *   is borrowed for the duration of the call; ownership stays with the caller.
     * @return hev's integer exit code. `0` indicates a clean shutdown (triggered by
     *   [nativeStop]); non-zero values indicate an error condition from the native layer.
     */
    external fun nativeStart(configYaml: String, tunFd: Int): Int

    /**
     * Signals the running tunnel to quit.
     *
     * Calls `hev_socks5_tunnel_quit()` which posts an asynchronous stop request into hev's
     * event loop. The loop will drain in-flight events before exiting, so [nativeStart] may
     * not return immediately. The caller should join the background thread / await the
     * coroutine to confirm the tunnel has fully stopped before closing the TUN fd.
     *
     * Safe to call from any thread, including the main thread.
     *
     * Calling [nativeStop] when no tunnel is running is a no-op at the native level.
     */
    external fun nativeStop()
}
