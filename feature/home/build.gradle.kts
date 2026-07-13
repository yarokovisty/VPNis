plugins {
    id("vpnis.android.feature")
}

android {
    namespace = "org.yarokovisty.vpnis.feature.home"
}

dependencies {
    implementation(project(":core:domain"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
