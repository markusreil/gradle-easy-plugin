package com.mreil.easy.jvm

import com.mreil.easy.PublicType
import org.gradle.api.provider.Property

/**
 * Internal implementation of [EasyJvmDefaultsSettingsExtension].
 *
 * Abstract for Gradle extension decoration via extensions.create (requires a non-final type).
 * Settings-only: it is never copied into projects, hence no `@CopyMode` annotations.
 */
@Suppress("AbstractClassCanBeInterface")
@PublicType(EasyJvmDefaultsSettingsExtension::class)
abstract class DefaultEasyJvmDefaultsSettingsExtension : EasyJvmDefaultsSettingsExtension {
    init {
        enabled.convention(true)
    }

    /**
     * Backing state for [dokkaJavadoc]; absent until the function is called. Not part of the public
     * interface (the function is the public surface).
     */
    abstract val dokkaJavadocVersion: Property<String>

    override fun dokkaJavadoc(version: String?) {
        require(version == null || version.isNotBlank()) { "Dokka version must not be blank." }
        dokkaJavadocVersion.set(version ?: EasyJvmDefaultsSettingsExtension.DEFAULT_DOKKA_VERSION)
    }
}
