plugins {
    `kotlin-dsl`
}

group = "org.yarokovisty.vpnis.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Resolve the ktlint-gradle plugin marker from the version catalog.
    // Assumes the `ktlint` plugin entry stays a plain required version string;
    // `strictly`/`prefer`/`reject` constraints would make requiredVersion empty.
    implementation(
        libs.plugins.ktlint.map { pluginDep ->
            "${pluginDep.pluginId}:${pluginDep.pluginId}.gradle.plugin:${pluginDep.version.requiredVersion}"
        }
    )
}
