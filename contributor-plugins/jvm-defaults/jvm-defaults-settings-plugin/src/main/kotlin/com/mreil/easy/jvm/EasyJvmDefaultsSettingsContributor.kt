package com.mreil.easy.jvm

import com.mreil.easy.EasyPluginContributor
import com.mreil.easy.jvm.kotlin.DokkaJavadocSettingsPlugin
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import kotlin.reflect.KClass

/** Settings-scope contributor: the Dokka Javadoc settings plugin and its settings extension. */
class EasyJvmDefaultsSettingsContributor : EasyPluginContributor {
    override fun settingsPlugins(): Set<KClass<out Plugin<Settings>>> = setOf(DokkaJavadocSettingsPlugin::class)

    override fun settingsExtensions() = setOf(DefaultEasyJvmDefaultsSettingsExtension::class)
}
