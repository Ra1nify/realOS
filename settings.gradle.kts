pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.itoseo.coui-kit") }
        }
    }
}

rootProject.name = "realOS"
include(":app")
include(":libs:dexkit")
project(":libs:dexkit").projectDir = file("libs/dexkit")

// Use dependencySubstitution to map published coordinates onto local projects, so publishToMavenLocal is not needed.
