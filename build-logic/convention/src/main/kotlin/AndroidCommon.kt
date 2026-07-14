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
        // Single project-wide NDK pin (spike #59 / ADR 0001). The value matches the adopted
        // SaeedDev94/Xray v12.3.0 build recipe and must stay identical across the gomobile AAR
        // build (#60) and the native CI job (#72) — divergent NDK versions produce
        // incompatible ABIs. Setting the property here is inert for modules without native
        // code (AGP only resolves the NDK when a module actually has native build inputs);
        // it takes effect once :data:vpn adds the Xray-core .aar / hev .so.
        ndkVersion = "29.0.14206865"
    }
}
