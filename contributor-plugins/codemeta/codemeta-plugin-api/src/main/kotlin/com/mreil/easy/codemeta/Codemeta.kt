package com.mreil.easy.codemeta

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * CodeMeta POJO mapped via kotlinx.serialization.
 *
 * JSON-LD fields `@context` and `@type` use [SerialName] for weird names.
 * Unknown fields are ignored via config; nulls are omitted via config (`explicitNulls = false`).
 * Covers the easily-supportable CodeMeta terms (schema.org Software/Thing/Person + CodeMeta terms).
 * Complex graph nodes (MediaObject, DataFeed, Review, Role) are omitted; URL/text projections use String.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Codemeta(
    @SerialName("@context")
    val context: String = "https://doi.org/10.5063/schema/codemeta-2.0",
    @SerialName("@type")
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
    @JsonNames("contIntegration")
    val continuousIntegration: String? = null,
    @JsonNames("embargoDate")
    val embargoEndDate: String? = null,
)

@Serializable
data class Person(
    @SerialName("@type")
    val type: String = "Person",
    val givenName: String? = null,
    val familyName: String? = null,
    val name: String? = null,
    val email: String? = null,
    val affiliation: String? = null,
    val identifier: String? = null,
    val address: String? = null,
)
