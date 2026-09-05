package com.mreil.utils

import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.io.FileHandler
import java.io.File

/**
 * Locates the `gradle.properties` file declaring a key.
 *
 * Directories are searched in the given priority order (e.g. project directory
 * first, then parents up to the root directory); the first file containing the
 * key wins. Files are read with Apache Commons Configuration, which preserves
 * comments, blank lines and key order on load — so a later writer (e.g. a
 * release plugin bumping `version`) round-trips the file layout.
 */
object GradlePropertiesLocator {
    /** Returns the first `gradle.properties` under [dirsInPriorityOrder] declaring [key], or null. */
    fun locateDeclaringFile(
        dirsInPriorityOrder: List<File>,
        key: String,
    ): File? =
        dirsInPriorityOrder
            .map { File(it, "gradle.properties") }
            .firstOrNull { it.isFile && declaresKey(it, key) }

    /** Returns the raw value of [key] in [file], or null when absent or unreadable. */
    fun readRawValue(
        file: File,
        key: String,
    ): String? =
        runCatching {
            val configuration = PropertiesConfiguration()
            FileHandler(configuration).load(file)
            configuration.getString(key)
        }.getOrNull()

    private fun declaresKey(
        file: File,
        key: String,
    ): Boolean =
        runCatching {
            val configuration = PropertiesConfiguration()
            FileHandler(configuration).load(file)
            configuration.containsKey(key)
        }.getOrDefault(false)
}
