package com.mreil.easy.publish

import com.mreil.easy.EasyPluginContributor
import com.mreil.easy.publish.central.EasyJreleaserPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.KClass

/** Contributor that provides [EasyPublishPlugin] and [EasyJreleaserPlugin] via ServiceLoader. */
class EasyPublishContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(EasyPublishPlugin::class, EasyJreleaserPlugin::class)

    override fun pluginExtensions() = setOf(DefaultEasyPublishExtension::class)
}
