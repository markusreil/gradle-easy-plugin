package com.mreil.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GradlePropertiesTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `subdir file wins over root`() {
        val root = File(tempDir, "root").apply { mkdirs() }
        val child = File(root, "child").apply { mkdirs() }
        File(root, "gradle.properties").writeText("version=1.0.0\n")
        File(child, "gradle.properties").writeText("version=2.0.0\n")

        val found = GradleProperties.locateDeclaringFile(listOf(child, root), "version")

        assertThat(found).isEqualTo(File(child, "gradle.properties"))
    }

    @Test
    fun `falls back to root when subdir omits key`() {
        val root = File(tempDir, "root").apply { mkdirs() }
        val child = File(root, "child").apply { mkdirs() }
        File(root, "gradle.properties").writeText("version=1.0.0\n")
        File(child, "gradle.properties").writeText("group=com.example\n")

        val found = GradleProperties.locateDeclaringFile(listOf(child, root), "version")

        assertThat(found).isEqualTo(File(root, "gradle.properties"))
    }

    @Test
    fun `returns null when no file declares key`() {
        val root = File(tempDir, "root").apply { mkdirs() }
        File(root, "gradle.properties").writeText("group=com.example\n")

        val found = GradleProperties.locateDeclaringFile(listOf(root), "version")

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

        assertThat(GradleProperties.locateDeclaringFile(listOf(root), "version"))
            .isEqualTo(File(root, "gradle.properties"))
        assertThat(GradleProperties.readRawValue(File(root, "gradle.properties"), "version"))
            .isEqualTo("1.0.0")
        assertThat(GradleProperties.readRawValue(File(root, "gradle.properties"), "group"))
            .isEqualTo("com.example")
    }

    @Test
    fun `writeValue rewrites only the changed key and preserves unrelated lines`() {
        val file =
            File(tempDir, "gradle.properties").apply {
                writeText(
                    "# header\n" +
                        "version  =  1.0.0-SNAPSHOT  \n" +
                        "repo = https://example.com/a:b\n" +
                        "list = a,b,c\n" +
                        "foo = bar\n",
                )
            }

        val wrote = GradleProperties.writeValue(file, "version", "1.0.0")

        assertThat(wrote).isTrue()
        assertThat(file.readText()).isEqualTo(
            "# header\n" +
                "version=1.0.0\n" +
                "repo = https://example.com/a:b\n" +
                "list = a,b,c\n" +
                "foo = bar\n",
        )
    }

    @Test
    fun `writeValue returns false and leaves the file untouched when value already set`() {
        val file =
            File(tempDir, "gradle.properties").apply {
                writeText("# header\nversion  =  1.0.0\n")
            }

        val wrote = GradleProperties.writeValue(file, "version", "1.0.0")

        assertThat(wrote).isFalse()
        assertThat(file.readText()).isEqualTo("# header\nversion  =  1.0.0\n")
    }

    @Test
    fun `writeValue appends the key when absent, preserving the rest`() {
        val file =
            File(tempDir, "gradle.properties").apply {
                writeText("# header comment\n\ngroup=com.example\nversion=1.0.0\n")
            }

        GradleProperties.writeValue(file, "version", "2.0.0")

        assertThat(file.readText()).isEqualTo("# header comment\n\ngroup=com.example\nversion=2.0.0\n")
    }

    @Test
    fun `writeValue appends a new key when absent from the file`() {
        val file = File(tempDir, "gradle.properties").apply { writeText("group=com.example\n") }

        val wrote = GradleProperties.writeValue(file, "version", "1.0.0")

        assertThat(wrote).isTrue()
        assertThat(file.readText()).isEqualTo("group=com.example\nversion=1.0.0\n")
    }

    @Test
    fun `writeValue creates the file with just the key when missing`() {
        val file = File(tempDir, "gradle.properties")

        val wrote = GradleProperties.writeValue(file, "version", "1.0.0")

        assertThat(wrote).isTrue()
        assertThat(file.readText()).isEqualTo("version=1.0.0\n")
    }

    @Test
    fun `readRawValue returns whole value when it contains commas`() {
        val file = File(tempDir, "gradle.properties").apply { writeText("list = a,b,c\n") }

        assertThat(GradleProperties.readRawValue(file, "list")).isEqualTo("a,b,c")
    }
}
