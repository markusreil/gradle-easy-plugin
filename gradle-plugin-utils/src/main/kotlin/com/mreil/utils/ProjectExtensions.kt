package com.mreil.utils

import org.gradle.api.Project
import java.io.File

/** Returns true when this project is the root project of the build. */
fun Project.isRoot(): Boolean = this == rootProject

/**
 * Returns [Project.projectDir] followed by each ancestor directory up to and including
 * the root project directory.
 *
 * Useful for walking `gradle.properties` files from a project toward the build root,
 * e.g. with [GradleProperties.locateDeclaringFile].
 */
fun Project.propertiesDirs(): List<File> = generateSequence(projectDir) { dir -> dir.parentFile?.takeIf { dir != rootDir } }.toList()
