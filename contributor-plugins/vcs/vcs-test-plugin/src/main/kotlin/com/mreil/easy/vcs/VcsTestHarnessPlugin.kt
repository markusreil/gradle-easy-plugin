package com.mreil.easy.vcs

import com.mreil.easy.ProjectPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Test harness plugin for `vcs-plugin`.
 *
 * Applies the core [ProjectPlugin]; the contributed [EasyVcsPlugin] is then
 * discovered via ServiceLoader (`EasyVcsContributor`) and applied automatically.
 */
class VcsTestHarnessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(ProjectPlugin::class.java)
    }
}
