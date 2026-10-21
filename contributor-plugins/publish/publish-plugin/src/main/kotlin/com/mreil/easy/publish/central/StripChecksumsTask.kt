package com.mreil.easy.publish.central

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Strips checksums from a staged Maven repository after the staging upload
 * and before the Maven Central deploy. Two uploads: (1) `maven-publish` stages
 * into the local dir, (2) JReleaser deploys the cleaned dir to Central.
 *
 * Maven Central requires the primary artifact, POM, sources JAR, Javadoc JAR and GPG
 * signatures; it generates and serves checksums itself. Gradle's `maven-publish`
 * plugin emits all four (`*.md5`, `*.sha1`, `*.sha256`, `*.sha512`) for each artifact,
 * and the `signing` plugin produces signature checksums (`*.asc.md5`, `*.asc.sha1`,
 * `*.asc.sha256`, `*.asc.sha512`) — none of which Central needs, and each of which
 * counts against the monthly publishing file quota. So this task removes every
 * checksum file and keeps only distributables + signatures. Runs per enabled project
 * against that project's staging dir (see [CentralPublishingWiring]).
 */
abstract class StripChecksumsTask : DefaultTask() {
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
        val CHECKSUM_SUFFIXES = listOf(".md5", ".sha1", ".sha256", ".sha512")
    }

    private fun shouldStrip(name: String): Boolean = CHECKSUM_SUFFIXES.any { name.endsWith(it) }
}
