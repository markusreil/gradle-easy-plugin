package com.mreil.easy.codemeta

import com.mreil.easy.isExtensionEnabled
import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Public entry point for lazy codemeta lookup.
 *
 * Consumers only need to pass a [Project] instance. The raw file location
 * and parsing are hidden inside the plugin's [CodemetaService].
 */
object EasyCodemeta {
    /**
     * Lazily reads `codemeta.json` from the root project directory.
     *
     * Fails if the plugin is not enabled (`easy { codemeta {} }`).
     */
    fun of(project: Project): Provider<Codemeta> {
        if (!project.isExtensionEnabled(EasyCodemetaExtension::class)) {
            error("EasyCodemeta plugin is not enabled - add `easy { codemeta {} }` to enable it")
        }
        // Service is registered by EasyCodemetaPlugin; lookup is lazy via Provider
        val serviceProvider =
            project.gradle.sharedServices.registrations
                .findByName("codemeta")
                ?.service
                ?: error("CodemetaService not registered - is the codemeta plugin applied?")

        @Suppress("UNCHECKED_CAST")
        val codemetaService = serviceProvider.get() as CodemetaService
        return codemetaService.codemeta
    }
}
