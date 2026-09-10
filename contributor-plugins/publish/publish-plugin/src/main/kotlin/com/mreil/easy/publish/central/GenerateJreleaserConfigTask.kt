package com.mreil.easy.publish.central

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Generates a JReleaser YAML config for Maven Central deployment.
 *
 * Registered only on the root project when [DefaultEasyPublishExtension.toMavenCentral]
 * is set (see [EasyJreleaserPlugin] for the gating rule).
 * Holds the lazy `@Input` properties and delegates rendering to [MavenCentralWiring.buildYaml].
 * See [MavenCentralWiring.Config] for the resolved values (including the test-only nexus
 * escape hatch that swaps in a `nexus3/local-test` deployer and demotes `mavenCentral`
 * to `NEVER` so smoke runs can never touch real Central).
 */
@CacheableTask
abstract class GenerateJreleaserConfigTask : DefaultTask() {
    @get:Input
    abstract val stagingDirs: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val mavenCentralUsername: Property<String>

    @get:Input
    @get:Optional
    abstract val mavenCentralPassword: Property<String>

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val projectGroupId: Property<String>

    @get:Input
    @get:Optional
    abstract val nexusUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val nexusUsername: Property<String>

    @get:Input
    @get:Optional
    abstract val nexusPassword: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "publishing"
        description = "Generates JReleaser config for Maven Central (staging at stagingDirs)"
    }

    @TaskAction
    // UseOrEmpty does not apply: Gradle Property has no orEmpty(), orNull ?: "" is the idiom.
    @Suppress("UseOrEmpty")
    fun generate() {
        // Fail fast with actionable messages instead of Gradle's generic missing-value
        // error. Conventions must stay absent-able: a throwing provider (e.g. via
        // orElse) would detonate at configuration-cache store time, where TestKit-style
        // -D flags are not yet visible to not-yet-realized providers.
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            JreleaserYaml.buildYaml(
                JreleaserYaml.Config(
                    projectName = projectName.get(),
                    projectVersion = projectVersion.get(),
                    projectGroupId = projectGroupId.get(),
                    stagingDirs = stagingDirs.get(),
                    mavenCentralUsername =
                        mavenCentralUsername.required("Maven Central username is required"),
                    mavenCentralPassword =
                        mavenCentralPassword.required("Maven Central password is required"),
                    nexusUrl = nexusUrl.orNull,
                    nexusUsername = nexusUsername.orNull ?: "",
                    nexusPassword = nexusPassword.orNull ?: "",
                ),
            ),
        )
    }

    private fun Property<String>.required(message: String): String = orNull ?: throw GradleException(message)
}
