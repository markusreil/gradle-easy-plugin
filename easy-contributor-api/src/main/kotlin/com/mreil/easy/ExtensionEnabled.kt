package com.mreil.easy

import org.gradle.api.plugins.ExtensionAware
import kotlin.reflect.KClass

/**
 * Checks whether the extension gated by [EnabledBy] is enabled.
 *
 * Looks up `easy` on this [ExtensionAware] (Project or Settings) by type, then the child
 * extension by type via [org.gradle.api.plugins.ExtensionContainer.findByType], and evaluates
 * [CanBeEnabled.isEnabled]. Returns `false` when `easy` or the child is absent.
 */
@Suppress("ReturnCount")
fun ExtensionAware.isExtensionEnabled(extensionClass: KClass<out EasyPluginExtension>): Boolean {
    val easy = extensions.findByType(EasyExtension::class.java) as? ExtensionAware ?: return false
    val ext = easy.extensions.findByType(extensionClass.java) ?: return false
    val holder =
        ext as? CanBeEnabled ?: error(
            "Extension ${extensionClass.qualifiedName} referenced by @EnabledBy must implement CanBeEnabled",
        )
    return holder.isEnabled()
}
