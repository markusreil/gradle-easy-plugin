package com.mreil.easy.publish

import org.semver4j.Semver

/**
 * Pure repository routing rules for snapshot/release filtering.
 *
 * **Snapshot definition**: a version counts as a snapshot iff its parsed semver pre-release
 * component is exactly `SNAPSHOT` (the Maven convention, case-sensitive). Releases (including
 * `0.x` versions like `0.0.105`) and other pre-releases (e.g. `1.0.0-RC1`, `1.0.0-alpha.1`)
 * are treated as releases. When semver is disabled or parsing fails, the `-SNAPSHOT` version
 * suffix fallback decides instead.
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

    fun isSnapshot(semver: Semver?): Boolean? = semver?.let { it.getPreRelease() == listOf("SNAPSHOT") }
}
