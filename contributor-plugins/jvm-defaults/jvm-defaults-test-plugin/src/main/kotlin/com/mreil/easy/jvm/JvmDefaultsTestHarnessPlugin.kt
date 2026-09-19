package com.mreil.easy.jvm

import com.mreil.easy.ProjectPluginEntryPoint
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Test harness plugin for `jvm-defaults`.
 *
 * Applies the core [ProjectPluginEntryPoint]; the contributed [EasyJvmDefaultsPlugin] is
 * then discovered via ServiceLoader (`EasyJvmDefaultsContributor`) and applied automatically.
 * This allows functional tests to use `withPluginClasspath()` instead of manual
 * `buildscript { classpath(files(...)) }` wiring.
 */
class JvmDefaultsTestHarnessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(ProjectPluginEntryPoint::class.java)
    }
}
