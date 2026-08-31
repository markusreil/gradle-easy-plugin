package com.mreil.easy.fixtures

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Test fixture project plugin that registers a dummy task. */
@Suppress("unused")
class DummyProjectPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register("dummyTask") { task ->
            task.doLast { println("DUMMY_APPLIED") }
        }
    }
}
