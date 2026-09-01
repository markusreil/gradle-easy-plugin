package com.mreil.easy.jvm

import com.mreil.easy.ProjectPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Test harness plugin for `jvm-defaults`.
 *
 * Applies the core [ProjectPlugin]; the contributed [EasyJvmDefaultsPlugin] is then
 * discovered via ServiceLoader (`EasyJvmDefaultsContributor`) and applied automatically.
 * This allows functional tests to use `withPluginClasspath()` instead of manual
 * `buildscript { classpath(files(...)) }` wiring.
 */
class JvmDefaultsTestHarnessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(ProjectPlugin::class.java)
    }
}
