package com.mreil.easy.test.support

import java.io.File
import java.util.Properties

/** Helper function that loads a Gradle property by name from `gradle.properties`. */
@Suppress("NestedBlockDepth")
fun loadGradleProperty(propertyName: String): String {
    val properties = Properties()
    val searchRoots =
        listOfNotNull(
            System.getProperty("user.dir")?.let { File(it) },
            File(".").canonicalFile,
            File(
                PluginTestUtils::class.java.protectionDomain.codeSource.location
                    .toURI(),
            ).canonicalFile,
        )
    for (root in searchRoots) {
        generateSequence(root.canonicalFile) { it.parentFile }
            .forEach { dir ->
                val file = File(dir, "gradle.properties")
                if (file.exists()) {
                    file.inputStream().use { properties.load(it) }
                    properties
                        .getProperty(propertyName)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { return it }
                }
            }
    }
    error("Failed to load property $propertyName")
}

/** Loads the settings plugin ID from `gradle.properties`. */
fun loadSettingsPluginId(): String = loadGradleProperty("plugin.settings")

/** Object holder for test utils class reference. */
object PluginTestUtils
