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

rootProject.name = "NikoDialer"
include(":app")

// Shared Niko code, a git submodule (Hub ADR 0032).
include(":commons")
project(":commons").projectDir = file("commons/commons")
