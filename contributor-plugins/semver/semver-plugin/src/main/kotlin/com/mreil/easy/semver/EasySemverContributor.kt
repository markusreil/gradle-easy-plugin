package com.mreil.easy.semver

import com.mreil.easy.EasyPluginContributor
import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.KClass

/** Contributor that provides [EasySemverPlugin] via ServiceLoader. */
class EasySemverContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(EasySemverPlugin::class)

    override fun pluginExtensions() = setOf(DefaultEasySemverExtension::class)
}
