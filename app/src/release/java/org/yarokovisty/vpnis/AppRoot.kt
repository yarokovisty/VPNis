package org.yarokovisty.vpnis

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.yarokovisty.vpnis.design.theme.VPNisTheme

/**
 * Release entry point: the clean product surface.
 *
 * The design-system showcase lives in the `debug` source set only, so this
 * release variant intentionally renders nothing but the themed background until
 * the real product UI lands in a future screens epic.
 */
@Composable
fun AppRoot() {
    VPNisTheme {
        Surface(modifier = Modifier.fillMaxSize()) {}
    }
}
