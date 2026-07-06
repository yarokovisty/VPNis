plugins {
    id("vpnis.android.library.compose")
}

android {
    namespace = "org.yarokovisty.vpnis.design.uikit"
}

dependencies {
    implementation(project(":design:theme"))
    api(libs.androidx.compose.material3)
}
