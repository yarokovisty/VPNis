import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    // Pin the bundled ktlint engine — deterministic across machines and CI.
    version = "1.5.0"
    android = true
    ignoreFailures = false
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.SARIF)
    }
}

// ktlint-gradle#936 (OPEN): KtLintFormatTask does not serialize under Gradle 9
// configuration cache. Opt this task type out; ktlintCheck is unaffected.
tasks.withType<KtLintFormatTask>().configureEach {
    notCompatibleWithConfigurationCache("ktlint-gradle#936")
}
