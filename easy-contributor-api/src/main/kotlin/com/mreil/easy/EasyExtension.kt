package com.mreil.easy

import org.gradle.api.plugins.ExtensionAware

/**
 * Top-level Gradle extension registered under the name `"easy"` on both [org.gradle.api.initialization.Settings]
 * and [org.gradle.api.Project].
 *
 * It serves as the primary configuration entry point for the Easy plugin suite. Because it implements [ExtensionAware],
 * it also acts as the parent container for modular sub-extensions contributed by plugins (which implement [EasyPluginExtension]).
 *
 * ### Differences from [EasyPluginExtension]:
 * - **Scope**: [EasyExtension] is the root DSL extension (`easy { ... }`), whereas [EasyPluginExtension] defines modular child
 *   extensions attached nested inside [EasyExtension.extensions] (e.g. `easy.publish { ... }`).
 * - **Container Role**: [EasyExtension] is [ExtensionAware] and hosts child extensions, while [EasyPluginExtension] instances
 *   represent individual feature or contributor configurations.
 */
interface EasyExtension :
    ExtensionAware,
    CanBeCopied {
    companion object : Named {
        override val name: String = "easy"
    }
}
