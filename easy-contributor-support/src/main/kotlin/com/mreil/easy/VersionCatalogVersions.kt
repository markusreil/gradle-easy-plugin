package com.mreil.easy

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Returns the version for [alias] in the `libs` version catalog, or [default] when absent.
 *
 * Looks up `extensions.findByType(VersionCatalogsExtension)?.find("libs")?.findVersion(alias)`.
 * Consumer builds may have no catalog or no such alias — both (plus blank versions) fall back
 * to [default] instead of failing. Never throws.
 *
 * Timing contract: call only once version catalogs are final — inside `afterEnabled`, a
 * [org.gradle.api.provider.Provider], or a task action. Catalogs are fixed after settings
 * evaluation, so wiring-time calls are safe.
 */
fun Project.catalogVersionOrDefault(
    alias: String,
    default: String,
    catalogName: String = "libs",
): String =
    runCatching {
        extensions
            .findByType(VersionCatalogsExtension::class.java)
            ?.find(catalogName)
            ?.orElse(null)
            ?.findVersion(alias)
            ?.orElse(null)
            ?.requiredVersion
            ?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: default
