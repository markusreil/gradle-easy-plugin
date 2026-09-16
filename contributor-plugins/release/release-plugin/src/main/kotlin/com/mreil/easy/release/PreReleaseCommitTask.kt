package com.mreil.easy.release

import com.mreil.easy.vcs.VcsService
import com.mreil.utils.GradleProperties
import com.mreil.utils.required
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Writes the release version into the version file and commits it together with any files
 * contributed by [ReleaseLifecycleListener] implementations.
 *
 * Runs after [PreReleaseCheckTask] (release group gate), reading the release
 * version from [ReleaseStateService]. The version file is rewritten and committed as a
 * distinct commit so the release build (a separate Gradle invocation) picks up
 * the new `gradle.properties` at configuration time.
 *
 * Listeners registered via [EasyRelease.beforePreReleaseCommit] may return additional files;
 * these are added to [additionalFiles] and committed in the same commit. The version file is
 * always included when anything changed; if it is already at the release version and no listener
 * contributed files, the task is a no-op.
 *
 * Without a VCS, [VcsService] is a `VcsNone` no-op: files are still updated but not committed.
 */
abstract class PreReleaseCommitTask :
    DefaultTask(),
    PreReleaseCommitTaskSpec {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val versionFile: RegularFileProperty

    @get:Input
    abstract val commitMessageTemplate: Property<String>

    @get:Internal
    abstract override val additionalFiles: ListProperty<String>

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
            state.releaseVersion().required(
                "No release version resolved: set -Deasy.release.version=<version> " +
                    "or enable the semver plugin with a valid project version.",
            )
        val versionFile = versionFile.get().asFile
        val versionChanged = GradleProperties.writeValue(versionFile, "version", releaseVersion)
        val extraFiles =
            additionalFiles.orNull
                ?.filter { it.isNotBlank() }
                ?.map { File(it) }
                ?.filter { it.exists() }
                ?.distinctBy { it.absolutePath }
                .orEmpty()
        if (!versionChanged && extraFiles.isEmpty()) {
            logger.lifecycle(
                "Version file {} already at release version {} and no listener files contributed; nothing to commit.",
                versionFile,
                releaseVersion,
            )
            return
        }
        val paths =
            buildList {
                add(versionFile.absolutePath)
                extraFiles.mapTo(this) { it.absolutePath }
            }.distinct()
        val message = commitMessageTemplate.get().replace("\$v", releaseVersion)
        if (!vcs.get().addAndCommit(paths, message).get()) {
            throw GradleException(
                "Failed to git add/commit release files ${paths.joinToString()} (message: '$message').",
            )
        }
        logger.lifecycle("Committed release version {} in {} (extra files: {}).", releaseVersion, versionFile, extraFiles)
    }
}
