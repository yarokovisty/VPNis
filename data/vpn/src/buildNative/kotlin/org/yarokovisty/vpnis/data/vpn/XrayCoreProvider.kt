package org.yarokovisty.vpnis.data.vpn

import android.content.Context

/**
 * Source-set-specific factory for [XrayCore].
 *
 * This variant is active when `vpnis.buildNative=true`. It returns a [LibXrayCoreImpl]
 * backed by [RealLibxrayApi] — the thin gomobile wrapper that is the **only** class
 * in the project that references the `XrayCore.aar` directly.
 *
 * The `default` source set provides an identically named object in the same package
 * that returns [NoOpXrayCore]. Exactly one variant is active per build — the source-set
 * injection is wired in `data/vpn/build.gradle.kts` via
 * `androidComponents.onVariants { v -> v.sources.kotlin?.addSrcDir(...) }`.
 *
 * [XrayCoreProvider] is referenced **only** from [vpnModule] — do not import it anywhere else.
 */
internal object XrayCoreProvider {

    /**
     * Returns a [LibXrayCoreImpl] backed by [RealLibxrayApi] using
     * `context.filesDir.path` as the `datDir` for Xray asset files
     * (`geoip.dat`, `geosite.dat`).
     *
     * @param context Application context. `filesDir` is used as `datDir` for the Xray
     *   runtime — asset files must be extracted there before [XrayCore.start] is called.
     */
    fun create(context: Context): XrayCore =
        LibXrayCoreImpl(api = RealLibxrayApi(), datDir = context.filesDir.path)
}
