package com.mreil.easy.publish

import com.mreil.easy.EasyPluginContributor
import com.mreil.easy.EasyPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.KClass

/** Contributor that provides [EasyPublishPlugin] via ServiceLoader. */
class EasyPublishContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(EasyPublishPlugin::class)

    override fun pluginExtensions(): Set<KClass<out EasyPluginExtension>> = setOf(EasyPublishExtension::class)
}
