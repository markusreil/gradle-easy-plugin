package com.mreil.easy.codemeta

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * CodeMeta POJO mapped via Jackson.
 *
 * JSON-LD fields `@context` and `@type` use [JsonProperty] for weird names.
 * Unknown fields are ignored via mapper config; nulls are omitted via [JsonInclude].
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Codemeta(
    @JsonProperty("@context")
    val context: String = "https://doi.org/10.5063/schema/codemeta-2.0",
    @JsonProperty("@type")
    val type: String = "SoftwareSourceCode",
    val name: String,
    val description: String,
    val version: String,
    val license: String? = null,
    val codeRepository: String? = null,
    val author: List<Person>? = null,
    val dateCreated: String? = null,
    val programmingLanguage: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Person(
    @JsonProperty("@type")
    val type: String = "Person",
    val givenName: String? = null,
    val familyName: String? = null,
    val name: String? = null,
    val email: String? = null,
)
