package org.yarokovisty.vpnis.data.vpn

import android.content.Context

/**
 * Source-set-specific factory for [XrayCore].
 *
 * This variant is active when `vpnis.buildNative=false` (the default). It returns a
 * [NoOpXrayCore] so that the tunnel flow works end-to-end (hev starts, TUN is established,
 * state-machine transitions and notifications work) without the real libXray AAR.
 *
 * The `buildNative` source set provides an identically named object in the same package
 * that returns [LibXrayCoreImpl] backed by [RealLibxrayApi]. Exactly one variant is active
 * per build — the source-set injection is wired in `data/vpn/build.gradle.kts` via
 * `androidComponents.onVariants { v -> v.sources.kotlin?.addSrcDir(...) }`.
 *
 * [XrayCoreProvider] is referenced **only** from [vpnModule] — do not import it anywhere else.
 */
internal object XrayCoreProvider {

    /**
     * Returns the appropriate [XrayCore] implementation for this source-set variant.
     *
     * @param context Application context. Used by the `buildNative` variant to derive
     *   `datDir` (`context.filesDir.path`). Unused by this default variant.
     */
    fun create(context: Context): XrayCore = NoOpXrayCore()
}
