plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        targetExclude("app/build/**")
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktfmt().kotlinlangStyle()
    }
    format("repositoryText") {
        target(
            ".editorconfig",
            ".github/**/*.xml",
            ".github/**/*.yml",
            "app/src/**/*.xml",
            "docs/**/*.md",
            "*.md",
            "*.properties",
            "scripts/**/*.sh",
            "scripts/**/*.py",
        )
        targetExclude("app/build/**", "build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register("resolveVerificationDependencies") {
    group = "verification"
    description = "Resolves every resolvable configuration so Gradle can record dependency checksums."

    doLast {
        allprojects.forEach { project ->
            project.configurations
                .filter { it.isCanBeResolved }
                .forEach { configuration ->
                    configuration.resolve()
                }
        }
    }
}
