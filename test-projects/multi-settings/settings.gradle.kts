pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://repo.mreil.com/gradle-plugins-snapshots")
        }
        mavenCentral()
    }
}

plugins {
    id("com.mreil.easy.settings") version "latest.integration"
}

easy {
    publish {
        enabled.set(true)
        toMavenLocal()
        toMavenStaging()
    }
    semver {}
    codemeta {}
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://repo.mreil.com/gradle-plugins-snapshots")
        }
    }
}


rootProject.name = "multi-settings"
include(":java-lib")

