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
// (local) Maven repository. This guard is opt-in (`-Pe2e`): it drags staging + signing
// into the graph, which must stay out of the default build. Run it explicitly
// (`./gradlew :e2e-published:test -Pe2e`) or via the nightly e2e workflow.
// The wiring below is gated as a whole: with the flag absent the suite is inert
// (SKIPPED, no staging scheduled); with it present the suite stages, signs as needed
// (release versions enforce signing — provide keys like any publish), and asserts.
// `providers` reads are CC-tracked; only plain values reach the task configuration.
val runE2eGuard = providers.gradleProperty("e2e").isPresent
tasks.withType<Test>().configureEach {
    // Plain-property assignment, not onlyIf: an onlyIf closure would capture the script
    // object and break configuration-cache storage. Disabled tasks are skipped while
    // their (here also gated) dependencies stay out of the graph.
    enabled = runE2eGuard
    if (runE2eGuard) {
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
