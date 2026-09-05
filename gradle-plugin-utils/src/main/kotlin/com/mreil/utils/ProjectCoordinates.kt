package com.mreil.utils

import org.gradle.api.Project

/**
 * Returns true when the value is set (neither null, empty nor Gradle's `"unspecified"` default).
 */
fun String?.isSpecified(): Boolean = !isNullOrEmpty() && this != "unspecified"

/** Returns true when the project has a usable `group`. */
fun Project.hasGroup(): Boolean = group.toString().isSpecified()

/** Returns true when the project has a usable `version`. */
fun Project.hasVersion(): Boolean = version.toString().isSpecified()
