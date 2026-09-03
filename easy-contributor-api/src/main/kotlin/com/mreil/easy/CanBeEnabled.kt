package com.mreil.easy

import org.gradle.api.provider.Property

/**
 * Marks an [EasyPluginExtension] as toggleable via `easy { <name> { enabled.set(...) } }`.
 *
 * The backing [enabled] property is materialized by [com.mreil.easy.ExtensionRegistrar]
 * after contributed extensions are attached via `enabled.convention(true)` (or
 * `convention(false)` when `easy.disableAllPlugins=true`). It is therefore safe to call
 * [isEnabled] / [enabled.get] after registration without a fallback.
 */
interface CanBeEnabled {
    val enabled: Property<Boolean>
}

/** Returns `true` when the extension is enabled. Requires that `enabled` has a convention/value. */
fun CanBeEnabled.isEnabled(): Boolean = enabled.get()
