// Convention plugin for screen-level feature modules.
// Layers on top of vpnis.android.library.compose (which brings the Android Library
// plugin, Compose support, kotlin.plugin.compose, ktlint, detekt, and explicit API
// mode) and vpnis.koin (which wires in the Koin BOM + koin-core).
//
// Adds the standard feature-layer runtime dependencies: Navigation Compose,
// Lifecycle ViewModel + Runtime Compose, and the Koin Compose integration.
//
// Intentionally does NOT declare project(":core:domain") or any design module —
// those cross-module edges are wired in each feature module's own build.gradle.kts.

plugins {
    // Brings: com.android.library, kotlin.plugin.compose, ktlint, detekt, explicitApi.
    id("vpnis.android.library.compose")
    // Brings: Koin BOM + koin-core.
    id("vpnis.koin")
}

// Precompiled script plugins cannot use the generated `libs` accessor directly;
// the version catalog must be resolved via the extension API instead.
val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

dependencies {
    // Jetpack Navigation for Compose-based screen routing.
    implementation(libs.findLibrary("androidx-navigation-compose").get())
    // ViewModel integration for Compose — provides viewModel() and collectAsStateWithLifecycle().
    implementation(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    implementation(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
    // Koin Compose integration — provides koinViewModel() and other Compose-aware helpers.
    implementation(libs.findLibrary("koin-androidx-compose").get())
    // Material3 is standard across all feature screens.
    implementation(libs.findLibrary("androidx-compose-material3").get())
}
