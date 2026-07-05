plugins {
    `kotlin-dsl`
}

group = "org.yarokovisty.vpnis.buildlogic"

// Pin build-logic to Java 17 so the compiled bytecode can be loaded by the
// JDK 17 runtime on CI. Foojay resolver (wired in settings.gradle.kts) will
// download the toolchain on first use if not already cached.
// The old `java { sourceCompatibility/targetCompatibility = VERSION_21 }` block
// was removed — jvmToolchain() sets both source and target in one place and is
// the idiomatic choice for a kotlin-dsl included build.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // The Kotlin Gradle plugin must be on the classpath so that detekt 2.x (which references
    // KotlinBasePlugin) can be applied during precompiled script plugin accessor generation.
    // Also provides kotlin-android and all other Kotlin Gradle plugins (no separate marker needed).
    implementation(libs.kotlin.gradle.plugin)

    // Resolve the AGP plugin marker from the version catalog.
    // The marker artifact (com.android.library:com.android.library.gradle.plugin) pulls the full
    // AGP JAR transitively, making CommonExtension / LibraryExtension available in helpers.
    // Note: multi-segment aliases use the hierarchical dot accessor (android.library), not camelCase.
    implementation(
        libs.plugins.android.library.map { pluginDep ->
            "${pluginDep.pluginId}:${pluginDep.pluginId}.gradle.plugin:${pluginDep.version.requiredVersion}"
        }
    )

    // Resolve the Kotlin Compose compiler plugin marker from the version catalog.
    // Needed so that accessor generation for precompiled plugins that apply kotlin-compose succeeds.
    implementation(
        libs.plugins.kotlin.compose.map { pluginDep ->
            "${pluginDep.pluginId}:${pluginDep.pluginId}.gradle.plugin:${pluginDep.version.requiredVersion}"
        }
    )

    // Resolve the ktlint-gradle plugin marker from the version catalog.
    // Assumes the `ktlint` plugin entry stays a plain required version string;
    // `strictly`/`prefer`/`reject` constraints would make requiredVersion empty.
    implementation(
        libs.plugins.ktlint.map { pluginDep ->
            "${pluginDep.pluginId}:${pluginDep.pluginId}.gradle.plugin:${pluginDep.version.requiredVersion}"
        }
    )

    // Resolve the detekt-gradle plugin marker from the version catalog.
    implementation(
        libs.plugins.detekt.map { pluginDep ->
            "${pluginDep.pluginId}:${pluginDep.pluginId}.gradle.plugin:${pluginDep.version.requiredVersion}"
        }
    )
}
