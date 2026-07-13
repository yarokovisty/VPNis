package org.yarokovisty.vpnis

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.yarokovisty.vpnis.data.fake.fakeVpnModule
import org.yarokovisty.vpnis.feature.home.homeModule

class VpnisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            // Route Koin log output through Android Logcat.
            androidLogger()
            // Bind the application context so Koin can inject it into Android-aware modules.
            androidContext(this@VpnisApplication)
            // homeModule provides HomeViewModel; fakeVpnModule provides ConnectionController +
            // ServerRepository backed by configurable fakes. Swap fakeVpnModule for the real
            // :data:vpn module in epic B (#66) without touching any other module.
            modules(homeModule, fakeVpnModule)
        }
    }
}
