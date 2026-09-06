package com.mreil.easy.codemeta

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Creates an initial `codemeta.json` with placeholder values if it does not exist.
 *
 * Fails the build after creation to force user review, avoiding surprising side effects.
 */
abstract class GenerateCodemetaTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val projectDescription: Property<String>

    @TaskAction
    fun generate() {
        val file = outputFile.get().asFile
        if (file.exists()) return
        file.parentFile.mkdirs()
        val codemeta =
            Codemeta(
                name = projectName.getOrElse("TODO: Add project name"),
                description = projectDescription.getOrElse("TODO: Add description - replace with project description"),
                version = projectVersion.getOrElse("TODO: Add version"),
                license = "https://spdx.org/licenses/MIT",
                codeRepository = "TODO: Add codeRepository - e.g. https://github.com/mreil/gradle-easy-plugin-new",
                url = "TODO: Add url - e.g. https://mreil.com/gradle-easy-plugin-new",
                issueTracker = "TODO: Add issueTracker - e.g. https://github.com/mreil/gradle-easy-plugin-new/issues",
                datePublished = "TODO: Add datePublished - e.g. 2026-01-01",
                keywords = listOf("TODO: Add keywords"),
                author =
                    listOf(
                        Person(
                            givenName = "TODO",
                            familyName = "TODO",
                            email = "TODO@example.com",
                        ),
                    ),
                programmingLanguage = "Kotlin",
            )
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, codemeta)
        throw GradleException(
            "codemeta.json was not found - created initial file at ${file.absolutePath} " +
                "with placeholder values. Please review, fill correct values, and re-run the build.",
        )
    }

    companion object {
        private val mapper: ObjectMapper =
            jacksonObjectMapper().apply {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
    }
}
