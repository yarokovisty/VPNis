import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.util.Properties

plugins {
    id("vpnis.jvm.library")
    id("vpnis.koin")
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// ---------------------------------------------------------------------------
// Local-properties value source (config-cache-safe file read)
// ---------------------------------------------------------------------------

abstract class LocalPropertiesValueSource : ValueSource<String, LocalPropertiesValueSource.Params> {

    interface Params : ValueSourceParameters {
        val rootDir: DirectoryProperty
    }

    override fun obtain(): String? {
        val propsFile = parameters.rootDir.get().file("local.properties").asFile
        if (!propsFile.exists()) return null
        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        return props.getProperty("vpnis.defaultServerConfig")
    }
}

// ---------------------------------------------------------------------------
// Codegen task — writes the resolved config value into a namespaced resource
// ---------------------------------------------------------------------------

abstract class GenerateDefaultServerConfigTask : DefaultTask() {

    @get:Input
    abstract val configValue: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val target = outputDir.get()
            .file("org/yarokovisty/vpnis/data/server/default_server.config")
            .asFile
        target.parentFile.mkdirs()
        target.writeText(configValue.get(), Charsets.UTF_8)
    }
}

// ---------------------------------------------------------------------------
// Value resolution chain (gradle property → env var → local.properties → "")
// ---------------------------------------------------------------------------

val injectedDefaultServerConfig =
    providers.gradleProperty("vpnis.defaultServerConfig")
        .orElse(providers.environmentVariable("VPNIS_DEFAULT_SERVER_CONFIG"))
        // TODO(project-isolation): replace rootProject reference when project isolation is enabled
        .orElse(
            providers.of(LocalPropertiesValueSource::class) {
                parameters.rootDir = rootProject.layout.projectDirectory
            },
        )
        .orElse("")

// ---------------------------------------------------------------------------
// Task registration + resource wiring
// ---------------------------------------------------------------------------

val generateDefaultServerConfig =
    tasks.register<GenerateDefaultServerConfigTask>("generateDefaultServerConfig") {
        configValue = injectedDefaultServerConfig
        outputDir = layout.buildDirectory.dir("generated/vpnis/resources")
        outputs.cacheIf { false }
    }

sourceSets["main"].resources.srcDir(generateDefaultServerConfig.flatMap { it.outputDir })
