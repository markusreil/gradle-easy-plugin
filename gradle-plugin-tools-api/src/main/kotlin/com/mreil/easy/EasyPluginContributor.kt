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

    /** Returns extension classes contributed by this provider. */
    fun pluginExtensions(): Set<KClass<out EasyPluginExtension>> = emptySet()
}
