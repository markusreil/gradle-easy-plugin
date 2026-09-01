package com.mreil.easy.publish

import com.mreil.easy.ProjectPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Test harness plugin for `publish-plugin`.
 *
 * Applies the core [ProjectPlugin]; the contributed [EasyPublishPlugin] is then
 * discovered via ServiceLoader (`EasyPublishContributor`) and applied automatically.
 * This allows functional tests to use `withPluginClasspath()` instead of manual
 * `buildscript { classpath(files(...)) }` wiring.
 */
class PublishTestHarnessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(ProjectPlugin::class.java)
    }
}
