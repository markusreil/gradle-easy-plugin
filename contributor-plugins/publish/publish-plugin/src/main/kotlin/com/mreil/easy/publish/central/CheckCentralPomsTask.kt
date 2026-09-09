package com.mreil.easy.publish.central

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
 * Registered in every enabled project (see [CentralPublishingWiring]) with that project's
 * `GenerateMavenPom` outputs as inputs, so invalid POMs fail fast at upload time.
 * Skips silently when the project has no POM files (`onlyIf` in the wiring).
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
        // Inputs are this project's GenerateMavenPom destinations by construction
        // (`pom-default.xml`); no extension filtering - any generated file is validated.
        // Empty is unreachable via normal wiring (`onlyIf` skips first).
        val files = pomFiles.files.filter { it.isFile }.sorted()
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
