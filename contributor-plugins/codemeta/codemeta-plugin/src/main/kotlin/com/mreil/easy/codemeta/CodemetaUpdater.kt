package com.mreil.easy.codemeta

import java.io.File
import java.time.LocalDate

/**
 * Writer-side counterpart to [CodemetaService]: read-modify-write of `codemeta.json`.
 *
 * Used by release-lifecycle listeners to update codemeta properties (e.g. `version`,
 * `dateModified`) at release time. Stateless and configuration-cache safe.
 */
object CodemetaUpdater {
    fun updateVersionAndDateModified(
        file: File,
        version: String,
        dateModified: String = LocalDate.now().toString(),
    ): List<File> {
        val current = CodemetaJson.readIfExists(file) ?: return emptyList()
        val updated = current.copy(version = version, dateModified = dateModified)
        if (updated != current) CodemetaJson.write(file, updated)
        return if (updated != current) listOf(file) else emptyList()
    }
}
