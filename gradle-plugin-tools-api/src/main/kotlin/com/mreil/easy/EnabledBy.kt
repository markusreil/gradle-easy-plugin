package com.mreil.easy

import kotlin.reflect.KClass

/**
 * Links a plugin to the [EasyPluginExtension] that controls whether it is enabled.
 *
 * When a plugin class is annotated with [EnabledBy], the referenced extension must implement
 * [CanBeEnabled]. [PluginRegistrar] applies plugins eagerly; plugins that need `easy { ... }`
 * to be respected should extend [AbstractEasyProjectPlugin] or [AbstractEasySettingsPlugin],
 * which call [PluginLifecycle.init] eagerly and [PluginLifecycle.afterEnabled] lazily via
 * `afterEvaluate`/`settingsEvaluated` when [CanBeEnabled.isEnabled] is true.
 *
 * @param value the extension class that gates this plugin. Must implement [CanBeEnabled]
 * and have a companion object implementing [Named].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnabledBy(
    val value: KClass<out EasyPluginExtension>,
)
