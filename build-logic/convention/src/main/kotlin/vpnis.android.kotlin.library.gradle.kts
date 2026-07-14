// Convention plugin for Android library modules that use Kotlin but do NOT
// expose Compose UI. Layers on top of vpnis.android.library (which already brings
// the Android Library plugin, ktlint, and detekt).
//
// Use this plugin for modules such as :data:vpn that need full Android SDK
// access and Kotlin but have no Compose dependency.
//
// For modules that additionally use Compose, use vpnis.android.library.compose
// instead — it enables the Compose build feature and the Kotlin Compose compiler.
//
// Note: Kotlin support is NOT applied here. AGP 9.x (com.android.library) provides
// built-in Kotlin — it registers the `kotlin` extension itself, so applying
// org.jetbrains.kotlin.android on top fails with "extension 'kotlin' already
// registered". The `kotlin { }` DSL and .kt compilation are available from the
// Android Library plugin alone (this is why vpnis.android.library.compose only
// adds the kotlin.plugin.compose compiler add-on, not the base kotlin plugin).

plugins {
    id("vpnis.android.library")
}
