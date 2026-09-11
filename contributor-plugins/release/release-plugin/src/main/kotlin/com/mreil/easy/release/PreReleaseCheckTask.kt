package com.mreil.easy.release

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Verifies release readiness and resolves release versions; gates `release`.
 *
 * All git state is consumed at execution time via providers. When VCS is
 * absent (provider empty), VCS checks are skipped and coordinates checks
 * still run. Release/next versions resolve as system property > semver, and
 * fail here with guidance when neither is present.
 */
abstract class PreReleaseCheckTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val branch: Property<String>

    @get:Input
    @get:Optional
    abstract val clean: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val upToDate: Property<Boolean>

    @get:Input
    abstract val releaseBranchPattern: Property<String>

    @get:Input
    abstract val hasGroup: Property<Boolean>

    @get:Input
    abstract val hasVersion: Property<Boolean>

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val currentVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val releaseVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val nextVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val commitSha: Property<String>

    @get:ServiceReference("release")
    abstract val releaseState: Property<ReleaseStateService>

    @TaskAction
    fun check() {
        val failures = collectFailures()
        if (failures.isNotEmpty()) {
            throw GradleException("Release readiness check failed:\n- " + failures.joinToString("\n- "))
        }
        logVersions()
        recordState()
    }

    private fun logVersions() {
        val release =
            releaseVersion.orNull ?: throw GradleException(
                "No release version resolved: set -Deasy.release.version=<version> " +
                    "or enable the semver plugin with a valid project version.",
            )
        val next =
            nextVersion.orNull ?: throw GradleException(
                "No next version resolved: set -Deasy.release.nextVersion=<version> " +
                    "or enable the semver plugin with a valid project version.",
            )
        logger.lifecycle(
            "Releasing {}: current version {}, release version {}, next version {}",
            projectName.get(),
            currentVersion.get(),
            release,
            next,
        )
    }

    private fun collectFailures(): List<String> {
        val failures = mutableListOf<String>()
        if (!hasGroup.get()) failures.add("project group is not set")
        if (!hasVersion.get()) failures.add("project version is not set")
        if (!clean.isPresent || !upToDate.isPresent || !branch.isPresent) {
            logger.lifecycle("PreReleaseCheck: VCS unavailable, skipping VCS checks.")
            return failures
        }
        if (!clean.get()) failures.add("working tree is dirty")
        if (!upToDate.get()) failures.add("branch is behind remote")
        val current = branch.get()
        val pattern = releaseBranchPattern.get()
        if (current.isNotBlank() && !Regex(pattern).matches(current)) {
            failures.add("branch '$current' does not match release pattern '$pattern'")
        }
        return failures
    }

    private fun recordState() {
        val state = releaseState.get()
        commitSha.orNull?.takeIf { it.isNotBlank() }?.let { state.recordCommitSha(it) }
        releaseVersion.orNull?.let { state.recordReleaseVersion(it) }
        nextVersion.orNull?.let { state.recordNextVersion(it) }
        state.recordProjectName(projectName.get())
    }
}
