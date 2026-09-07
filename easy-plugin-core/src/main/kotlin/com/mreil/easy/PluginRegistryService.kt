package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

/**
 * Shared build service implementing [PluginRegistry] via ServiceLoader discovery.
 */
@Suppress("TooManyFunctions")
abstract class PluginRegistryService :
    BuildService<BuildServiceParameters.None>,
    PluginRegistry {
    private val projectPlugins =
        java.util.Collections.synchronizedSet(mutableSetOf<KClass<out Plugin<Project>>>())
    private val settingsPlugins =
        java.util.Collections.synchronizedSet(mutableSetOf<KClass<out Plugin<Settings>>>())
    private val pluginToContributor =
        java.util.Collections.synchronizedMap(mutableMapOf<KClass<out Plugin<*>>, EasyPluginContributor>())
    private val extensions =
        java.util.Collections.synchronizedSet(mutableSetOf<KClass<out EasyPluginExtension>>())
    private val extensionToContributor =
        java.util.Collections.synchronizedMap(mutableMapOf<KClass<out EasyPluginExtension>, EasyPluginContributor>())
    private val loaded = AtomicBoolean(false)

    override fun registerProjectPlugin(pluginClass: KClass<out Plugin<Project>>) {
        projectPlugins.add(pluginClass)
    }

    override fun getProjectPlugins(): Set<KClass<out Plugin<Project>>> = synchronized(projectPlugins) { projectPlugins.toSet() }

    override fun registerSettingsPlugin(pluginClass: KClass<out Plugin<Settings>>) {
        settingsPlugins.add(pluginClass)
    }

    override fun getSettingsPlugins(): Set<KClass<out Plugin<Settings>>> = synchronized(settingsPlugins) { settingsPlugins.toSet() }

    override fun registerExtension(extensionClass: KClass<out EasyPluginExtension>) {
        extensions.add(extensionClass)
    }

    override fun getRegisteredExtensions(): Set<KClass<out EasyPluginExtension>> = synchronized(extensions) { extensions.toSet() }

    /** Returns the contributor that provided [pluginClass], or null if unknown. */
    fun getContributorFor(pluginClass: KClass<out Plugin<*>>): EasyPluginContributor? = pluginToContributor[pluginClass]

    /** Loads contributors via ServiceLoader using [classLoader]. First call wins; repeat calls are no-ops. */
    fun loadFromServiceLoader(classLoader: ClassLoader) {
        if (!loaded.compareAndSet(false, true)) return
        synchronized(this) {
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
                throw IllegalStateException("Failed to load EasyPluginContributor services: ${e.message}", e)
            }
        }
    }
}
