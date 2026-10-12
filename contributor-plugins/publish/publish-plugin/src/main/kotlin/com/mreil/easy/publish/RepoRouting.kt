package com.mreil.easy.publish

import org.gradle.api.GradleException
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
 * Roles are explicit ([MavenRepoSpec.role]): a repository publishes exactly what its role
 * declares. An unset role fails fast so a missing declaration can never silently change
 * what gets published.
 */
internal object RepoRouting {
    fun shouldPublishToRepo(
        spec: MavenRepoSpec,
        isSnapshot: Boolean?,
    ): Boolean {
        if (isSnapshot == null) return true
        return when (requireRole(spec)) {
            RepoRole.SNAPSHOT -> isSnapshot
            RepoRole.RELEASE -> !isSnapshot
            RepoRole.NEUTRAL -> true
        }
    }

    fun requireRole(spec: MavenRepoSpec): RepoRole =
        spec.role.orNull
            ?: throw GradleException(
                "Repository '${spec.name}' declares no role. Set `role` to RepoRole.SNAPSHOT, " +
                    "RepoRole.RELEASE or RepoRole.NEUTRAL in the mavenRepo declaration.",
            )

    fun isSnapshot(semver: Semver?): Boolean? = semver?.let { it.preRelease == listOf("SNAPSHOT") }
}
