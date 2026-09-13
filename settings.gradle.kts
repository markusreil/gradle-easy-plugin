pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
        maven { url = uri("https://repo.mreil.com/gradle-plugins-snapshots") }
    }
}

plugins { id("com.mreil.easy.settings") version "0.0.107" }

easy {
    publish {
        toMavenStaging()
        toSonatypeSnapshots()
        mavenRepo(
            "mreilComGradlePluginsSnapshots",
            "https://repo.mreil.com/gradle-plugins-snapshots",
            true
        )
    }
}

rootProject.name = "gradle-easy-plugin"
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
include("contributor-plugins:jvm-defaults:jvm-defaults-plugin-api")
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
include("contributor-plugins:vcs:vcs-plugin-api")
include("contributor-plugins:vcs:vcs-plugin")
include("contributor-plugins:vcs:vcs-test-plugin")
include("contributor-plugins:release:release-plugin-api")
include("contributor-plugins:release:release-plugin")
include("contributor-plugins:release:release-test-plugin")
