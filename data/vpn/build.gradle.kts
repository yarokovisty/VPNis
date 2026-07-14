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

android {
    namespace = "org.yarokovisty.vpnis.data.vpn"
    testOptions {
        unitTests {
            isReturnDefaultValues = true
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
    // ServiceCompat.startForeground / stopForeground, NotificationCompat.Builder
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    // koin-android: provides androidContext() in Koin module DSL and KoinComponent
    // `by inject()` support inside VpnTunnelService (an Android Service, not a ViewModel).
    implementation(libs.koin.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
