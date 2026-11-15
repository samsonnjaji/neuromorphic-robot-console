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

rootProject.name = "neuromorphic-robot-console"
include(":nrc-core", ":composeApp", ":androidApp", ":platform-api")
project(":nrc-core").projectDir = file("core/nrc-core")
project(":platform-api").projectDir = file("services/platform-api")
