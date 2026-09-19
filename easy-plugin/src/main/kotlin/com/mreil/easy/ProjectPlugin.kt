package com.mreil.easy

/**
 * Concrete project-scope plugin registered as `com.mreil.easy.project`.
 *
 * Declared in the marker module (not core) so [ProjectPluginEntryPoint]'s `javaClass.classLoader` is
 * this module's loader, which can see project-only plugins such as KGP.
 */
class ProjectPlugin : ProjectPluginEntryPoint()
