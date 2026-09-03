package com.mreil.easy.codemeta

import com.mreil.easy.ProjectPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Test harness plugin for `codemeta-plugin`.
 *
 * Applies the core [ProjectPlugin]; the contributed [EasyCodemetaPlugin] is then
 * discovered via ServiceLoader (`EasyCodemetaContributor`) and applied automatically.
 */
class CodemetaTestHarnessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(ProjectPlugin::class.java)
    }
}
