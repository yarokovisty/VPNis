import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion

/**
 * Shared Android configuration applied to every Android module (library and application).
 * Called from convention plugins so the settings stay in one place.
 *
 * compileSdk is set via the plain Int property rather than the `compileSdk { version = release(36) { } }`
 * DSL available in ApplicationExtension: the release{} factory is not part of CommonExtension's
 * public API surface, so the Int property is the portable form that compiles on CommonExtension.
 *
 * Note: AGP 9.x dropped the type parameters from CommonExtension — it is now a plain
 * (non-generic) interface, so the helper takes `CommonExtension` without star-projections.
 * The `defaultConfig { }` / `compileOptions { }` block methods live on the concrete
 * Library/Application extensions, not on CommonExtension, so we go through the getters
 * (`defaultConfig` / `compileOptions`) which are portable across every module type.
 */
fun configureAndroidCommon(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = 36
        defaultConfig.minSdk = 26
        compileOptions.sourceCompatibility = JavaVersion.VERSION_11
        compileOptions.targetCompatibility = JavaVersion.VERSION_11
    }
}
