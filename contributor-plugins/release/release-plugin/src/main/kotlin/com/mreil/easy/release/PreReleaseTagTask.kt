package com.mreil.easy.release

import com.mreil.easy.vcs.VcsService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Tags the release commit with the release version.
 *
 * Runs after [PreReleaseCommitTask] (so the tag points at the version-bump
 * commit), reading the release version from [ReleaseStateService]. The tag name
 * comes from [tagTemplate]; the placeholder `$v` is replaced with the release
 * version.
 *
 * Without a VCS, [VcsService] is a `VcsNone` no-op and no tag is created.
 */
abstract class PreReleaseTagTask : DefaultTask() {
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
    fun tag() {
        val releaseVersion =
            releaseState.get().releaseVersion().orNull ?: throw GradleException(
                "No release version resolved: set -Deasy.release.version=<version> " +
                    "or enable the semver plugin with a valid project version.",
            )
        val tagName = tagTemplate.get().replace("\$v", releaseVersion)
        if (!vcs.get().tag(tagName).get()) {
            throw GradleException("Failed to create git tag '$tagName'.")
        }
        logger.lifecycle("Created git tag {}.", tagName)
    }
}
