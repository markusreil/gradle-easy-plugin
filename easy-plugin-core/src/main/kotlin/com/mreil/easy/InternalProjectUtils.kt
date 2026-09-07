package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import kotlin.reflect.KClass

/**
 * Core-only project plumbing for fan-out and registry access.
 *
 * Deliberately `internal` to `easy-plugin-core`: contributors must not depend on
 * fan-out ordering or registry lookup. Contributor-facing helpers (e.g. `Project.isRoot`)
 * live in `easy-contributor-support`.
 *
 * Returns the root project followed by all subprojects sorted by path.
 *
 * Centralizes the ordering used by [ProjectPlugin], [PluginRegistrar] and [ExtensionRegistrar]
 * to ensure consistent fan-out when a contributor is annotated with [ApplyToSubprojects].
 */
internal fun orderedAllProjects(project: Project): List<Project> =
    project.gradle.rootProject.let { root -> listOf(root) + root.subprojects.sortedBy { it.path } }

/**
 * Checks whether [pluginClass] is annotated with [ApplyToSubprojects].
 *
 * Centralizes the `isAnnotationPresent` check duplicated in [ProjectPlugin] and [PluginRegistrar].
 * Only the annotation on the plugin itself determines subproject fan-out.
 */
internal fun PluginRegistry.shouldApplyToSubprojects(pluginClass: KClass<out Plugin<Project>>): Boolean =
    pluginClass.java.isAnnotationPresent(ApplyToSubprojects::class.java)

internal fun Project.getPluginRegistry(): PluginRegistryService = gradle.getRegistry()

internal fun Settings.getPluginRegistry(): PluginRegistryService = gradle.getRegistry()

private fun Gradle.getRegistry(): PluginRegistryService =
    sharedServices.registerIfAbsent(PluginRegistry.NAME, PluginRegistryService::class.java).get()

internal fun Project.hasEasyExtension(): Boolean = extensions.findByType(EasyExtension::class.java) != null
