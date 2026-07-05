plugins {
    id("vpnis.android.library.compose")
}

android {
    namespace = "org.yarokovisty.vpnis.design.theme"
}

dependencies {
    implementation(libs.androidx.compose.material3)
}
