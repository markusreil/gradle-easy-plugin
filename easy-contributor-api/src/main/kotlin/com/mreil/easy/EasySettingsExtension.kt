package com.mreil.easy

import org.gradle.api.plugins.ExtensionAware

/**
 * Top-level Gradle extension registered under the name `"easy"` on [org.gradle.api.initialization.Settings].
 *
 * It is the settings-scope root counterpart of [EasyExtension]: configuration made here applies to the settings build
 * itself and is not pushed into projects. Because it implements [ExtensionAware], child extensions may be attached
 * later (e.g. settings-scoped contributor configuration); for now the settings root is empty.
 *
 * ### Differences from [EasyExtension]:
 * - **Scope**: [EasySettingsExtension] is scoped to the settings build and never propagated to projects, whereas
 *   [EasyExtension] is the project root DSL extension.
 * - **Container Role**: both are [ExtensionAware] containers, but [EasySettingsExtension] does not participate in
 *   parent-to-child configuration copying.
 */
interface EasySettingsExtension : ExtensionAware {
    companion object : Named {
        override val name: String = "easy"
    }
}
