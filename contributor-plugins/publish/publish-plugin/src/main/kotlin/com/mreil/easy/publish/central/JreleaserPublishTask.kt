package com.mreil.easy.publish.central

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction

/**
 * Deploys staged Maven artifacts via the JReleaser CLI (`deploy`).
 *
 * Runs `org.jreleaser.cli.Main` from the resolve-only `jreleaser` configuration
 * (no external binary needed) against the generated JReleaser YAML config.
 * Registered only on the root project as `publishToMavenCentral` when
 * [com.mreil.easy.publish.EasyPublishExtension.toMavenCentral] is set (see
 * [EasyJreleaserPlugin] for the gating rule). Not cacheable (remote side effects);
 * declared inputs give up-to-date skipping, which also protects against Central
 * rejecting redeploys.
 */
abstract class JreleaserPublishTask : JavaExec() {
    @get:Classpath
    @get:InputFiles
    abstract val jreleaserClasspath: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configFile: RegularFileProperty

    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val dryRun: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val deployerName: Property<String>

    /**
     * Whether any `PublishToMavenRepository` upload task stages artifacts for this deploy.
     *
     * Resolved once the task graph is final (see `JreleaserDeployWiring`) and read as a
     * plain task input — querying `taskDependencies` at execution time is unsupported
     * with the configuration cache. Unset means staged (convention `true` in the wiring).
     */
    @get:Input
    abstract val hasStagedUploads: Property<Boolean>

    init {
        group = "publishing"
        description = "Deploys staged artifacts to Maven Central via JReleaser (deploy)"
        mainClass.convention(JreleaserVersions.MAIN_CLASS)
    }

    @TaskAction
    override fun exec() {
        ensureStagedUploads()
        setClasspath(jreleaserClasspath)
        args = buildArgs()
        environment("JRELEASER_PROJECT_VERSION", projectVersion.get())
        super.exec()
    }

    internal fun ensureStagedUploads() {
        if (!hasStagedUploads.getOrElse(true)) {
            logger.warn(
                "publishToMavenCentral: no PublishToMavenRepository tasks found - nothing staged, skipping deploy.",
            )
            throw StopExecutionException("Nothing staged for Maven Central deployment.")
        }
    }

    internal fun buildArgs(): List<String> =
        buildList {
            add("deploy")
            add("-c")
            add(configFile.get().asFile.absolutePath)
            if (dryRun.get()) add("--dry-run")
            deployerName.orNull?.let {
                add("-yn")
                add(it)
            }
        }
}
