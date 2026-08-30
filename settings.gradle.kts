pluginManagement {
    val runningInGithubActions = System.getenv("GITHUB_ACTIONS") == "true"
    val useIranMirrors =
        !runningInGithubActions &&
            (providers.gradleProperty("useIranMirrors").orNull == "true" ||
                System.getenv("USE_IRAN_MIRRORS") == "true")

    repositories {
        if (useIranMirrors) {
            maven { url = uri("https://maven.myket.ir") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
        }

        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // AGP is published by Google's Maven repository as com.android.tools.build:gradle.  Resolve
    // the application plugin directly instead of relying on a separate marker artifact, so the
    // official Google repository remains the single source for this plugin.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
            if (requested.id.id == "com.diffplug.spotless") {
                useModule("com.diffplug.spotless:spotless-plugin-gradle:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    val runningInGithubActions = System.getenv("GITHUB_ACTIONS") == "true"
    val useIranMirrors =
        !runningInGithubActions &&
            (providers.gradleProperty("useIranMirrors").orNull == "true" ||
                System.getenv("USE_IRAN_MIRRORS") == "true")

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useIranMirrors) {
            maven { url = uri("https://maven.myket.ir") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
        }

        google()
        mavenCentral()
    }
}

rootProject.name = "Unison"

include(":app")
