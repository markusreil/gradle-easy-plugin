package com.mreil.easy.semver

import com.mreil.easy.ProjectPluginEntryPoint
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Test harness plugin for `semver-plugin`.
 *
 * Applies the core [ProjectPluginEntryPoint]; the contributed [EasySemverPlugin] is then
 * discovered via ServiceLoader (`EasySemverContributor`) and applied automatically.
 */
class SemverTestHarnessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(ProjectPluginEntryPoint::class.java)
    }
}
