package com.mreil.easy.codemeta

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Hidden storage for the parsed `codemeta.json`.
 *
 * Registered by [EasyCodemetaPlugin] as a shared service, not exposed via public extension.
 * Consumers access via [EasyCodemeta.of] which delegates to this service.
 */
abstract class CodemetaService : BuildService<CodemetaService.Params> {
    interface Params : BuildServiceParameters {
        val codemetaFile: RegularFileProperty
    }

    private val mapper =
        jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        }

    val codemeta: Provider<Codemeta> =
        parameters.codemetaFile.map { file ->
            val f = file.asFile
            if (!f.exists()) {
                error("codemeta.json not found at ${f.absolutePath} - run generateCodemeta")
            }
            mapper.readValue(f)
        }
}
