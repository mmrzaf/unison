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
