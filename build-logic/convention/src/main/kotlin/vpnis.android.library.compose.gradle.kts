// Convention plugin for Android library modules that expose Compose UI.
// Layers on top of vpnis.android.library: enables Compose build feature,
// adds the Kotlin Compose compiler plugin, enforces explicit API mode,
// and pulls in the Compose BOM + minimal UI dependencies.
//
// material3 is intentionally omitted — declare it per-module.
//
// explicitApi() is enabled here so all public declarations in design/compose
// modules are required to have explicit visibility modifiers.

plugins {
    id("vpnis.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures {
        compose = true
    }
}

// Enforce explicit API mode: every public declaration must have an explicit
// visibility modifier and return type, making the module's API surface intentional.
kotlin {
    explicitApi()
}

// Precompiled script plugins cannot use the generated `libs` accessor directly;
// the version catalog must be resolved via the extension API instead.
val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

dependencies {
    // Import the Compose BOM so all Compose artifact versions are aligned.
    implementation(platform(libs.findLibrary("androidx-compose-bom").get()))
    // Minimal Compose UI surface — additional Compose libs are declared per-module.
    implementation(libs.findLibrary("androidx-compose-ui").get())
    implementation(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
}
