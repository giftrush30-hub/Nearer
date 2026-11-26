pluginManagement {
    repositories {
        google()
        // Google-hosted Maven Central mirror reduces shared-runner throttling; keep
        // Maven Central as the canonical fallback if an artifact is not mirrored.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
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

rootProject.name = "NeaRer"
include(":app")
