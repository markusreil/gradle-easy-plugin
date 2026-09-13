package com.mreil.easy.codemeta

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
        val current = read(file) ?: return emptyList()
        val updated = current.copy(version = version, dateModified = dateModified)
        if (updated != current) write(file, updated)
        return if (updated != current) listOf(file) else emptyList()
    }

    private val mapper: ObjectMapper =
        jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        }

    private fun read(file: File): Codemeta? = file.takeIf { it.exists() }?.let { mapper.readValue(it) }

    private fun write(
        file: File,
        codemeta: Codemeta,
    ) {
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, codemeta)
    }
}
