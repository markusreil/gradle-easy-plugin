package com.mreil.easy.release

import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Public handle for the resolved release version.
 *
 * Registered eagerly in [EasyReleasePlugin.init] under [EasyRelease.SERVICE_NAME]. The version is
 * carried in [Params] so Gradle's configuration cache can serialize it. Listener callbacks are
 * materialized as task actions, not stored as service state.
 */
abstract class ReleaseLifecycleService : BuildService<ReleaseLifecycleService.Params> {
    interface Params : BuildServiceParameters {
        val releaseVersion: Property<String>
    }

    /**
     * Returns a lazy provider for the resolved release version.
     *
     * The value is empty when the release version has not been resolved.
     */
    fun releaseVersion(): Provider<String> = parameters.releaseVersion
}
