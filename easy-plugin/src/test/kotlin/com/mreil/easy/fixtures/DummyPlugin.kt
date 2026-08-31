package com.mreil.easy.fixtures

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class DummyPlugin : Plugin<Project> {
    override fun apply(target: Project) {}
}
