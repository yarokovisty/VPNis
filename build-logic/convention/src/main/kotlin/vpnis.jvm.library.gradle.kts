// Convention plugin for pure-JVM library modules (e.g. :core:domain).
// Applies the Kotlin JVM plugin plus linting gates, sets Java 11 source/target
// compatibility to match the Android modules, and enforces explicit API mode so
// every public symbol requires an explicit visibility modifier and return type.
//
// No Android or Compose plugins are applied here — this plugin is intentionally
// free of any Android build-tool dependency.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("vpnis.ktlint")
    id("vpnis.detekt")
}

java {
    // Mirror the Java 11 level used in configureAndroidCommon() so compiled
    // bytecode is compatible with both JVM and Android runtimes.
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// Enforce explicit API mode: every public declaration must carry an explicit
// visibility modifier and return type, keeping the module's public surface intentional.
// Set jvmTarget to 11 to match the Java source/target above and avoid the
// "Inconsistent JVM Target Compatibility" error from Gradle's consistency check.
kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}
