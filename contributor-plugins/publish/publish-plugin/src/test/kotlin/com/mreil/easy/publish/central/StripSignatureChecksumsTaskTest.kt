package com.mreil.easy.publish.central

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StripSignatureChecksumsTaskTest {
    @TempDir
    lateinit var stagingDir: File

    @Test
    fun `strip removes signature checksums and plain sha256 sha512 but keeps md5 and sha1`() {
        val expectedRemaining =
            setOf(
                "foo.jar",
                "foo.jar.asc",
                "foo.jar.md5",
                "foo.jar.sha1",
                "foo.pom",
                "foo.pom.md5",
                "foo.pom.sha1",
            )
        createStagedFiles(
            expectedRemaining +
                setOf(
                    "foo.jar.sha256",
                    "foo.jar.sha512",
                    "foo.jar.asc.md5",
                    "foo.jar.asc.sha1",
                    "foo.jar.asc.sha256",
                    "foo.jar.asc.sha512",
                    "foo.pom.sha256",
                    "foo.pom.sha512",
                ),
        )

        runStrip()

        assertThatRemainingFilesAreExactly(expectedRemaining)
    }

    @Test
    fun `strip preserves unrelated files`() {
        val expectedRemaining =
            setOf(
                "bar.jar",
                "bar.jar.md5",
                "bar.jar.sha1",
                "license.txt",
            )
        createStagedFiles(
            expectedRemaining +
                setOf(
                    "bar.jar.sha256",
                    "bar.jar.sha512",
                ),
        )

        runStrip()

        assertThatRemainingFilesAreExactly(expectedRemaining)
    }

    @Test
    fun `strip does nothing when staging dir does not exist`() {
        val project = ProjectBuilder.builder().build()
        val nonexistentDir = File(stagingDir, "nonexistent")
        val task =
            project.tasks.create("stripSignatureChecksums", StripSignatureChecksumsTask::class.java) {
                it.stagingDir.set(nonexistentDir)
            }

        // Should not throw.
        task.strip()
    }

    @Test
    fun `strip does nothing when staging dir is empty`() {
        runStrip()

        assertThat(stagingDir.listFiles()).isEmpty()
    }

    @Test
    fun `strip handles deeply nested staged artifacts`() {
        val subDir = File(stagingDir, "com/example/foo/1.0.0")
        subDir.mkdirs()
        val expectedRemaining =
            setOf(
                "foo-1.0.0.jar",
                "foo-1.0.0.jar.md5",
                "foo-1.0.0.jar.sha1",
                "foo-1.0.0.jar.asc",
            )
        (
            expectedRemaining +
                setOf(
                    "foo-1.0.0.jar.sha256",
                    "foo-1.0.0.jar.sha512",
                    "foo-1.0.0.jar.asc",
                    "foo-1.0.0.jar.asc.md5",
                    "foo-1.0.0.jar.asc.sha1",
                    "foo-1.0.0.jar.asc.sha256",
                    "foo-1.0.0.jar.asc.sha512",
                )
        ).forEach { File(subDir, it).writeText("content") }

        runStrip()

        assertThatRemainingFilesIn(subDir, expectedRemaining)
    }

    private fun createStagedFiles(names: Set<String>) {
        names.forEach { File(stagingDir, it).writeText("content") }
    }

    private fun runStrip() {
        val project = ProjectBuilder.builder().build()
        val task =
            project.tasks.create("stripSignatureChecksums", StripSignatureChecksumsTask::class.java) {
                it.stagingDir.set(stagingDir)
            }
        task.strip()
    }

    private fun assertThatRemainingFilesAreExactly(expected: Set<String>) {
        assertThatRemainingFilesIn(stagingDir, expected)
    }

    private fun assertThatRemainingFilesIn(
        dir: File,
        expected: Set<String>,
    ) {
        val actual =
            dir
                .listFiles()!!
                .filter { it.isFile }
                .map { it.name }
                .toSet()
        assertThat(actual).isEqualTo(expected)
    }
}
