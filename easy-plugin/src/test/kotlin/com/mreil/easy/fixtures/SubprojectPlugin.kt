package com.mreil.easy.fixtures

import org.gradle.api.Plugin
import org.gradle.api.Project

class SubprojectPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.add("subprojectApplied", true)
    }
}
