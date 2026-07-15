package org.yarokovisty.vpnis

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.yarokovisty.vpnis.data.server.serverModule
import org.yarokovisty.vpnis.feature.home.homeModule

class VpnisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            // Route Koin log output through Android Logcat.
            androidLogger()
            // Bind the application context so Koin can inject it into Android-aware modules.
            androidContext(this@VpnisApplication)
            // serverModule — real in-memory ServerRepository pre-seeded with the operator
            //   default server (FR-50, SRS §5.6, issue #56). Exactly one ServerRepository
            //   binding in the graph.
            // VpnBindings.module — the ConnectionController backend, selected per build
            //   variant (issue #66): fakeVpnModule in default builds, the real vpnModule
            //   (:data:vpn) when -Pvpnis.buildNative=true. VpnBindings is the only seam that
            //   flips the swap — no consumer (feature:home, etc.) is touched.
            // homeModule — HomeViewModel and its use-case dependencies.
            modules(homeModule, serverModule, VpnBindings.module)
        }
    }
}
