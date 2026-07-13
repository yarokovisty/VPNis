// Convention plugin that layers Koin DI onto a module.
// Applies no language or platform plugin itself — it is designed to be composed
// on top of a plugin that already configures Kotlin (e.g. vpnis.jvm.library,
// vpnis.android.kotlin.library, or vpnis.android.library.compose).
//
// All Koin artifact versions are resolved transitively through the BOM, so no
// explicit version string is needed on individual koin-* coordinates.
//
// Note: dependencies are added via `project.dependencies.add(configurationName, ...)`
// rather than the typed `implementation(...)` shorthand. The typed shorthand requires
// a Kotlin or Java plugin to be present in this script's plugins{} block at compile
// time, which would conflict with the Android or Kotlin plugin already applied by
// the composing plugin. The string-based API is always available on `Project`.

// Precompiled script plugins cannot use the generated `libs` accessor directly;
// the version catalog must be resolved via the extension API instead.
val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// Import the Koin BOM so all Koin artifact versions are aligned.
project.dependencies.add("implementation", project.dependencies.platform(libs.findLibrary("koin-bom").get()))
// koin-core is the base DI container — required by every Koin-aware module.
project.dependencies.add("implementation", libs.findLibrary("koin-core").get())
