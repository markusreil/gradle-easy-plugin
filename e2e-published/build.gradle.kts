plugins {
    `java-library`
}

// Test-only published-consumer guard: must not ship to Central (see TODO.md).
easy {
    publish.enabled = false
}

repositories { mavenCentral() }

// Published-consumer guard for the two-jar split (see TWO_JAR_SPLIT.md step 5). TestKit's
// `withPluginClasspath()` flattens the plugin classpath, so the settings/project classloader
// boundary can only be reproduced by resolving the two staged marker artifacts from a real
// (local) Maven repository. Staging signs on release versions (signing auto-enabled);
// the test only needs the files present, signed or not, so it can depend on the
// publish tasks directly. Test-task env scrubbing (see root build.gradle.kts) keeps
// CI secrets out of these fixtures either way.
tasks.withType<Test>().configureEach {
    dependsOn(
        ":easy-plugin:publishAllPublicationsToMavenStagingRepository",
        ":easy-plugin-settings:publishAllPublicationsToMavenStagingRepository",
    )
    systemProperty("e2e.pluginVersion", version.toString())
    systemProperty(
        "e2e.projectRepo",
        rootProject.layout.projectDirectory
            .dir("easy-plugin/build/stagingRepo")
            .asFile.absolutePath,
    )
    systemProperty(
        "e2e.settingsRepo",
        rootProject.layout.projectDirectory
            .dir("easy-plugin-settings/build/stagingRepo")
            .asFile.absolutePath,
    )
    systemProperty("e2e.kgpVersion", libs.versions.kotlin.get())
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(gradleTestKit())
            }
        }
    }
}
