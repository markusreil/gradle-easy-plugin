package com.mreil.easy

import org.gradle.api.provider.Property

/**
 * Marks an [EasyPluginExtension] as toggleable via `easy { <name> { enabled.set(...) } }`.
 *
 * The backing [enabled] property defaults to `true` via [isEnabled] (`getOrElse(true)`), so
 * extensions are active unless explicitly disabled. Registrar implementations should set
 * `enabled.convention(true)` on creation to make the default visible in Gradle properties.
 */
interface CanBeEnabled {
    val enabled: Property<Boolean>
}

/** Returns `true` when the extension is enabled, defaulting to enabled if unset. */
fun CanBeEnabled.isEnabled(): Boolean = enabled.getOrElse(true)
