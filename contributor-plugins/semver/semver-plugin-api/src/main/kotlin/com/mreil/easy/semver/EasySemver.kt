package com.mreil.easy.semver

import com.mreil.easy.isExtensionEnabled
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
     * @param project the project whose version should be read.
     * @return a [Provider] that yields the parsed [Semver] when realized.
     */
    @Suppress("TooGenericExceptionCaught")
    fun of(project: Project): Provider<Semver> =
        project.providers.provider { project.version.toString() }.map { raw ->
            if (!project.isExtensionEnabled(EasySemverExtension::class)) {
                error("EasySemver plugin is not enabled - add `easy { semver {} }` to enable it")
            }
            val clean =
                raw.takeIf { it.isNotEmpty() && it != "unspecified" }
                    ?: error("Project version must be set for semver lookup (e.g. version = \"1.0.0\")")
            try {
                Semver.parse(clean) ?: error("Version '$clean' is not valid semver")
            } catch (e: Exception) {
                throw IllegalStateException("Version '$clean' is not valid semver: ${e.message}", e)
            }
        }
}
