package com.mreil.easy.jvm

import com.mreil.easy.EasyPluginContributor
import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.KClass

/** Contributor that provides [EasyJvmDefaultsPlugin] via ServiceLoader. */
class EasyJvmDefaultsContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(EasyJvmDefaultsPlugin::class)
}
