package com.mreil.easy.projectdefaults

import com.mreil.easy.EasyPluginContributor
import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.KClass

/** Contributor that provides [EasyProjectDefaultsPlugin] via ServiceLoader. */
class EasyProjectDefaultsContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(EasyProjectDefaultsPlugin::class)

    override fun pluginExtensions() = setOf(DefaultEasyProjectDefaultsExtension::class)
}
