package com.mreil.easy.release

import com.mreil.easy.EasyPluginContributor
import org.gradle.api.Plugin
import org.gradle.api.Project
import kotlin.reflect.KClass

class EasyReleaseContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(EasyReleasePlugin::class)

    override fun pluginExtensions() = setOf(DefaultEasyReleaseExtension::class)
}
