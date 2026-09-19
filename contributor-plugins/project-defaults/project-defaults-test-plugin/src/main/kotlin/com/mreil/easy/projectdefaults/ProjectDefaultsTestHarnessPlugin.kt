package com.mreil.easy.projectdefaults

import com.mreil.easy.ProjectPluginEntryPoint
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Test harness plugin for `project-defaults-plugin`.
 *
 * Applies the core [ProjectPluginEntryPoint]; the contributed [EasyProjectDefaultsPlugin] is
 * then discovered via ServiceLoader (`EasyProjectDefaultsContributor`) and applied automatically.
 */
class ProjectDefaultsTestHarnessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(ProjectPluginEntryPoint::class.java)
    }
}
