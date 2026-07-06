package org.yarokovisty.vpnis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // AppRoot has a build-variant-specific implementation:
            //   src/debug   -> the design-system showcase gallery
            //   src/release -> the clean product screen
            // The showcase code is therefore compiled ONLY into debug builds and
            // is physically absent from the release APK.
            AppRoot()
        }
    }
}
