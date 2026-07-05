import com.android.build.api.dsl.LibraryExtension

// Convention plugin for all Android library modules.
// Applies the Android Library plugin, delegates shared compile settings to
// configureAndroidCommon(), then layers on lint and static-analysis gates.
//
// Note: kotlin-android is intentionally NOT applied here. Modules that use Kotlin
// should apply it directly, or layer vpnis.android.library.compose on top (which
// pulls in Kotlin support transitively via org.jetbrains.kotlin.plugin.compose).

plugins {
    id("com.android.library")
    id("vpnis.ktlint")
    id("vpnis.detekt")
}

// Apply shared compileSdk, minSdk, and Java 11 compileOptions.
configureAndroidCommon(the<LibraryExtension>())
