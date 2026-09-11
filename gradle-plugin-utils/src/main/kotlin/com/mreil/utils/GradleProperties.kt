package com.mreil.utils

import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler
import org.apache.commons.configuration2.io.FileHandler
import java.io.File

/**
 * Reads and writes `gradle.properties` files while preserving their layout.
 *
 * Files are handled with Apache Commons Configuration, which keeps comments,
 * blank lines, escaping and key order on load — so an untouched key is written
 * back byte-for-byte while [writeValue] only rewrites the line of the key it
 * changes. List-delimiter parsing is disabled so values containing commas are
 * treated as single strings.
 */
object GradleProperties {
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
    ): String? = runCatching { configure(file).getString(key) }.getOrNull()

    /**
     * Writes [value] for [key] into [file], preserving unrelated lines.
     *
     * Returns false without touching the file when [key] is already set to
     * [value]; otherwise writes the new value (appending the key when absent,
     * creating the file when it does not exist) and returns true. I/O errors
     * propagate to the caller.
     */
    fun writeValue(
        file: File,
        key: String,
        value: String,
    ): Boolean {
        val configuration = configure(file)
        if (configuration.getString(key) == value) return false
        configuration.setProperty(key, value)
        configuration.layout.setSeparator(key, "=")
        file.writer().use { writer -> configuration.write(writer) }
        return true
    }

    private fun configure(file: File): PropertiesConfiguration =
        PropertiesConfiguration().apply {
            setListDelimiterHandler(DisabledListDelimiterHandler.INSTANCE)
            if (file.isFile) FileHandler(this).load(file)
        }

    private fun declaresKey(
        file: File,
        key: String,
    ): Boolean = runCatching { configure(file).containsKey(key) }.getOrDefault(false)
}
