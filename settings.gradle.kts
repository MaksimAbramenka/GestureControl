pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GestureControl"

include(":app")
include(":domain")
include(":core-camera")
include(":core-ml")
include(":core-ml:gesture-classifier")
include(":core-engine")
include(":core-engine-ios")
include(":core-ui")
include(":core-voice")
