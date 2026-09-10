package com.mreil.easy.vcs

import com.mreil.easy.EasyPluginContributor
import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.KClass

/** Contributor that provides [EasyVcsPlugin] via ServiceLoader. */
class EasyVcsContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(EasyVcsPlugin::class)

    override fun pluginExtensions() = setOf(DefaultEasyVcsExtension::class)
}
