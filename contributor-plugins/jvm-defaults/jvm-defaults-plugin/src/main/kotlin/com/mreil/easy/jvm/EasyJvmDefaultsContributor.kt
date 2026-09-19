package com.mreil.easy.jvm

import com.mreil.easy.EasyPluginContributor
import com.mreil.easy.jvm.kotlin.EasyJvmDefaultsKotlinPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.KClass

/** Contributor that provides [EasyJvmDefaultsPlugin] via ServiceLoader. */
class EasyJvmDefaultsContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> =
        setOf(EasyJvmDefaultsPlugin::class, EasyJvmDefaultsKotlinPlugin::class)

    override fun pluginExtensions() = setOf(DefaultEasyJvmDefaultsExtension::class)
}
