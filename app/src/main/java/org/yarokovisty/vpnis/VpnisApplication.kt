package org.yarokovisty.vpnis

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.yarokovisty.vpnis.data.fake.fakeVpnModule
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
            // fakeVpnModule — fake ConnectionController only (ServerRepository removed from
            //   it in #56). Swap for :data:vpn in epic B (#66).
            // homeModule — HomeViewModel and its use-case dependencies.
            modules(homeModule, serverModule, fakeVpnModule)
        }
    }
}
