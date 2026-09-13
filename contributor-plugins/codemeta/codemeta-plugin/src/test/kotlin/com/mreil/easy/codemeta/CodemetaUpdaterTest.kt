package com.mreil.easy.codemeta

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.LocalDate

class CodemetaUpdaterTest {
    @TempDir
    lateinit var tempDir: Path

    private val mapper =
        jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        }

    private fun codemetaFile(): File = tempDir.resolve("codemeta.json").toFile()

    private fun writeInitialCodemeta(file: File) {
        file.writeText(
            """
            {
              "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
              "@type": "SoftwareSourceCode",
              "name": "test",
              "description": "test",
              "version": "1.0.0-SNAPSHOT"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `updates version and dateModified and rewrites the file`() {
        val file = codemetaFile()
        writeInitialCodemeta(file)

        val result = CodemetaUpdater.updateVersionAndDateModified(file, "2.0.0", "2026-02-02")

        val parsed: Codemeta = mapper.readValue(file)
        assertSoftly { softly ->
            softly.assertThat(result).containsExactly(file)
            softly.assertThat(parsed.version).isEqualTo("2.0.0")
            softly.assertThat(parsed.dateModified).isEqualTo("2026-02-02")
            softly.assertThat(parsed.name).isEqualTo("test")
            softly.assertThat(parsed.description).isEqualTo("test")
        }
    }

    @Test
    fun `returns empty list and does not rewrite when nothing changed`() {
        val file = codemetaFile()
        writeInitialCodemeta(file)

        CodemetaUpdater.updateVersionAndDateModified(file, "2.0.0", "2026-02-02")
        val before = file.readText()
        val result = CodemetaUpdater.updateVersionAndDateModified(file, "2.0.0", "2026-02-02")
        val after = file.readText()

        assertSoftly { softly ->
            softly.assertThat(result).isEmpty()
            softly.assertThat(after).isEqualTo(before)
        }
    }

    @Test
    fun `returns empty list when file does not exist`() {
        val file = codemetaFile()

        val result = CodemetaUpdater.updateVersionAndDateModified(file, "2.0.0", "2026-02-02")

        assertSoftly { softly ->
            softly.assertThat(result).isEmpty()
            softly.assertThat(file).doesNotExist()
        }
    }

    @Test
    fun `uses today as dateModified when not provided`() {
        val file = codemetaFile()
        writeInitialCodemeta(file)

        val result = CodemetaUpdater.updateVersionAndDateModified(file, "2.0.0")

        val parsed: Codemeta = mapper.readValue(file)
        assertSoftly { softly ->
            softly.assertThat(result).containsExactly(file)
            softly.assertThat(parsed.dateModified).isEqualTo(LocalDate.now().toString())
        }
    }
}
