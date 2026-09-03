package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import kotlin.reflect.KClass

/**
 * Returns the root project followed by all subprojects sorted by path.
 *
 * Centralizes the ordering used by [ProjectPlugin], [PluginRegistrar] and [ExtensionRegistrar]
 * to ensure consistent fan-out when a contributor is annotated with [ApplyToSubprojects].
 */
internal fun orderedAllProjects(project: Project): List<Project> {
    val root = project.gradle.rootProject
    return listOf(root) + root.subprojects.sortedBy { it.path }
}

/**
 * Checks whether [pluginClass] is annotated with [ApplyToSubprojects].
 *
 * Centralizes the `isAnnotationPresent` check duplicated in [ProjectPlugin] and [PluginRegistrar].
 * Only the annotation on the plugin itself determines subproject fan-out.
 */
internal fun PluginRegistry.shouldApplyToSubprojects(pluginClass: KClass<out Plugin<Project>>): Boolean =
    pluginClass.java.isAnnotationPresent(ApplyToSubprojects::class.java)

internal fun Project.getPluginRegistry(): PluginRegistryService =
    gradle.sharedServices.registerIfAbsent(PluginRegistry.NAME, PluginRegistryService::class.java).get()

internal fun Settings.getPluginRegistry(): PluginRegistryService =
    gradle.sharedServices.registerIfAbsent(PluginRegistry.NAME, PluginRegistryService::class.java).get()

internal fun Project.hasEasyExtension(): Boolean = extensions.findByType(EasyExtension::class.java) != null
