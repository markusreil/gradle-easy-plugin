package com.mreil.easy.jvm

import com.mreil.easy.EasyPluginContributor
import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.KClass

/** Contributor that provides [JvmDefaultsPlugin] via ServiceLoader. */
class JvmDefaultsContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(JvmDefaultsPlugin::class)
}
