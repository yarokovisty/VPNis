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

dependencies {
    api(project(":core:domain"))
    // ServiceCompat.startForeground / stopForeground, NotificationCompat.Builder
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
