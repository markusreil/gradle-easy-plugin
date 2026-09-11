package com.mreil.easy.vcs

import com.mreil.easy.easyService
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
     * Never throws at call time; the returned [Provider] is absent when the
     * vcs plugin is disabled or not applied (`easy { vcs {} }`).
     */
    fun of(project: Project): Provider<VcsService> = project.easyService("vcs", EasyVcsExtension::class)
}
