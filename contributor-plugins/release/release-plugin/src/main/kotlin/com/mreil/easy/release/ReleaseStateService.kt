package com.mreil.easy.release

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import java.io.File
import javax.inject.Inject

/**
 * Holds versions and commit captured by `preReleaseCheck` for downstream release tasks.
 *
 * Internal implementation detail of `release-plugin`, not part of the public API: nothing in
 * `release-plugin-api` references it (only `EasyReleaseExtension` is public), and `api` of
 * `release-plugin` is not exposed to consumers. This keeps Gradle `tooling.events.*` types out
 * of the consumer compile classpath.
 *
 * Registered by `EasyReleasePlugin` as a shared service. Listens for task completions: a failed
 * release-group task rolls the local repository back to the state captured at the gate (hard reset
 * to the gate commit + guarded deletion of the release tag); any other failure only logs the
 * captured state.
 */
@Suppress("TooManyFunctions")
internal abstract class ReleaseStateService
    @Inject
    constructor(
        private val providers: ProviderFactory,
    ) : BuildService<ReleaseStateService.Params>,
        OperationCompletionListener {
        abstract class Params : BuildServiceParameters {
            abstract val commitSha: Property<String>
            abstract val releaseVersion: Property<String>
            abstract val nextVersion: Property<String>
            abstract val projectName: Property<String>
            abstract val currentVersion: Property<String>
            abstract val rootDir: DirectoryProperty
            abstract val tagTemplate: Property<String>
            abstract val releaseTaskPaths: ListProperty<String>

            init {
                commitSha.convention("")
                releaseVersion.convention("")
                nextVersion.convention("")
                projectName.convention("")
                currentVersion.convention("")
                tagTemplate.convention("v\$v")
                releaseTaskPaths.convention(emptyList())
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

/**
         * Release tag name: `tagTemplate` with `$v` replaced by the release version.
         *
         * Null when no release version is resolved. Single resolution point for the tag name —
         * release tasks, rollback, and the gate's tag-existence pre-flight all read it here
         * (the deferred form is [tagNameProvider]) so they can never disagree.
         */
        fun tagName(): String? =
            parameters.releaseVersion.orNull
                ?.takeIf { it.isNotBlank() }
                ?.let { parameters.tagTemplate.get().replace("\$v", it) }

        /**
         * Deferred form of [tagName]: realized at execution time, never at configuration time.
         *
         * Used to wire task inputs (e.g. `preReleaseCheck`'s tag-existence check) so the
         * configuration cache tracks the resolution instead of capturing an eager value.
         * Empty string when no release version is resolved (Gradle `Provider<T>` requires
         * `T : Any`, so null is signalled via the empty sentinel — `hasTag("")` is always
         * false and short-circuits the existence check).
         */
        fun tagNameProvider(): Provider<String> = providers.provider { tagName() ?: "" }

        override fun onFinish(event: FinishEvent) {
            if (event !is TaskFinishEvent || event.result !is TaskFailureResult) return
            val sha = parameters.commitSha.orNull?.takeIf { it.isNotBlank() } ?: return
            val taskPath = event.descriptor.taskPath
            if (taskPath in parameters.releaseTaskPaths.orNull.orEmpty()) {
                rollback(taskPath)
            } else {
                logger.error(
                    "Task '{}' failed after release check at commit {}. No rollback for non-release-group tasks.",
                    event.descriptor.taskPath,
                    sha,
                )
            }
        }

        /**
         * Restores the local repository to the state captured by `preReleaseCheck` after a failed
         * release-group task: hard-resets to the gate commit and deletes the release tag created
         * by this run. Nothing is ever reset against the remote — a failed release means nothing
         * was pushed, and if an earlier invocation pushed, the gate commit is then-current HEAD.
         */
        fun rollback(failedTaskPath: String) {
            val sha = parameters.commitSha.orNull?.takeIf { it.isNotBlank() } ?: return
            val root = parameters.rootDir.orNull?.asFile ?: return
            logger.error(
                "Task '{}' failed after release check at commit {}. Rolling back local changes.",
                failedTaskPath,
                sha,
            )
            if (isGitRepository(root)) {
                if (resetToGate(root, sha)) {
                    logger.error("Rolled back working tree to commit {}.", sha)
                    deleteReleaseTagIfCreatedAfterGate(root, sha)
                } else {
                    logger.error("Rollback failed: 'git reset --hard {}' did not succeed.", sha)
                }
            } else {
                logger.error(
                    "Task '{}' failed after release check at commit {}; {} is not a git repository, nothing to roll back.",
                    failedTaskPath,
                    sha,
                    root,
                )
            }
        }

        private fun isGitRepository(root: File): Boolean = root.resolve(".git").isDirectory

        private fun resetToGate(
            root: File,
            gateSha: String,
        ): Boolean = gitResult(root, listOf("reset", "--hard", gateSha)).first == 0

        private fun deleteReleaseTagIfCreatedAfterGate(
            root: File,
            gateSha: String,
        ) {
            val tag = tagName() ?: return
            if (!tagCreatedAfter(root, tag, gateSha)) return
            if (gitResult(root, listOf("tag", "-d", tag)).first == 0) {
                logger.error("Deleted release tag {}.", tag)
            } else {
                logger.error("Rollback: could not delete release tag {}.", tag)
            }
        }

        private fun tagCreatedAfter(
            root: File,
            tag: String,
            gateSha: String,
        ): Boolean {
            val (exit, target) = gitResult(root, listOf("rev-parse", "--verify", "--quiet", "$tag^{commit}"))
            if (exit != 0 || target.isBlank() || target == gateSha) return false
            return gitResult(root, listOf("merge-base", "--is-ancestor", gateSha, target)).first == 0
        }

        private fun gitResult(
            root: File,
            arguments: List<String>,
        ): Pair<Int, String> {
            val output =
                providers.exec {
                    it.commandLine(listOf("git") + arguments)
                    it.workingDir = root
                    it.isIgnoreExitValue = true
                }
            return output.result.get().exitValue to
                output.standardOutput.asText
                    .get()
                    .trim()
        }
    }
