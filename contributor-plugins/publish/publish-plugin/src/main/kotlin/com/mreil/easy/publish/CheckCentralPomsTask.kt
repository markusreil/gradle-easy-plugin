package com.mreil.easy.publish

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Validates generated Maven POMs against the Maven Central metadata requirements.
 *
 * Registered only on the root project and enabled only when `toMavenCentral` is set.
 * Inputs are the `GenerateMavenPom` outputs of all projects, so this runs before any
 * upload task and fails fast instead of deploying a subset of repositories first.
 * Missing developer emails are warnings; everything else fails the build.
 */
abstract class CheckCentralPomsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pomFiles: ConfigurableFileCollection

    init {
        group = "verification"
        description = "Validates generated POMs against Maven Central requirements (fails on missing metadata)"
    }

    @TaskAction
    fun check() {
        // Inputs are GenerateMavenPom destinations by construction (`pom-default.xml`);
        // no extension filtering - any generated file is validated.
        val files = pomFiles.files.filter { it.isFile }.sorted()
        if (files.isEmpty()) {
            throw GradleException(
                "checkCentralPoms found no POM files - ensure publications exist " +
                    "and generatePom tasks ran before this task.",
            )
        }
        val failures = mutableListOf<String>()
        files.forEach { file ->
            val violation = PomRequirementsChecker.check(file)
            violation.warnings.forEach { logger.warn("{}: {}", file.name, it) }
            if (violation.errors.isNotEmpty()) {
                failures.add("${file.invariantSeparatorsPath}: ${violation.errors.joinToString(", ")}")
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException(
                "Maven Central POM requirements not met:\n${failures.joinToString("\n")}\n" +
                    "Fix codemeta.json (run generateCodemeta for placeholders) and re-run.",
            )
        }
    }
}
