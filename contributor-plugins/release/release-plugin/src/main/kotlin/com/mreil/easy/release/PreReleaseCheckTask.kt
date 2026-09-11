package com.mreil.easy.release

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Verifies release readiness; gates `release`.
 *
 * All git state is consumed at execution time via providers. When VCS is
 * absent (provider empty), VCS checks are skipped and coordinates checks
 * still run.
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

    @TaskAction
    fun check() {
        val failures = collectFailures()
        if (failures.isNotEmpty()) {
            throw GradleException("Release readiness check failed:\n- " + failures.joinToString("\n- "))
        }
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
}
