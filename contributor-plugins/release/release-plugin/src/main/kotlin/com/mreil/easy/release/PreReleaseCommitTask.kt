package com.mreil.easy.release

import com.mreil.easy.vcs.VcsService
import com.mreil.utils.GradleProperties
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Writes the release version into the version file and commits it.
 *
 * Runs after [PreReleaseCheckTask] (release group gate), reading the release
 * version from [ReleaseStateService]. The file is rewritten and committed as a
 * distinct commit so the release build (a separate Gradle invocation) picks up
 * the new `gradle.properties` at configuration time.
 *
 * Without a VCS, [VcsService] is a `VcsNone` no-op: the file is still updated
 * but not committed.
 */
abstract class PreReleaseCommitTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val versionFile: RegularFileProperty

    @get:Input
    abstract val commitMessageTemplate: Property<String>

    @get:ServiceReference("release")
    internal abstract val releaseState: Property<ReleaseStateService>

    @get:ServiceReference("vcs")
    abstract val vcs: Property<VcsService>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun commit() {
        val state = releaseState.get()
        val releaseVersion =
            state.releaseVersion().orNull ?: throw GradleException(
                "No release version resolved: set -Deasy.release.version=<version> " +
                    "or enable the semver plugin with a valid project version.",
            )
        val file = versionFile.get().asFile
        if (!GradleProperties.writeValue(file, "version", releaseVersion)) {
            logger.lifecycle("Version file {} already at release version {}, nothing to commit.", file, releaseVersion)
            return
        }
        val message = commitMessageTemplate.get().replace("\$v", releaseVersion)
        if (!vcs.get().addAndCommit(listOf(file.absolutePath), message).get()) {
            throw GradleException("Failed to git add/commit version file $file (message: '$message').")
        }
        logger.lifecycle("Committed release version {} in {}.", releaseVersion, file)
    }
}
