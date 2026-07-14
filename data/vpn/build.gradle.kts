plugins {
    id("vpnis.android.kotlin.library")
    id("vpnis.koin")
}

android {
    namespace = "org.yarokovisty.vpnis.data.vpn"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
