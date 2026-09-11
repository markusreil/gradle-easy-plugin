package com.mreil.easy.vcs

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Public entry point for lazy vcs lookup.
 *
 * Consumers only need to pass a [Project] instance. The git interaction is
 * hidden inside the plugin's [VcsService].
 */
object EasyVcs {
    /**
     * Lazily resolves the [VcsService] registered by the vcs plugin.
     *
     * Fails if the plugin is not enabled (`easy { vcs {} }`).
     */
    fun of(project: Project): Provider<VcsService> {
        // Service is registered by EasyVcsPlugin; lookup is lazy via Provider
        val serviceProvider =
            project.gradle.sharedServices.registrations
                .findByName("vcs")
                ?.service
                ?: error("VcsService not registered - is the vcs plugin applied?")

        @Suppress("UNCHECKED_CAST")
        return serviceProvider.map { it as VcsService }
    }
}
