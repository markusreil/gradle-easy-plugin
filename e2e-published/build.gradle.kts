plugins {
    `java-library`
}

repositories { mavenCentral() }

// Published-consumer guard for the two-jar split (see TWO_JAR_SPLIT.md step 5). TestKit's
// `withPluginClasspath()` flattens the plugin classpath, so the settings/project classloader
// boundary can only be reproduced by resolving the two staged marker artifacts from a real
// (local) Maven repository. Staging is credential-free in this repo (signing is not configured),
// so the test can depend on the publish tasks directly.
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
