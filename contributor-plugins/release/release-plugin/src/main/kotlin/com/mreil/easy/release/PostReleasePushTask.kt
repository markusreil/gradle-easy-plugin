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
 * Bumps to the next development version, commits it and pushes commit and tag.
 *
 * Runs after [PreReleaseTagTask] (so the release tag exists), reading the next
 * development version from [ReleaseStateService]. The version file is rewritten
 * to the next version and committed as a distinct commit; the commit and the
 * release tag are then pushed in a single atomic operation.
 *
 * Without a VCS, [VcsService] is a `VcsNone` no-op: the file is still updated
 * but nothing is pushed.
 */
abstract class PostReleasePushTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val versionFile: RegularFileProperty

    @get:Input
    abstract val commitMessageTemplate: Property<String>

    @get:Input
    abstract val tagTemplate: Property<String>

    @get:ServiceReference("release")
    abstract val releaseState: Property<ReleaseStateService>

    @get:ServiceReference("vcs")
    abstract val vcs: Property<VcsService>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun push() {
        val nextVersion = nextVersion()
        val file = versionFile.get().asFile
        if (GradleProperties.writeValue(file, "version", nextVersion)) {
            val message = commitMessageTemplate.get().replace("\$v", nextVersion)
            if (!vcs.get().addAndCommit(listOf(file.absolutePath), message).get()) {
                throw GradleException("Failed to git add/commit version file $file (message: '$message').")
            }
        } else {
            logger.lifecycle("Version file {} already at next version {}, nothing to commit.", file, nextVersion)
        }
        val tagName = tagName()
        if (!vcs.get().push(tagName).get()) {
            throw GradleException("Failed to push commit and tag '$tagName'.")
        }
        logger.lifecycle("Pushed commit and tag {}.", tagName)
    }

    private fun nextVersion(): String =
        releaseState.get().nextVersion().orNull ?: throw GradleException(
            "No next development version resolved: set -Deasy.release.nextVersion=<version> " +
                "or enable the semver plugin with a valid project version.",
        )

    private fun tagName(): String {
        val releaseVersion =
            releaseState.get().releaseVersion().orNull ?: throw GradleException(
                "No release version resolved: set -Deasy.release.version=<version> " +
                    "or enable the semver plugin with a valid project version.",
            )
        return tagTemplate.get().replace("\$v", releaseVersion)
    }
}
