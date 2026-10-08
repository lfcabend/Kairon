// Own Gradle root (M11 D7) — deliberately NOT included from the repo's root
// settings.gradle.kts alongside :backend/:web. Keeps AGP/Android SDK out of
// the backend/web contributor toolchain entirely.
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

rootProject.name = "kairon-android"

include(":app", ":openapi-client")
