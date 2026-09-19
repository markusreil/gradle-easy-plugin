package com.mreil.easy

import com.mreil.utils.isRoot
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Project-scope entry-point logic for the `easy` project plugin.
 *
 * Declared in core so [PluginRegistrar] can trigger on it without core depending on the concrete
 * marker plugin (which would create a dependency cycle). The concrete `ProjectPlugin` lives in the
 * marker module so that [apply]'s `javaClass.classLoader` resolves to the project buildscript
 * classloader, which can see project-only plugins such as KGP. Moving this `apply` body into a
 * top-level function/object in core would silently resolve the wrong classloader and break that.
 *
 * Root-first contract: apply to the root project. It creates [EasyExtension] there, injects copied
 * `easy` extensions into every subproject ([injectEasyExtensions]) and applies the project-scope
 * contributors, so a missing `easy` extension on a non-root project means the root setup was
 * bypassed — applying the plugin to a bare subproject is unsupported and fails fast (see below).
 *
 * Settings scope is set up separately by `com.mreil.easy.settings`; that plugin creates
 * [EasySettingsExtension] and never pushes configuration into projects.
 *
 * Open (not abstract) so tests and pre-split harnesses can apply the entry point directly; only the
 * concrete marker class is registered under a plugin ID.
 */
open class ProjectPluginEntryPoint : Plugin<Project> {
    final override fun apply(project: Project) {
        val registry = project.getPluginRegistry()
        registry.loadFromServiceLoader(javaClass.classLoader)
        if (!project.hasEasyExtension()) {
            check(project.isRoot()) {
                "ProjectPlugin applied to non-root project '${project.path}' without an 'easy' extension. " +
                    "Apply the plugin to the root project so 'easy' is injected to subprojects."
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
 * and hand-applying [ProjectPluginEntryPoint] to them fails via the root-first contract above.
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
