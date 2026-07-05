import dev.detekt.gradle.Detekt

// Convention plugin for static analysis via detekt.
// Applies the dev.detekt plugin, points it at the shared config, and enables
// SARIF reporting on every Detekt task variant (e.g. detektDebug on Android modules).
// detektGenerateConfig can be used to regenerate config/detekt/detekt.yml from defaults.

plugins {
    id("dev.detekt")
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    ignoreFailures = false
    parallel = true
}

tasks.withType<Detekt>().configureEach {
    reports {
        sarif.required = true
    }
}
