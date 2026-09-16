package com.mreil.gradletest.project

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal

/** Finalizes [this] project, triggering its `afterEvaluate` callbacks. */
fun Project.evaluate() = (this as ProjectInternal).evaluate()
