pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://repo.mreil.com/gradle-plugins-snapshots") }
    }
}

plugins { id("com.mreil.easy.settings") version "0.0.100" }

easy {
    publish {
        enabled.set(true)
        toMavenStaging()
        toMavenCentral()
        mavenRepo(
            "mreilComGradlePluginsSnapshots",
            "https://repo.mreil.com/gradle-plugins-snapshots",
            true
        )
    }
}

rootProject.name = "gradle-easy-plugin-new"
include("easy-plugin")
include("easy-plugin-core")
include("easy-contributor-api")
include("easy-contributor-support")
include("easy-test-support")
include("gradle-plugin-testutils")
include("gradle-plugin-utils")
include("contributor-plugins:publish:publish-plugin-api")
include("contributor-plugins:publish:publish-plugin")
include("contributor-plugins:publish:publish-test-plugin")
include("contributor-plugins:jvm-defaults:jvm-defaults-plugin")
include("contributor-plugins:jvm-defaults:jvm-defaults-test-plugin")
include("contributor-plugins:semver:semver-plugin-api")
include("contributor-plugins:semver:semver-plugin")
include("contributor-plugins:semver:semver-test-plugin")
include("contributor-plugins:codemeta:codemeta-plugin-api")
include("contributor-plugins:codemeta:codemeta-plugin")
include("contributor-plugins:codemeta:codemeta-test-plugin")
include("contributor-plugins:project-defaults:project-defaults-plugin-api")
include("contributor-plugins:project-defaults:project-defaults-plugin")
include("contributor-plugins:project-defaults:project-defaults-test-plugin")
