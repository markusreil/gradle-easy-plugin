package com.mreil.easy.test.project.assertj

/**
 * Maven coordinates of a published artifact, e.g. `MavenCoordinates(name = "demo")`
 * for the conventional `com.example:demo:1.0.0`.
 */
data class MavenCoordinates(
    val group: String = "com.example",
    val name: String,
    val version: String = "1.0.0",
    val classifier: String? = null,
    val extension: String = "jar",
)
