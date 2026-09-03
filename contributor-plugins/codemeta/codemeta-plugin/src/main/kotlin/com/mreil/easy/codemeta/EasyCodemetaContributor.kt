package com.mreil.easy.codemeta

import com.mreil.easy.EasyPluginContributor
import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.KClass

/** Contributor that provides [EasyCodemetaPlugin] via ServiceLoader. */
class EasyCodemetaContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(EasyCodemetaPlugin::class)

    override fun pluginExtensions() = setOf(DefaultEasyCodemetaExtension::class)
}
