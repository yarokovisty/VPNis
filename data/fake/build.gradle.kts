plugins {
    id("vpnis.jvm.library")
    id("vpnis.koin")
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // koin-test-junit4 for the fakeVpnModule checkModules graph test (issue #127 T-8a).
    testImplementation(libs.koin.test.junit4)
}
