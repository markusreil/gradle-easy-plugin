package com.mreil.easy

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import kotlin.reflect.KClass

/**
 * Central utility responsible for instantiating and configuring [EasyExtension] instances and their contributed child extensions.
 *
 * It manages:
 * - Registering the root [EasyExtension] container on target Gradle objects (such as `Settings` or `Project`).
 * - Discovering and instantiating modular [EasyPluginExtension] child extensions provided via [PluginRegistry].
 * - Propagating and copying configuration state from a parent [ExtensionAware] scope (e.g. from `Settings` to root `Project`)
 *   using [ExtensionCopier].
 * - Injecting extension copies to subprojects when the contributor is annotated with [ApplyToSubprojects]
 *   (mirroring [PluginRegistrar] plugin application). Currently the injection is unguarded — all
 *   registered extensions are copied to subprojects — and can be narrowed to per-extension
 *   contributors later.
 *
 * Note: Extension creation is eager — done directly in `ProjectPlugin`/`SettingsPlugin.apply`
 * so `easy { }` is available immediately during script evaluation. Only plugin *behaviour*
 * (`AbstractEasyProjectPlugin.afterEnabled` / `AbstractEasySettingsPlugin.afterEnabled`) is
 * deferred via `afterEvaluate` / `settingsEvaluated` to respect `easy { }` configuration.
 */
object ExtensionRegistrar {
    /**
     * Creates and registers the root [EasyExtension] on the provided [target] [ExtensionAware] instance.
     *
     * @param target The Gradle entity hosting extensions (e.g., [org.gradle.api.Project] or [org.gradle.api.initialization.Settings]).
     * @param registry The [PluginRegistry] containing registered [EasyPluginExtension] classes to attach as child extensions.
     * @param parent An optional parent [ExtensionAware] or [CanBeCopied] instance from which existing configuration is copied.
     * @return The created and configured [EasyExtension] instance.
     */
    fun createExtension(
        target: ExtensionAware,
        registry: PluginRegistry,
        parent: ExtensionAware? = null,
    ): EasyExtension = createExtension(target.extensions, registry, parent)

    /**
     * Creates and registers the root [EasyExtension] on the given [project] and, if the project
     * is the root, injects copies into all subprojects.
     *
     * For now the injection is unconditional for all registered extensions (the plugin-side
     * guard `ApplyToSubprojects` is not yet mirrored for extensions). This ensures a plugin
     * applied to subprojects via [PluginRegistrar] always finds its `easy.*` extension in the
     * target project, with values copied from the parent `easy` (typically the root or `Settings`).
     *
     * @param project The project requesting extension creation.
     * @param registry The registry containing registered child extension types.
     * @param parent Optional parent for the root project (usually the `Settings` `easy`).
     * @return The created `EasyExtension` for `project`.
     */
    fun createExtension(
        project: Project,
        registry: PluginRegistry,
        parent: ExtensionAware? = null,
    ): EasyExtension {
        val extension = createExtension(project as ExtensionAware, registry, parent)
        injectExtensionsToSubprojects(project, registry, extension)
        return extension
    }

    private fun injectExtensionsToSubprojects(
        project: Project,
        registry: PluginRegistry,
        parentExtension: EasyExtension,
    ) {
        if (project != project.gradle.rootProject) return
        val allProjects = orderedAllProjects(project)
        for (subproject in allProjects) {
            if (subproject == project || subproject.hasEasyExtension()) continue
            createExtension(subproject as ExtensionAware, registry, parentExtension as ExtensionAware)
        }
    }

    /**
     * Creates and registers the root [EasyExtension] within the specified [extensions] container.
     *
     * In addition to creating the root extension, this method:
     * 1. Iterates over all contributed extension classes in [registry] and attaches them to [EasyExtension.extensions].
     * 2. If [parent] is supplied, extracts the source [CanBeCopied] configuration and copies its values into the new extension.
     *
     * @param extensions The [ExtensionContainer] where [EasyExtension] will be created.
     * @param registry The [PluginRegistry] holding registered child extension types.
     * @param parent An optional parent [ExtensionAware] or [CanBeCopied] instance used as the source for copying configuration.
     * @return The created and populated [EasyExtension] instance.
     */
    fun createExtension(
        extensions: ExtensionContainer,
        registry: PluginRegistry,
        parent: ExtensionAware? = null,
    ): EasyExtension {
        val extension =
            createExtensionAs(
                extensions,
                EasyExtension::class,
                DefaultEasyExtension::class,
            )
        attachContributedExtensions(extension, registry)
        copyParentIfPresent(extension, parent)
        return extension
    }

    private fun attachContributedExtensions(
        extension: EasyExtension,
        registry: PluginRegistry,
    ) {
        registry.getRegisteredExtensions().forEach { implClass ->
            val publicType = implClass.resolvePublicType()
            @Suppress("UNCHECKED_CAST")
            createExtensionAs(
                extension.extensions,
                publicType as KClass<Any>,
                implClass as KClass<Any>,
            )
        }
    }

    private fun KClass<out EasyPluginExtension>.resolvePublicType(): KClass<out EasyPluginExtension> {
        val annotation = this.annotations.filterIsInstance<PublicType>().firstOrNull() ?: return this
        val publicType = annotation.value
        require(publicType.java.isAssignableFrom(this.java)) {
            "Implementation ${this.qualifiedName} annotated with @PublicType(${publicType.qualifiedName}) must implement that type"
        }
        return publicType
    }

    private fun copyParentIfPresent(
        extension: EasyExtension,
        parent: ExtensionAware?,
    ) {
        if (parent == null) return
        val source =
            if (parent is CanBeCopied) {
                parent
            } else {
                parent.extensions.findByType(EasyExtension::class.java) as? CanBeCopied
            }
        if (source != null) {
            ExtensionCopier.copy(source, extension)
        }
    }

    /**
     * Registers an extension under its public API type while instantiating the implementation type.
     *
     * Uses the [Named] companion of [publicType] for the extension name and creates the instance as [instanceType].
     * For `@PublicType`-aliased extensions the public type is the interface from the `-api` module and the instance
     * is the implementation; for plain extensions both are the same and this call is equivalent to a direct creation.
     *
     * @param P The public extension interface type.
     * @param I The implementation class type extending [P].
     * @param extensions The target [ExtensionContainer].
     * @param publicType The public Kotlin class interface.
     * @param instanceType The concrete Kotlin class implementation.
     * @return The instantiated extension of type [P].
     */
    private fun <P : Any, I : P> createExtensionAs(
        extensions: ExtensionContainer,
        publicType: KClass<P>,
        instanceType: KClass<I>,
    ): P =
        extensions.create(
            publicType.java,
            publicType.extensionName(),
            instanceType.java,
        )
}
