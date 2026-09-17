package com.mreil.easy.codemeta

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CodemetaJsonWriteTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `write terminates the file with a newline`() {
        val file = tempDir.resolve("codemeta.json").toFile()
        val codemeta = Codemeta(name = "test", description = "test", version = "1.0.0")

        CodemetaJson.write(file, codemeta)

        assertSoftly { softly ->
            softly.assertThat(file.readText()).endsWith("\n")
            softly.assertThat(CodemetaJson.read(file)).isEqualTo(codemeta)
        }
    }
}
