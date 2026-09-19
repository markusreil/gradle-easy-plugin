package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import kotlin.reflect.KClass

/** Registry for plugin and extension classes discovered via [EasyPluginContributor]. */
interface PluginRegistry {
    /** Registers a Project plugin class. */
    fun registerProjectPlugin(pluginClass: KClass<out Plugin<Project>>)

    /** Returns all registered Project plugin classes. */
    fun getProjectPlugins(): Set<KClass<out Plugin<Project>>>

    /** Registers a Settings plugin class. */
    fun registerSettingsPlugin(pluginClass: KClass<out Plugin<Settings>>)

    /** Returns all registered Settings plugin classes. */
    fun getSettingsPlugins(): Set<KClass<out Plugin<Settings>>>

    /** Registers a project-scope extension class (attached to the project root [EasyExtension]). */
    fun registerExtension(extensionClass: KClass<out EasyPluginExtension>)

    /** Returns all registered project-scope extension classes. */
    fun getRegisteredExtensions(): Set<KClass<out EasyPluginExtension>>

    /** Registers a settings-scope extension class (attached to the settings root [EasySettingsExtension]). */
    fun registerSettingsExtension(extensionClass: KClass<out EasyPluginExtension>)

    /** Returns all registered settings-scope extension classes. */
    fun getSettingsExtensions(): Set<KClass<out EasyPluginExtension>>

    companion object {
        /** Project-scope service name for [PluginRegistry]. */
        const val NAME = "easyPluginRegistry"

        /**
         * Settings-scope service name for [PluginRegistry].
         *
         * Settings and project scopes live in separate plugin classloaders, so they must not share a
         * single classloader-bound [org.gradle.api.services.BuildService] instance (same FQCN, different
         * `Class` would fail on lookup). Each scope therefore registers its own `PluginRegistryService`
         * under a distinct name, keeping its own `AtomicBoolean` and ServiceLoader pass.
         */
        const val SETTINGS_NAME = "easyPluginRegistrySettings"
    }
}
