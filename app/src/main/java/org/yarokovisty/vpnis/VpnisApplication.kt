package org.yarokovisty.vpnis

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class VpnisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            // Route Koin log output through Android Logcat.
            androidLogger()
            // Bind the application context so Koin can inject it into Android-aware modules.
            androidContext(this@VpnisApplication)
            // Feature and domain modules register their own Koin modules; they are collected
            // and wired here in #50 (:feature:home) and #53 (:core:domain).
            modules(emptyList())
        }
    }
}
