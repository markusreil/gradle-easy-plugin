package com.mreil.easy

/**
 * Lifecycle for plugins that want eager [init] and lazy [afterEnabled] handling.
 *
 * Implementations are typically [AbstractEasyProjectPlugin] or [AbstractEasySettingsPlugin],
 * which call [init] eagerly during [org.gradle.api.Plugin.apply] and [afterEnabled] only when
 * the extension referenced by [EnabledBy] is enabled. This hides the [CanBeEnabled.enabled]
 * wiring from plugin authors.
 */
interface PluginLifecycle<T> {
    fun init(target: T)

    fun afterEnabled(target: T)
}
