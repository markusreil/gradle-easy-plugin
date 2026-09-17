package com.mreil.easy.codemeta

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File

/**
 * kotlinx.serialization read/write for `codemeta.json`.
 *
 * Jackson's `ACCEPT_SINGLE_VALUE_AS_ARRAY` (a single object decoding as a one-element list)
 * has no kotlinx equivalent, so [normalize] wraps non-array values for the known list-valued
 * CodeMeta terms before decoding.
 */
object CodemetaJson {
    private val listValuedTerms =
        setOf(
            "author",
            "contributor",
            "maintainer",
            "funder",
            "producer",
            "provider",
            "publisher",
            "sponsor",
            "keywords",
        )

    private val format =
        Json {
            // Jackson wrote defaults and omitted nulls (@JsonInclude(NON_NULL)); mirror that.
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    fun decode(json: String): Codemeta = format.decodeFromJsonElement(normalize(format.parseToJsonElement(json)))

    fun read(file: File): Codemeta = decode(file.readText())

    fun readIfExists(file: File): Codemeta? = file.takeIf { it.exists() }?.let(::read)

    fun write(
        file: File,
        codemeta: Codemeta,
    ) {
        file.writeText(format.encodeToString(codemeta))
    }

    private fun normalize(element: JsonElement): JsonElement =
        if (element !is JsonObject) {
            element
        } else {
            JsonObject(
                element.mapValues { (key, value) -> if (key in listValuedTerms) asArray(value) else value },
            )
        }

    private fun asArray(value: JsonElement): JsonElement = if (value is JsonArray || value is JsonNull) value else JsonArray(listOf(value))
}
