plugins {
    id("vpnis.jvm.library")
    id("vpnis.koin")
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
}
