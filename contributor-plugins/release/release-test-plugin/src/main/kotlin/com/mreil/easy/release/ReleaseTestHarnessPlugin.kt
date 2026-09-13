package com.mreil.easy.release

import com.mreil.easy.ProjectPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

class ReleaseTestHarnessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(ProjectPlugin::class.java)
    }
}
