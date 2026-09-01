package com.mreil.easy.fixtures

import org.gradle.api.Plugin
import org.gradle.api.Project

class OtherDummyPlugin : Plugin<Project> {
    override fun apply(target: Project) {}
}
