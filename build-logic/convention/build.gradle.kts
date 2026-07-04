plugins {
    `kotlin-dsl`
}

group = "org.yarokovisty.vpnis.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // The Kotlin Gradle plugin must be on the classpath so that detekt 2.x (which references
    // KotlinBasePlugin) can be applied during precompiled script plugin accessor generation.
    implementation(libs.kotlin.gradle.plugin)
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
