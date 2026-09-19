package com.mreil.easy

/**
 * Concrete settings-scope plugin registered as `com.mreil.easy.settings`.
 *
 * Declared in the marker module (not core) so [SettingsPluginEntryPoint]'s `javaClass.classLoader`
 * is this module's loader.
 */
class SettingsPlugin : SettingsPluginEntryPoint()
