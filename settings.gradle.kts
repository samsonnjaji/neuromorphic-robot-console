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
include(":nrc-core", ":composeApp", ":androidApp")
project(":nrc-core").projectDir = file("core/nrc-core")
