pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application" || requested.id.id == "com.android.library") {
                useModule("com.android.tools.build:gradle:8.5.0")
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

rootProject.name = "Scanium"
include(
    ":androidApp",
    // Legacy Android wrapper modules retained for compatibility; replace with shared modules once dependencies are updated.
    ":core-models",
    ":core-tracking",
    ":core-domainpack",
    ":core-scan",
    ":core-contracts",
    ":android-ml-mlkit",
    ":android-camera-camerax",
    ":android-platform-adapters",
    ":shared:core-models",
    ":shared:core-tracking",
    ":shared:test-utils",
    ":shared:telemetry-contract",
    ":shared:telemetry",
)

// Map shared modules to their directories under /shared
project(":shared:core-models").projectDir = file("shared/core-models")
project(":shared:core-tracking").projectDir = file("shared/core-tracking")
project(":shared:test-utils").projectDir = file("shared/test-utils")
project(":shared:telemetry-contract").projectDir = file("shared/telemetry-contract")
project(":shared:telemetry").projectDir = file("shared/telemetry")
