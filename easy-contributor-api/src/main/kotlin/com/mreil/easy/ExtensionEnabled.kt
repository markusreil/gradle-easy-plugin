package com.mreil.easy

import org.gradle.api.plugins.ExtensionAware
import kotlin.reflect.KClass

/**
 * Checks whether the extension gated by [EnabledBy] is enabled.
 *
 * Resolves the `easy` root on this [ExtensionAware] (Project or Settings) by type — either the
 * project root [EasyExtension] or the settings root [EasySettingsExtension] — then the child
 * extension by type via [org.gradle.api.plugins.ExtensionContainer.findByType], and evaluates
 * [CanBeEnabled.isEnabled]. Returns `false` when `easy` or the child is absent.
 */
@Suppress("ReturnCount")
fun ExtensionAware.isExtensionEnabled(extensionClass: KClass<out EasyPluginExtension>): Boolean {
    val easy = findEasyRoot() ?: return false
    val ext = easy.extensions.findByType(extensionClass.java) ?: return false
    val holder =
        ext as? CanBeEnabled ?: error(
            "Extension ${extensionClass.qualifiedName} referenced by @EnabledBy must implement CanBeEnabled",
        )
    return holder.isEnabled()
}

private fun ExtensionAware.findEasyRoot(): ExtensionAware? =
    extensions.findByType(EasyExtension::class.java)
        ?: extensions.findByType(EasySettingsExtension::class.java)
