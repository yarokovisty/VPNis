plugins {
    id("vpnis.jvm.library")
}

dependencies {
    // Pure formatting — no coroutines, no serialization, no Android, no :core:domain.
    // The formatter takes a primitive Long and returns a value + enum, so both :feature:home
    // and :data:vpn can share it without any inward-pointing dependency.
    testImplementation(libs.junit)
}
