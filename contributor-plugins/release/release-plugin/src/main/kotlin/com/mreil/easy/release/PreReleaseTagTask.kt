package com.mreil.easy.release

import com.mreil.easy.vcs.VcsService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.TaskAction

/**
 * Tags the release commit with the release version.
 *
 * Runs after [PreReleaseCommitTask] (so the tag points at the version-bump
 * commit), reading the release tag name from [ReleaseStateService] (resolved
 * once from the extension's tag template, so rollback deletes exactly the tag
 * created here).
 *
 * Without a VCS, [VcsService] is a `VcsNone` no-op and no tag is created.
 */
abstract class PreReleaseTagTask : DefaultTask() {
    @get:ServiceReference("release")
    abstract val releaseState: Property<ReleaseStateService>

    @get:ServiceReference("vcs")
    abstract val vcs: Property<VcsService>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun tag() {
        val tagName =
            releaseState.get().tagName() ?: throw GradleException(
                "No release version resolved: set -Deasy.release.version=<version> " +
                    "or enable the semver plugin with a valid project version.",
            )
        if (!vcs.get().tag(tagName).get()) {
            throw GradleException("Failed to create git tag '$tagName'.")
        }
        logger.lifecycle("Created git tag {}.", tagName)
    }
}
