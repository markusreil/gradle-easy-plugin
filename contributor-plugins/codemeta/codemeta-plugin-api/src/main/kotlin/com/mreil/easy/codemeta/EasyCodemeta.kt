package com.mreil.easy.codemeta

import com.mreil.easy.easyService
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
     * Lazily resolves the [CodemetaService] registered by the codemeta plugin.
     *
     * Never throws at call time; the returned [Provider] is absent when the
     * codemeta plugin is disabled or not applied (`easy { codemeta {} }`).
     */
    private fun service(project: Project): Provider<CodemetaService> = project.easyService("codemeta", EasyCodemetaExtension::class)

    /**
     * Lazily reads `codemeta.json` from the root project directory.
     *
     * The returned [Provider] is absent when the codemeta plugin is disabled
     * or not applied (`easy { codemeta {} }`), or when the `codemeta.json`
     * file cannot be read (e.g. `generateCodemeta` has not run yet).
     */
    fun of(project: Project): Provider<Codemeta> =
        service(project)
            .map { codemeta -> runCatching { codemeta.codemeta.get() }.getOrNull() }
}
