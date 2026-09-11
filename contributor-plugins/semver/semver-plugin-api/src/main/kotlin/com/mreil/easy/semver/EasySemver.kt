package com.mreil.easy.semver

import com.mreil.easy.gatedBy
import com.mreil.utils.isSpecified
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.semver4j.Semver

/**
 * Public entry point for lazy semver lookup.
 *
 * Consumers only need to pass a [Project] instance. The raw version source
 * (currently `project.version`) is hidden and may change without breaking callers.
 * Parsing is strict - invalid semver fails at provider realization.
 */
object EasySemver {
    /**
     * Lazily parses the project's version as [Semver].
     *
     * Absent (`orNull == null`) when the semver extension is not enabled; fails
     * lazily on an invalid version only when enabled.
     *
     * @param project the project whose version should be read.
     * @return a [Provider] that yields the parsed [Semver] when realized.
     */
    fun of(project: Project): Provider<Semver> =
        project.gatedBy(EasySemverExtension::class) {
            project.providers.provider { project.version.toString() }.map(::parseStrict)
        }

    @Suppress("TooGenericExceptionCaught")
    private fun parseStrict(raw: String): Semver {
        val clean =
            raw.takeIf { it.isSpecified() }
                ?: error("Project version must be set for semver lookup (e.g. version = \"1.0.0\")")
        try {
            return Semver.parse(clean) ?: error("Version '$clean' is not valid semver")
        } catch (e: Exception) {
            throw IllegalStateException("Version '$clean' is not valid semver: ${e.message}", e)
        }
    }
}
