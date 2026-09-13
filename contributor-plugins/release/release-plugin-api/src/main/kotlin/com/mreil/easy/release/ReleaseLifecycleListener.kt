package com.mreil.easy.release

import org.gradle.api.provider.Provider
import java.io.File

/**
 * Listener invoked at key points of the release lifecycle.
 *
 * Implementations are attached as task actions, not stored as [BuildService] state, so they survive
 * configuration-cache reuse. Implementations must capture only configuration-cache-serializable state
 * — never [Project], [Gradle][org.gradle.api.invocation.Gradle], [Task][org.gradle.api.Task] or
 * extension instances.
 */
fun interface ReleaseLifecycleListener {
    /**
     * Invoked before [preReleaseCommit][PreReleaseCommitTask] rewrites the version file.
     *
     * Return the list of files the listener wrote or updated. These files are committed together
     * with the version file by [PreReleaseCommitTask]. An empty list means the listener did not
     * produce any files to commit.
     *
     * @param releaseVersion the resolved release version; lazy and empty string when unresolved.
     * @return files to include in the pre-release commit.
     */
    fun beforePreReleaseCommit(releaseVersion: Provider<String>): List<File>
}
