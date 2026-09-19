package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import kotlin.reflect.KClass

/**
 * SPI for discovering plugin and extension classes via ServiceLoader.
 */
interface EasyPluginContributor {
    /** Returns Project plugin classes contributed by this provider. */
    fun projectPlugins(): Set<KClass<out Plugin<Project>>> = emptySet()

    /** Returns Settings plugin classes contributed by this provider. */
    fun settingsPlugins(): Set<KClass<out Plugin<Settings>>> = emptySet()

    /** Returns project-scope extension classes contributed by this provider, attached to the project root [EasyExtension]. */
    fun pluginExtensions(): Set<KClass<out EasyPluginExtension>> = emptySet()

    /** Returns settings-scope extension classes contributed by this provider, attached to the settings root [EasySettingsExtension]. */
    fun settingsExtensions(): Set<KClass<out EasyPluginExtension>> = emptySet()
}
