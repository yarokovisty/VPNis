plugins {
    id("vpnis.android.kotlin.library")
    id("vpnis.koin")
}

// Gate all native-build wiring behind an opt-in Gradle property.
// Default is false so that `./gradlew assembleDebug` succeeds with no NDK installed.
// Issue #72 will enable this on the native-build CI agent via -Pvpnis.buildNative=true.
val buildNative = providers.gradleProperty("vpnis.buildNative")
    .map { it.toBoolean() }
    .getOrElse(false)

// T-4: Inject exactly one XrayCoreProvider source dir per variant using the
// provider-safe AGP 9 API. At configuration time `buildNative` is already a plain
// Boolean (resolved via getOrElse above), so the lambda simply captures it.
// Using onVariants (not a config-time sourceSets.main.kotlin.srcDir call) keeps
// the registration lazy and avoids stale source roots when the flag flips between
// builds — the configuration cache sees a stable task graph regardless.
//
// AGP 9.x API: Sources.getKotlin() returns SourceDirectories.Flat (non-nullable);
// the correct registration method is addStaticSourceDirectory(String) — NOT the
// non-existent addSrcDir(). "Static" means the directory is pre-existing on disk
// (as opposed to addGeneratedSourceDirectory for task-produced directories).
androidComponents.onVariants { v ->
    v.sources.kotlin?.addStaticSourceDirectory(
        if (buildNative) "src/buildNative/kotlin" else "src/default/kotlin",
    )
}

android {
    namespace = "org.yarokovisty.vpnis.data.vpn"
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Robolectric needs the merged resources to resolve R.string/R.drawable in the
            // notification tests (issue #127: createChannel/build call getString).
            isIncludeAndroidResources = true
        }
    }

    if (buildNative) {
        defaultConfig {
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
            externalNativeBuild {
                ndkBuild {
                    arguments("APP_APPLICATION_MK=src/main/jni/Application.mk")
                }
            }
        }
        externalNativeBuild {
            ndkBuild {
                path = file("src/main/jni/Android.mk")
            }
        }
    }
}

// XrayCore.aar (gomobile bind of the libXray submodule) is built by
// `buildXrayCore.sh`, invoked directly by the native-build CI job (#72) — not
// through Gradle. libXray's own module graph lacks golang.org/x/mobile, so the
// bind must run from a throwaway wrapper module (mirroring SaeedDev94/Xray's
// buildXrayCore.sh); that is shell territory, kept out of the AGP build. The AAR
// is not consumed as a dependency yet — the real LibXrayCoreImpl swap is #66.
// The hev .so IS built by AGP ndkBuild above when -Pvpnis.buildNative=true.

dependencies {
    api(project(":core:domain"))
    // Shared bitrate formatter (issues #69/#130) — same unit rounding/copy as the Home tiles.
    // Unconditional (used from src/main by TunnelNotifications, runs on both flavors).
    implementation(project(":core:format"))
    // ServiceCompat.startForeground / stopForeground, NotificationCompat.Builder
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    // kotlinx.serialization element API: buildJsonObject, parseToJsonElement, JsonPrimitive.
    // Used by XrayConfigBuilder (URI → Xray JSON) and LibXrayCoreImpl (CallResponse decode).
    // NOTE added for T-1/T-3 compilation — serialization compiler plugin is NOT applied;
    // only the runtime (element API) is used.
    implementation(libs.kotlinx.serialization.json)
    // koin-android: provides androidContext() in Koin module DSL and KoinComponent
    // `by inject()` support inside VpnTunnelService (an Android Service, not a ViewModel).
    implementation(libs.koin.android)
    // T-5: XrayCore.aar (gomobile bind of libXray) — present only when -Pvpnis.buildNative=true.
    // Consumed via a coordinate (NOT files()) so AGP's AAR transform runs and extracts the
    // gomobile .so libs. The flatDir repo that makes ":XrayCore@aar" resolvable is declared
    // in settings.gradle.kts (FAIL_ON_PROJECT_REPOS prevents a repo here even inside an if).
    if (buildNative) {
        implementation(":XrayCore@aar")
    }
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // koin-test-junit4 brings koin-test transitively + the JUnit-4 runner support used by the
    // checkModules graph tests; robolectric supplies an application Context for the real-graph check
    // (both cataloged; robolectric already used by :feature:home tests — issue #127 T-8a).
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.robolectric)
}
