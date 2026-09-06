package com.mreil.easy.publish

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Generates a JReleaser YAML config for Maven Central deployment.
 *
 * Guarded by [DefaultEasyPublishExtension.toMavenCentral]; registered only on the root project.
 * Holds the lazy `@Input` properties and delegates rendering to [MavenCentralWiring.buildYaml].
 * See [MavenCentralWiring.Config] for the resolved values (including the test-only nexus
 * escape hatch that swaps in a `nexus3/local-test` deployer and demotes `mavenCentral`
 * to `NEVER` so smoke runs can never touch real Central).
 */
@CacheableTask
abstract class GenerateJreleaserConfigTask : DefaultTask() {
    @get:Input
    abstract val stagingDirectory: Property<String>

    @get:Input
    abstract val gpgPublicKey: Property<String>

    @get:Input
    abstract val gpgPrivateKey: Property<String>

    @get:Input
    abstract val gpgPassphrase: Property<String>

    @get:Input
    abstract val mavenCentralUsername: Property<String>

    @get:Input
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
    abstract val nexusUsername: Property<String>

    @get:Input
    abstract val nexusPassword: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "publishing"
        description = "Generates JReleaser config for Maven Central (staging at stagingDirectory)"
    }

    @TaskAction
    fun generate() {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            MavenCentralWiring.buildYaml(
                MavenCentralWiring.Config(
                    projectName = projectName.get(),
                    projectVersion = projectVersion.get(),
                    projectGroupId = projectGroupId.get(),
                    stagingDir = stagingDirectory.get(),
                    gpgPublicKey = gpgPublicKey.get(),
                    gpgPrivateKey = gpgPrivateKey.get(),
                    gpgPassphrase = gpgPassphrase.get(),
                    mavenCentralUsername = mavenCentralUsername.get(),
                    mavenCentralPassword = mavenCentralPassword.get(),
                    nexusUrl = nexusUrl.orNull,
                    nexusUsername = nexusUsername.get(),
                    nexusPassword = nexusPassword.get(),
                ),
            ),
        )
    }
}
