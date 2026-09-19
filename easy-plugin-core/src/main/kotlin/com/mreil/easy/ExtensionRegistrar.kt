package com.mreil.easy

import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.ProviderFactory
import kotlin.reflect.KClass

/**
 * Creates and configures the `easy` extension on a single [target].
 *
 * Central utility responsible for instantiating the project-scope root [EasyExtension] (with its contributed child
 * extensions) or the settings-scope root [EasySettingsExtension].
 *
 * It manages:
 * - Registering the appropriate root container ([EasyExtension] or [EasySettingsExtension]) on the target Gradle object
 *   (such as `Settings` or `Project`).
 * - Discovering and instantiating modular [EasyPluginExtension] child extensions provided via [PluginRegistry]:
 *   project-scope extensions ([PluginRegistry.getRegisteredExtensions]) under [EasyExtension], settings-scope
 *   extensions ([PluginRegistry.getSettingsExtensions]) under [EasySettingsExtension].
 * - Propagating and copying configuration state from a parent [ExtensionAware] scope (e.g. from the root `Project`
 *   to subprojects) using [ExtensionCopier].
 *
 * Single-target by construction — subproject fan-out is explicit at the call site
 * (see `ProjectPluginEntryPoint`), never hidden in here. The `easy.disableAllPlugins` kill-switch
 * is read via [providers] so configuration-cache tracking applies.
 *
 * Note: Extension creation is eager — done directly in `ProjectPluginEntryPoint`/`SettingsPluginEntryPoint.apply`
 * so `easy { }` is available immediately during script evaluation. Only plugin *behaviour*
 * (`AbstractEasyProjectPlugin.afterEnabled` / `AbstractEasySettingsPlugin.afterEnabled`) is
 * deferred via `afterEvaluate` / `settingsEvaluated` to respect `easy { }` configuration.
 */
class ExtensionRegistrar(
    private val target: ExtensionAware,
    private val providers: ProviderFactory,
) {
    /**
     * Creates and registers the root [EasyExtension] on [target].
     *
     * In addition to creating the root extension, this method:
     * 1. Attaches the project-scope contributed extension classes ([PluginRegistry.getRegisteredExtensions]) to
     *    [EasyExtension.extensions].
     * 2. If [parent] is supplied, extracts the source [CanBeCopied] configuration and copies its values into the new extension.
     *
     * @param registry The [PluginRegistry] holding registered child extension types.
     * @param parent An optional parent [ExtensionAware] or [CanBeCopied] instance used as the source for copying configuration.
     * @return The created and populated [EasyExtension] instance.
     */
    fun createExtension(
        registry: PluginRegistry,
        parent: ExtensionAware? = null,
    ): EasyExtension {
        val extension =
            createExtensionAs(
                target.extensions,
                EasyExtension::class,
                DefaultEasyExtension::class,
            )
        attachContributedExtensions(extension, registry.getRegisteredExtensions())
        applyEnabledDefaults(extension)
        copyParentIfPresent(extension, parent)
        return extension
    }

    /**
     * Creates and registers the settings-scope root [EasySettingsExtension] on [target].
     *
     * Attaches the settings-scope contributed extension classes ([PluginRegistry.getSettingsExtensions]) to the new
     * root and applies enabled conventions. Unlike [createExtension], no parent configuration is copied — the settings
     * root never propagates into projects.
     *
     * @param registry The [PluginRegistry] holding registered settings-scope extension types.
     * @return The created [EasySettingsExtension] instance.
     */
    fun createSettingsExtension(registry: PluginRegistry): EasySettingsExtension {
        val extension =
            createExtensionAs(
                target.extensions,
                EasySettingsExtension::class,
                DefaultEasySettingsExtension::class,
            )
        attachContributedExtensions(extension, registry.getSettingsExtensions())
        applyEnabledDefaults(extension)
        return extension
    }

    private fun attachContributedExtensions(
        extension: ExtensionAware,
        extensionClasses: Set<KClass<out EasyPluginExtension>>,
    ) {
        extensionClasses.forEach { implClass ->
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
        val annotation = annotations.firstOrNull { it is PublicType } as? PublicType ?: return this
        val publicType = annotation.value
        require(publicType.java.isAssignableFrom(this.java)) {
            "Implementation ${this.qualifiedName} annotated with @PublicType(${publicType.qualifiedName}) must implement that type"
        }
        return publicType
    }

    private fun applyEnabledDefaults(extension: ExtensionAware) {
        val enabledExtensions =
            extension.extensions.extensionsSchema.mapNotNull { schema ->
                (extension.extensions.findByName(schema.name) as? CanBeEnabled)?.let { schema.name to it }
            }
        enabledExtensions.forEach { (name, ext) ->
            checkNotNull(ext.enabled.orNull) {
                "Extension '$name' (${ext::class.qualifiedName}) must provide a convention for 'enabled' " +
                    "during initialization (e.g. enabled.convention(true) in init block)."
            }
        }
        val disable = providers.systemProperty("easy.disableAllPlugins").getOrElse("false").toBoolean()
        if (disable) {
            enabledExtensions.forEach { (_, ext) -> ext.enabled.convention(false) }
        }
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
        source?.let { ExtensionCopier.copy(it, extension) }
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
