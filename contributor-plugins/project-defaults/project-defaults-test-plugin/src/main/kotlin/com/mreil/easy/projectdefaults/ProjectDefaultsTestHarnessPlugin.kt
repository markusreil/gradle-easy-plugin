package com.mreil.easy.projectdefaults

import com.mreil.easy.ProjectPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Test harness plugin for `project-defaults-plugin`.
 *
 * Applies the core [ProjectPlugin]; the contributed [EasyProjectDefaultsPlugin] is then
 * discovered via ServiceLoader (`EasyProjectDefaultsContributor`) and applied automatically.
 */
class ProjectDefaultsTestHarnessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(ProjectPlugin::class.java)
    }
}
