package com.mreil.easy.publish.central

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Strips unnecessary checksums from a staged Maven repository before deploy to Maven Central.
 *
 * Maven Central only requires MD5 and SHA-1 checksums. Gradle's `maven-publish` plugin emits
 * all four (`*.md5`, `*.sha1`, `*.sha256`, `*.sha512`) for each artifact, and the `signing`
 * plugin produces signature checksums (`*.asc.md5`, `*.asc.sha1`, `*.asc.sha256`, `*.asc.sha512`).
 *
 * This task removes:
 * - All signature checksums (`*.asc.md5`, `*.asc.sha1`, `*.asc.sha256`, `*.asc.sha512`)
 * - Optional SHA-256 and SHA-512 artifact checksums (`*.sha256`, `*.sha512`)
 *
 * Required `.md5` and `.sha1` checksums are kept intact. Runs per enabled project against
 * that project's staging dir (see [CentralPublishingWiring]).
 */
abstract class StripSignatureChecksumsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stagingDir: DirectoryProperty

    @TaskAction
    fun strip() {
        val base = stagingDir.asFile.orNull ?: return
        if (!base.isDirectory) return
        base
            .walkTopDown()
            .filter { it.isFile && shouldStrip(it.name) }
            .forEach { file ->
                if (file.delete()) {
                    logger.lifecycle("Removed stale checksum {}", file.name)
                } else {
                    logger.warn("Unable to delete stale checksum {}", file.name)
                }
            }
    }

    private companion object {
        val SIGNATURE_CHECKSUM_SUFFIXES =
            listOf("md5", "sha1", "sha256", "sha512").map { ".asc.$it" }
        val EXTRA_CHECKSUM_SUFFIXES = listOf(".sha256", ".sha512")
    }

    private fun shouldStrip(name: String): Boolean =
        SIGNATURE_CHECKSUM_SUFFIXES.any { name.endsWith(it) } ||
            (EXTRA_CHECKSUM_SUFFIXES.any { name.endsWith(it) } && !name.contains(".asc."))
}
