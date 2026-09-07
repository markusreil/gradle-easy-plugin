package com.mreil.easy.publish

import org.semver4j.Semver

/**
 * Pure repository routing rules for snapshot/release filtering.
 *
 * Naming contract: a repository whose name contains `release` (case-insensitive)
 * is a release repo, one containing `snapshot` is a snapshot repo, anything else
 * is neutral and always used. A repo containing both counts as both, so it is
 * skipped for either kind (e.g. `mySnapshotRelease` never publishes).
 */
internal object RepoRouting {
    fun shouldPublishToRepo(
        repoName: String,
        isSnapshot: Boolean?,
    ): Boolean {
        if (isSnapshot == null) return true
        val lower = repoName.lowercase()
        val isReleaseRepo = lower.contains("release")
        val isSnapshotRepo = lower.contains("snapshot")
        return when {
            isSnapshot -> !isReleaseRepo
            else -> !isSnapshotRepo
        }
    }

    fun isSnapshot(semver: Semver?): Boolean? = semver?.let { !it.isStable }
}
