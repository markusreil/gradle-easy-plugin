package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

/**
 * Settings-scope entry-point logic for the `easy` settings plugin: discovers and applies
 * contributed settings plugins and creates the settings-scope `easy` root extension.
 *
 * Declared in core; the concrete `SettingsPlugin` lives in the marker module so that [apply]'s
 * `javaClass.classLoader` resolves to that module's classloader.
 *
 * The settings root ([EasySettingsExtension]) is deliberately separate from the project root
 * ([EasyExtension]): this entry point neither pushes configuration into the root project nor applies
 * the project entry point. Project scope is set up by applying `com.mreil.easy.project` to the root
 * project.
 *
 * Open (not abstract) so tests and pre-split harnesses can apply the entry point directly; only the
 * concrete marker class is registered under a plugin ID.
 */
open class SettingsPluginEntryPoint : Plugin<Settings> {
    final override fun apply(settings: Settings) {
        val registry = settings.getPluginRegistry()
        registry.loadFromServiceLoader(javaClass.classLoader)

        ExtensionRegistrar(settings, settings.providers).createSettingsExtension(registry)

        PluginRegistrar.applyPlugins(settings, registry)
    }
}
