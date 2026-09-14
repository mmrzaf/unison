pluginManagement {
    repositories {
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Unison"

include(":app")
