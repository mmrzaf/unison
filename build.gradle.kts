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
        "Runs representative build tasks so Gradle can record checksums for their resolved dependencies."

    // Android Gradle Plugin test configurations are variant-aware internals. Directly resolving
    // them is ambiguous because no artifact type is requested. These real tasks resolve every
    // classpath through AGP and make --write-verification-metadata record the resulting artifacts.
    dependsOn(
        "spotlessCheck",
        ":app:assembleDebug",
        ":app:assembleRelease",
        ":app:testDebugUnitTest",
        ":app:lintDebug",
        ":app:lintRelease",
        ":app:compileDebugAndroidTestKotlin",
    )
}
