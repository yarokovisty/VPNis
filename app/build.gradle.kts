plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("vpnis.ktlint")
    id("vpnis.detekt")
}

// Gate the real-tunnel wiring behind the same opt-in property used by :data:vpn.
// Default (false): the app depends on :data:fake and loads fakeVpnModule, so
// `./gradlew :app:assembleDebug` needs no NDK/AAR and ships the fake controller
// (F-Droid / default channel). Native (`-Pvpnis.buildNative=true`): the app depends
// on :data:vpn and loads the real vpnModule — the production ConnectionControllerImpl
// swap (issue #66). Mirrors data/vpn/build.gradle.kts and XrayCoreProvider.
val buildNative = providers.gradleProperty("vpnis.buildNative")
    .map { it.toBoolean() }
    .getOrElse(false)

// Inject exactly one VpnBindings source dir per variant. The active variant's object
// exposes VpnBindings.module (fakeVpnModule vs vpnModule), which VpnisApplication loads
// without a compile-time reference to either data module. Lazy onVariants registration
// (not a config-time srcDir call) keeps the task graph stable when the flag flips —
// same rationale and AGP 9 API (addStaticSourceDirectory) as :data:vpn.
androidComponents.onVariants { v ->
    v.sources.kotlin?.addStaticSourceDirectory(
        if (buildNative) "src/buildNative/kotlin" else "src/default/kotlin",
    )
}

android {
    namespace = "org.yarokovisty.vpnis"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "org.yarokovisty.vpnis"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Koin — BOM aligns all koin-* artifact versions; koin-android provides startKoin + androidContext.
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    // Variant-aware VPN backend (issue #66). Default builds bind the fake controller;
    // native builds (-Pvpnis.buildNative=true) bind the real :data:vpn tunnel and merge
    // its <service> + VPN permissions. VpnBindings (source-set-selected) picks the module.
    if (buildNative) {
        implementation(project(":data:vpn"))
    } else {
        implementation(project(":data:fake"))
    }
    implementation(project(":data:server"))
    implementation(project(":design:theme"))
    implementation(project(":design:uikit"))
    implementation(project(":feature:home"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
