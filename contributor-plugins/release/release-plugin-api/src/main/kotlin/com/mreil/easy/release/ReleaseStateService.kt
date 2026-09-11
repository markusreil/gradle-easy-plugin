package com.mreil.easy.release

import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent

/**
 * Holds versions and commit captured by `preReleaseCheck` for downstream release tasks.
 *
 * Registered by [EasyReleasePlugin] as a shared service, not exposed via public extension.
 * Listens for task completions and logs captured state on failures; rollback is not implemented.
 */
@Suppress("TooManyFunctions")
abstract class ReleaseStateService :
    BuildService<ReleaseStateService.Params>,
    OperationCompletionListener {
    abstract class Params : BuildServiceParameters {
        abstract val commitSha: Property<String>
        abstract val releaseVersion: Property<String>
        abstract val nextVersion: Property<String>
        abstract val projectName: Property<String>
        abstract val currentVersion: Property<String>

        init {
            commitSha.convention("")
            releaseVersion.convention("")
            nextVersion.convention("")
            projectName.convention("")
            currentVersion.convention("")
        }
    }

    private val logger = Logging.getLogger(ReleaseStateService::class.java)

    fun recordCommitSha(sha: String) {
        parameters.commitSha.set(sha)
    }

    fun recordReleaseVersion(version: String) {
        parameters.releaseVersion.set(version)
    }

    fun recordNextVersion(version: String) {
        parameters.nextVersion.set(version)
    }

    fun recordProjectName(name: String) {
        parameters.projectName.set(name)
    }

    fun commitSha(): Provider<String> = parameters.commitSha

    fun releaseVersion(): Provider<String> = parameters.releaseVersion

    fun nextVersion(): Provider<String> = parameters.nextVersion

    fun projectName(): Provider<String> = parameters.projectName

    fun currentVersion(): Provider<String> = parameters.currentVersion

    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent || event.result !is TaskFailureResult) return
        val sha = parameters.commitSha.orNull?.takeIf { it.isNotBlank() } ?: return
        logger.error(
            "Task '{}' failed after release check at commit {}. Rollback not yet implemented.",
            event.descriptor.taskPath,
            sha,
        )
    }
}
