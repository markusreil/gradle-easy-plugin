package com.mreil.easy.release

import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal

/**
 * Exposes the additional files property of `preReleaseCommit` tasks.
 *
 * Implemented by the release plugin's `preReleaseCommit` task so listeners registered via
 * [EasyRelease.beforePreReleaseCommit] can contribute files without coupling the API module
 * to the task implementation.
 */
interface PreReleaseCommitTaskSpec {
    /**
     * Absolute paths of additional files to include in the pre-release commit.
     *
     * Files returned by [ReleaseLifecycleListener.beforePreReleaseCommit] are added here by
     * [EasyRelease.beforePreReleaseCommit]. The task commits the union of the version file and
     * these additional files.
     */
    @get:Internal
    val additionalFiles: ListProperty<String>
}
