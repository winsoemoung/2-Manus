import org.gradle.api.initialization.resolve.RepositoriesMode
pluginManagement {
    repositories {
        google() // AGP, androidx.* Gradle plugin (Room plugin အပါ)
        mavenCentral() // KSP နှင် Kotlin tooling
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
rootProject.name = "OfflinePOS"
include(":app")