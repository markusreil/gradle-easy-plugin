package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Project plugin that discovers and applies contributed plugins.
 *
 * Root-first contract: apply to the root project (directly, or via [SettingsPlugin] which
 * creates the root extension from the settings `easy` block first). The root extension is then
 * injected into every subproject ([injectEasyExtensions]), so a missing `easy` extension on a
 * non-root project means the root setup was bypassed — applying the plugin to a bare subproject
 * without root setup is unsupported and fails fast (see below).
 */
class ProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val registry = project.getPluginRegistry()
        registry.loadFromServiceLoader(javaClass.classLoader)
        if (!project.hasEasyExtension()) {
            check(project.isRoot()) {
                "ProjectPlugin applied to non-root project '${project.path}' without an 'easy' extension. " +
                    "Apply the plugin to the root project (or via the settings plugin) so 'easy' is injected to subprojects."
            }
            ExtensionRegistrar(project, project.providers).createExtension(registry)
        }
        if (project.isRoot()) {
            injectEasyExtensions(project, registry, project.getEasyExtension())
        }
        // Defer applying contributed plugins until ProjectPlugin is fully applied via withType,
        // mirroring PluginRegistrar's ordering (avoids premature plugin application).
        PluginRegistrar.applyPlugins(project, registry)
    }
}

/**
 * Copies the `easy` extension to every subproject missing one, parented at the root extension.
 *
 * Explicit here (not inside [ExtensionRegistrar], which is single-target by construction) so
 * both fan-out decisions — extensions here, plugins in [PluginRegistrar] — are visible together.
 * A plugin applied to subprojects always finds its `easy.*` extension with values copied from
 * the root. No-op on non-root projects.
 *
 * Snapshot semantics: only projects present at root-apply time are covered — projects added
 * later receive neither the extension (here) nor contributed plugins (see [PluginRegistrar]),
 * and hand-applying [ProjectPlugin] to them fails via the root-first contract above.
 */
internal fun injectEasyExtensions(
    root: Project,
    registry: PluginRegistry,
    parent: EasyExtension,
) {
    if (!root.isRoot()) return
    orderedAllProjects(root)
        .filter { it != root && !it.hasEasyExtension() }
        .forEach { ExtensionRegistrar(it, it.providers).createExtension(registry, parent) }
}
