package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.KClass

/** Project plugin that discovers and applies contributed plugins. */
class ProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val registry = project.getPluginRegistry()
        registry.loadFromServiceLoader(javaClass.classLoader)
        if (!project.hasEasyExtension()) {
            ExtensionRegistrar.createExtensionWithSubprojects(
                project,
                registry,
            )
        }
        // Defer applying contributed plugins until ProjectPlugin is fully applied via withType,
        // mirroring PluginRegistrar's ordering (avoids premature plugin application).
        project.plugins.withType(ProjectPlugin::class.java) {
            applyRegisteredPlugins(project, registry)
        }
    }

    private fun applyRegisteredPlugins(
        project: Project,
        registry: PluginRegistryService,
    ) {
        val allProjects = orderedAllProjects(project)
        registry.getProjectPlugins().forEach { kclass ->
            val targets = targetsFor(kclass, project, allProjects, registry)
            targets.forEach { target -> target.pluginManager.apply(kclass.java) }
        }
    }

    private fun targetsFor(
        kclass: KClass<out Plugin<Project>>,
        project: Project,
        allProjects: List<Project>,
        registry: PluginRegistryService,
    ): List<Project> {
        val toSubprojects = registry.shouldApplyToSubprojects(kclass)
        return if (toSubprojects) allProjects else listOf(project)
    }
}
