package com.mreil.utils

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import java.util.Optional
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull

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
    Optional
        .ofNullable(extensions.findByType(VersionCatalogsExtension::class.java))
        .flatMap { it.find(catalogName) }
        .flatMap { it.findVersion(alias) }
        .map { it.requiredVersion }
        .filter { it.isNotBlank() }
        .getOrElse { default }

/**
 * Returns the library declared for [alias] in the [catalogName] version catalog.
 *
 * The provider is absent when the catalog or the alias is missing, so callers test it with
 * [Provider.isPresent]/[Provider.orNull]. The provider keeps the catalog's coordinates and version
 * constraint lazy, so it can be added directly to a configuration or
 * [org.gradle.api.artifacts.dsl.DependencyCollector].
 *
 * Timing contract: call only once version catalogs are final — inside `afterEnabled`, a [Provider],
 * or a task action. Catalogs are fixed after settings evaluation, so wiring-time calls are safe.
 */
fun Project.catalogLibrary(
    alias: String,
    catalogName: String = "libs",
): Provider<MinimalExternalModuleDependency> =
    extensions
        .findByType(VersionCatalogsExtension::class.java)
        ?.find(catalogName)
        ?.flatMap { it.findLibrary(alias) }
        ?.getOrNull()
        ?: providers.provider { null }
