pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://repo.mreil.com/gradle-plugins-snapshots")
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://repo.mreil.com/gradle-plugins-snapshots")
        }
    }
}

rootProject.name = "central-smoke"
