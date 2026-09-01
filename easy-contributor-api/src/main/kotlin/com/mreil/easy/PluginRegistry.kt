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

    /** Registers an extension class. */
    fun registerExtension(extensionClass: KClass<out EasyPluginExtension>)

    /** Returns all registered extension classes. */
    fun getRegisteredExtensions(): Set<KClass<out EasyPluginExtension>>

    companion object {
        /** Shared service name for [PluginRegistry]. */
        const val NAME = "easyPluginRegistry"
    }
}
