package org.yarokovisty.vpnis

import androidx.compose.runtime.Composable

/**
 * Release entry point: delegates to [VpnisApp] which owns the full nav scaffold,
 * theme, and NavHost. VPNisTheme is applied inside VpnisApp — do not wrap it again.
 */
@Composable
fun AppRoot() = VpnisApp()
