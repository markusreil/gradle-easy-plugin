package com.mreil.easy.codemeta

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * CodeMeta POJO mapped via Jackson.
 *
 * JSON-LD fields `@context` and `@type` use [JsonProperty] for weird names.
 * Unknown fields are ignored via mapper config; nulls are omitted via [JsonInclude].
 * Covers the easily-supportable CodeMeta terms (schema.org Software/Thing/Person + CodeMeta terms).
 * Complex graph nodes (MediaObject, DataFeed, Review, Role) are omitted; URL/text projections use String.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Codemeta(
    @param:JsonProperty("@context")
    val context: String = "https://doi.org/10.5063/schema/codemeta-2.0",
    @param:JsonProperty("@type")
    val type: String = "SoftwareSourceCode",
    val name: String,
    val description: String,
    val version: String,
    val license: String? = null,
    val codeRepository: String? = null,
    val author: List<Person>? = null,
    val dateCreated: String? = null,
    val dateModified: String? = null,
    val datePublished: String? = null,
    val programmingLanguage: String? = null,
    val url: String? = null,
    val identifier: String? = null,
    val sameAs: String? = null,
    val relatedLink: String? = null,
    val downloadUrl: String? = null,
    val installUrl: String? = null,
    val releaseNotes: String? = null,
    val applicationCategory: String? = null,
    val applicationSubCategory: String? = null,
    val citation: String? = null,
    val copyrightHolder: String? = null,
    val copyrightYear: String? = null,
    val contributor: List<Person>? = null,
    val maintainer: List<Person>? = null,
    val editor: Person? = null,
    val funder: List<Person>? = null,
    val producer: List<Person>? = null,
    val provider: List<Person>? = null,
    val publisher: List<Person>? = null,
    val sponsor: List<Person>? = null,
    val fileFormat: String? = null,
    val fileSize: String? = null,
    val memoryRequirements: String? = null,
    val processorRequirements: String? = null,
    val storageRequirements: String? = null,
    val operatingSystem: String? = null,
    val permissions: String? = null,
    val runtimePlatform: String? = null,
    val softwareVersion: String? = null,
    val softwareHelp: String? = null,
    val targetProduct: String? = null,
    val isAccessibleForFree: Boolean? = null,
    val keywords: List<String>? = null,
    val issueTracker: String? = null,
    val readme: String? = null,
    val buildInstructions: String? = null,
    val developmentStatus: String? = null,
    val funding: String? = null,
    val referencePublication: String? = null,
    @param:JsonAlias("contIntegration")
    val continuousIntegration: String? = null,
    @param:JsonAlias("embargoDate")
    val embargoEndDate: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Person(
    @param:JsonProperty("@type")
    val type: String = "Person",
    val givenName: String? = null,
    val familyName: String? = null,
    val name: String? = null,
    val email: String? = null,
    val affiliation: String? = null,
    val identifier: String? = null,
    val address: String? = null,
)
