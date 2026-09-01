package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

/** Settings plugin that discovers and applies contributed settings plugins. */
class SettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val registry = settings.getPluginRegistry()
        registry.loadFromServiceLoader(javaClass.classLoader)

        val extension =
            ExtensionRegistrar.createExtension(
                settings,
                registry,
            )

        // Copy settings extension to the root Project.
        // beforeProject fires per-project during project evaluation, after settings.gradle.kts
        // has finished. At that point any `easy { ... }` configuration in settings has already
        // been applied, so the copy receives the configured values.
        settings.gradle.beforeProject {
            if (it == it.rootProject) {
                if (!it.hasEasyExtension()) {
                    ExtensionRegistrar.createExtension(
                        target = it,
                        registry = registry,
                        parent = extension,
                    )
                }
            }
        }

        PluginRegistrar.applyPlugins(settings, registry)
    }
}
