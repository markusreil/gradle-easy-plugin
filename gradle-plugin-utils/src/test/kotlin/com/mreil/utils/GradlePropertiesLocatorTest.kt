package com.mreil.utils

import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.io.FileHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GradlePropertiesLocatorTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `subdir file wins over root`() {
        val root = File(tempDir, "root").apply { mkdirs() }
        val child = File(root, "child").apply { mkdirs() }
        File(root, "gradle.properties").writeText("version=1.0.0\n")
        File(child, "gradle.properties").writeText("version=2.0.0\n")

        val found = GradlePropertiesLocator.locateDeclaringFile(listOf(child, root), "version")

        assertThat(found).isEqualTo(File(child, "gradle.properties"))
    }

    @Test
    fun `falls back to root when subdir omits key`() {
        val root = File(tempDir, "root").apply { mkdirs() }
        val child = File(root, "child").apply { mkdirs() }
        File(root, "gradle.properties").writeText("version=1.0.0\n")
        File(child, "gradle.properties").writeText("group=com.example\n")

        val found = GradlePropertiesLocator.locateDeclaringFile(listOf(child, root), "version")

        assertThat(found).isEqualTo(File(root, "gradle.properties"))
    }

    @Test
    fun `returns null when no file declares key`() {
        val root = File(tempDir, "root").apply { mkdirs() }
        File(root, "gradle.properties").writeText("group=com.example\n")

        val found = GradlePropertiesLocator.locateDeclaringFile(listOf(root), "version")

        assertThat(found).isNull()
    }

    @Test
    fun `ignores comments and supports separators`() {
        val root = File(tempDir, "root").apply { mkdirs() }
        File(root, "gradle.properties").writeText(
            """
            # a comment
            ! another comment
            version: 1.0.0
            group com.example
            """.trimIndent(),
        )

        assertThat(GradlePropertiesLocator.locateDeclaringFile(listOf(root), "version"))
            .isEqualTo(File(root, "gradle.properties"))
        assertThat(GradlePropertiesLocator.readRawValue(File(root, "gradle.properties"), "version"))
            .isEqualTo("1.0.0")
        assertThat(GradlePropertiesLocator.readRawValue(File(root, "gradle.properties"), "group"))
            .isEqualTo("com.example")
    }

    @Test
    fun `write round-trip preserves comments and order`() {
        val file =
            File(tempDir, "gradle.properties").apply {
                writeText("# header comment\n\ngroup=com.example\nversion=1.0.0\n")
            }

        val configuration = PropertiesConfiguration()
        FileHandler(configuration).load(file)
        configuration.setProperty("version", "2.0.0")
        FileHandler(configuration).save(file)

        assertThat(file.readText()).isEqualTo("# header comment\n\ngroup=com.example\nversion=2.0.0\n")
    }
}
