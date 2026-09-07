package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import kotlin.reflect.KClass

/**
 * Central utility responsible for applying registered plugins to [Project] and [Settings] instances.
 *
 * It manages:
 * - Applying registered plugins to [Settings] instances when [PluginIds.SETTINGS] is applied.
 * - Applying registered plugins to [Project] instances when [ProjectPlugin] is applied.
 * - Resolving target projects for plugin application (e.g. root only vs. subprojects when annotated with [ApplyToSubprojects]).
 *
 * Note: Plugins are applied eagerly. Plugins annotated with [EnabledBy] must guard their
 * own behavior lazily via the referenced [EasyPluginExtension]'s [CanBeEnabled.enabled]
 * [org.gradle.api.provider.Provider] (e.g. `onlyIf { enabled.getOrElse(true) }` or
 * `configureEach` with a provider) so `easy { ... }` configuration after `plugins {}` is respected
 * without deferring `pluginManager.apply` to `afterEvaluate`/`settingsEvaluated`, which would be
 * too late for early setup like `extensions.create` or `tasks.register`.
 */
object PluginRegistrar {
    /**
     * Applies all registered Settings plugins from [registry] to the given [settings] instance.
     *
     * @param settings The [Settings] instance to apply plugins to.
     * @param registry The [PluginRegistry] holding registered plugin types.
     */
    fun applyPlugins(
        settings: Settings,
        registry: PluginRegistry,
    ) {
        settings.pluginManager.withPlugin(PluginIds.SETTINGS) {
            registry
                .getSettingsPlugins()
                .forEach { kclass ->
                    settings.pluginManager.apply(kclass.java)
                }
        }
    }

    /**
     * Applies all registered Project plugins from [registry] to the given [project] instance (and subprojects if applicable).
     *
     * @param project The [Project] instance to apply plugins to.
     * @param registry The [PluginRegistry] holding registered plugin types.
     */
    fun applyPlugins(
        project: Project,
        registry: PluginRegistry,
    ) {
        project.plugins.withType(ProjectPlugin::class.java) {
            applyRegisteredPlugins(project, registry)
        }
    }

    private fun applyRegisteredPlugins(
        project: Project,
        registry: PluginRegistry,
    ) {
        val allProjects = orderedAllProjects(project)
        registry.getProjectPlugins().forEach { kclass ->
            val targets = targetsFor(kclass, project, allProjects, registry)
            targets.forEach { target ->
                target.pluginManager.apply(kclass.java)
            }
        }
    }

    /**
     * Determines the target projects to which a given plugin should be applied.
     *
     * If the plugin is annotated with [ApplyToSubprojects], the plugin
     * will be applied to all projects in the build (root and subprojects). Otherwise,
     * it will only be applied to the current project.
     *
     * Snapshot semantics (mirroring [injectEasyExtensions]): only projects in [allProjects]
     * at call time are covered — projects added later are missed by both fan-outs.
     *
     * @param kclass The plugin class to determine targets for.
     * @param project The current project requesting plugin application.
     * @param allProjects A sorted list of all projects in the build (root + subprojects).
     * @param registry The plugin registry containing contributor metadata.
     * @return A list of projects to which the plugin should be applied.
     */
    private fun targetsFor(
        kclass: KClass<out Plugin<Project>>,
        project: Project,
        allProjects: List<Project>,
        registry: PluginRegistry,
    ): List<Project> = if (registry.shouldApplyToSubprojects(kclass)) allProjects else listOf(project)
}
