package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import kotlin.reflect.KClass

/**
 * Shared build service implementing [PluginRegistry] via ServiceLoader discovery.
 */
abstract class PluginRegistryService :
    BuildService<BuildServiceParameters.None>,
    PluginRegistry {
    private val projectPlugins = mutableSetOf<KClass<out Plugin<Project>>>()
    private val settingsPlugins = mutableSetOf<KClass<out Plugin<Settings>>>()
    private val pluginToContributor = mutableMapOf<KClass<out Plugin<*>>, EasyPluginContributor>()
    private val extensions = mutableSetOf<KClass<out EasyPluginExtension>>()
    private val extensionToContributor = mutableMapOf<KClass<out EasyPluginExtension>, EasyPluginContributor>()

    override fun registerProjectPlugin(pluginClass: KClass<out Plugin<Project>>) {
        projectPlugins.add(pluginClass)
    }

    override fun getProjectPlugins(): Set<KClass<out Plugin<Project>>> = projectPlugins.toSet()

    override fun registerSettingsPlugin(pluginClass: KClass<out Plugin<Settings>>) {
        settingsPlugins.add(pluginClass)
    }

    override fun getSettingsPlugins(): Set<KClass<out Plugin<Settings>>> = settingsPlugins.toSet()

    override fun registerExtension(extensionClass: KClass<out EasyPluginExtension>) {
        extensions.add(extensionClass)
    }

    override fun getRegisteredExtensions(): Set<KClass<out EasyPluginExtension>> = extensions.toSet()

    /** Returns the contributor that provided [pluginClass], or null if unknown. */
    fun getContributorFor(pluginClass: KClass<out Plugin<*>>): EasyPluginContributor? = pluginToContributor[pluginClass]

    /** Returns the contributor that provided [extensionClass], or null if unknown. */
    fun getContributorForExtension(extensionClass: KClass<out EasyPluginExtension>): EasyPluginContributor? =
        extensionToContributor[extensionClass]

    /** Loads contributors via ServiceLoader using [classLoader]. */
    fun loadFromServiceLoader(classLoader: ClassLoader) {
        try {
            ServiceLoader
                .load(EasyPluginContributor::class.java, classLoader)
                .forEach { contributor ->
                    contributor.projectPlugins().forEach {
                        registerProjectPlugin(it)
                        pluginToContributor[it] = contributor
                    }
                    contributor.settingsPlugins().forEach {
                        registerSettingsPlugin(it)
                        pluginToContributor[it] = contributor
                    }
                    contributor.pluginExtensions().forEach {
                        registerExtension(it)
                        extensionToContributor[it] = contributor
                    }
                }
        } catch (e: ServiceConfigurationError) {
            error { "Failed to load EasyPluginContributor services: ${e.message}" }
        }
    }
}
