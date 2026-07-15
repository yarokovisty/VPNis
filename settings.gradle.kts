pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // T-5: XrayCore.aar flat repo — only added when -Pvpnis.buildNative=true.
        // MUST live here (not in data/vpn/build.gradle.kts): the project sets
        // RepositoriesMode.FAIL_ON_PROJECT_REPOS above, which rejects any
        // project-level repositories{} block in a module build file, even inside an if.
        // The coordinate implementation(":XrayCore@aar") in data/vpn/build.gradle.kts
        // resolves against this repo only when both the flag and the repo are active.
        // Default builds (buildNative=false) get neither the repo nor the dependency —
        // assembleDebug without the flag is always safe with no NDK or AAR present.
        if (providers.gradleProperty("vpnis.buildNative").map { it.toBoolean() }.getOrElse(false)) {
            flatDir { dirs(rootDir.resolve("data/vpn/libs").path) }
        }
    }
}

rootProject.name = "VPNis"
include(":app")
include(":core:domain")
include(":data:fake")
include(":data:server")
include(":data:vpn")
include(":design:theme")
include(":design:uikit")
include(":feature:home")
