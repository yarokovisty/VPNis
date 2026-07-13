// Convention plugin for Android library modules that use Kotlin but do NOT
// expose Compose UI. Layers kotlin-android on top of vpnis.android.library
// (which already brings the Android Library plugin, ktlint, and detekt).
//
// Use this plugin for modules such as :data:vpn that need full Android SDK
// access and Kotlin but have no Compose dependency.
//
// For modules that additionally use Compose, use vpnis.android.library.compose
// instead — it brings kotlin-android transitively via kotlin.plugin.compose.
//
// Note: org.jetbrains.kotlin.android is applied via apply(plugin=...) rather than
// the plugins{} block to avoid the "extension 'kotlin' already registered" conflict
// that arises during Gradle's precompiled-script accessor-generation phase when
// another Kotlin plugin (compose) has already registered the extension.

plugins {
    id("vpnis.android.library")
}

apply(plugin = "org.jetbrains.kotlin.android")
