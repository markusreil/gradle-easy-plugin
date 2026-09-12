package com.mreil.easy.release

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
 * Verifies release readiness and resolves release versions; gates `release`.
 *
 * Readiness inputs (branch, clean, upToDate, pattern, group, version) stay task
 * inputs. Versions and names resolve from [ReleaseStateService] (wired once in
 * the plugin); the commit sha is snapshotted here at execution, right after the
 * gate passes (HEAD-at-gate — a lazily-wired sha could capture a moved HEAD).
 *
 * Also verifies the configured [versionFile] is tracked by git: an untracked or
 * ignored file survives `git reset --hard` on rollback, so a release that fails
 * after rewriting it would leave the half-written file behind. Fails fast at the
 * gate with a clear message instead.
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
    @get:Optional
    abstract val commitSha: Property<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val versionFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val versionFileTracked: Property<Boolean>

    @get:ServiceReference("release")
    internal abstract val releaseState: Property<ReleaseStateService>

    @TaskAction
    fun check() {
        val failures = collectFailures()
        if (failures.isNotEmpty()) {
            throw GradleException("Release readiness check failed:\n- " + failures.joinToString("\n- "))
        }
        logVersions()
        commitSha.orNull?.takeIf { it.isNotBlank() }?.let { releaseState.get().recordCommitSha(it) }
    }

    private fun logVersions() {
        val state = releaseState.get()
        val release =
            state.releaseVersion().orNull ?: throw GradleException(
                "No release version resolved: set -Deasy.release.version=<version> " +
                    "or enable the semver plugin with a valid project version.",
            )
        val next =
            state.nextVersion().orNull ?: throw GradleException(
                "No next version resolved: set -Deasy.release.nextVersion=<version> " +
                    "or enable the semver plugin with a valid project version.",
            )
        logger.lifecycle(
            "Releasing {}: current version {}, release version {}, next version {}",
            state.projectName().get(),
            state.currentVersion().get(),
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
        if (versionFileTracked.isPresent && !versionFileTracked.get()) {
            failures.add(
                "version file '${versionFile.get().asFile}' is not tracked by git " +
                    "(an untracked file would survive 'git reset --hard' on rollback)",
            )
        }
        return failures
    }
}
