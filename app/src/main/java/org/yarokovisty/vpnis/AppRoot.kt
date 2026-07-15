package org.yarokovisty.vpnis

import androidx.compose.runtime.Composable

/**
 * App entry point: delegates to [VpnisApp] which owns the full nav scaffold,
 * theme, and NavHost. VPNisTheme is applied inside VpnisApp — do not wrap it again.
 *
 * Shared by all build variants: debug and release render the same product UI.
 */
@Composable
fun AppRoot() = VpnisApp()
