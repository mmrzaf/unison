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
    description =
        "Resolves project dependencies so Gradle can record checksums, using AGP for Android-test classpaths."

    // Android test configurations are variant-aware AGP internals. Resolving them directly
    // selects no artifact type and is ambiguous; AGP resolves them correctly for this task.
    dependsOn(":app:compileDebugAndroidTestKotlin")

    doLast {
        allprojects.forEach { project ->
            project.configurations
                .filter { it.isCanBeResolved }
                .filterNot { configuration ->
                    configuration.name.contains("AndroidTest", ignoreCase = true)
                }
                .forEach { configuration ->
                    configuration.resolve()
                }
        }
    }
}
