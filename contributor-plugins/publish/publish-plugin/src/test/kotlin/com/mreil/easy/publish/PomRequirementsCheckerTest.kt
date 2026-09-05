package com.mreil.easy.publish

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PomRequirementsCheckerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `valid pom has no errors or warnings`() {
        val violation = PomRequirementsChecker.check(writePom(validPom()))

        assertSoftly { softly ->
            softly.assertThat(violation.errors).isEmpty()
            softly.assertThat(violation.warnings).isEmpty()
        }
    }

    @Test
    fun `missing name description and url are errors`() {
        val violation =
            PomRequirementsChecker.check(
                writePom(
                    validPom()
                        .replace("<name>demo</name>", "")
                        .replace("<description>demo description</description>", "")
                        .replace("<url>https://github.com/example/demo</url>", ""),
                ),
            )

        assertSoftly { softly ->
            softly.assertThat(violation.errors).contains("missing <name>", "missing <description>", "missing <url>")
        }
    }

    @Test
    fun `todo values count as missing`() {
        val violation =
            PomRequirementsChecker.check(
                writePom(validPom().replace("https://github.com/example/demo", "TODO: Add codeRepository")),
            )

        assertSoftly { softly ->
            softly.assertThat(violation.errors).contains(
                "missing <url>",
                "scm missing <connection>",
                "scm missing <developerConnection>",
                "scm missing <url>",
            )
        }
    }

    @Test
    fun `missing licenses and developers are errors`() {
        val withoutLicensesAndDevelopers =
            validPom()
                .replace(Regex("<licenses>.*</licenses>", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<developers>.*</developers>", RegexOption.DOT_MATCHES_ALL), "")
        val violation = PomRequirementsChecker.check(writePom(withoutLicensesAndDevelopers))

        assertSoftly { softly ->
            softly.assertThat(violation.errors).contains("no <licenses> entries", "no <developers> entries")
        }
    }

    @Test
    fun `license without url is an error`() {
        val violation =
            PomRequirementsChecker.check(
                writePom(validPom().replace("<url>https://spdx.org/licenses/MIT</url>", "")),
            )

        assertSoftly { softly ->
            softly.assertThat(violation.errors).contains("license[0] missing <url>")
        }
    }

    @Test
    fun `developer without name is an error`() {
        val violation =
            PomRequirementsChecker.check(
                writePom(validPom().replace("<name>Ada Lovelace</name>", "")),
            )

        assertSoftly { softly ->
            softly.assertThat(violation.errors).contains("developer[0] missing <name>")
        }
    }

    @Test
    fun `developer without email is a warning only`() {
        val violation =
            PomRequirementsChecker.check(
                writePom(validPom().replace("<email>ada@example.com</email>", "")),
            )

        assertSoftly { softly ->
            softly.assertThat(violation.errors).isEmpty()
            softly.assertThat(violation.warnings).contains("developer 'Ada Lovelace' has no <email>")
        }
    }

    @Test
    fun `missing scm block is an error`() {
        val withoutScm = validPom().replace(Regex("<scm>.*</scm>", RegexOption.DOT_MATCHES_ALL), "")
        val violation = PomRequirementsChecker.check(writePom(withoutScm))

        assertSoftly { softly ->
            softly.assertThat(violation.errors).contains("missing <scm>")
        }
    }

    private fun writePom(content: String): File = File(tempDir, "demo-1.0.0.pom").apply { writeText(content) }

    private fun validPom(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>demo</artifactId>
          <version>1.0.0</version>
          <name>demo</name>
          <description>demo description</description>
          <url>https://github.com/example/demo</url>
          <licenses>
            <license>
              <name>MIT</name>
              <url>https://spdx.org/licenses/MIT</url>
            </license>
          </licenses>
          <developers>
            <developer>
              <name>Ada Lovelace</name>
              <email>ada@example.com</email>
            </developer>
          </developers>
          <scm>
            <connection>scm:git:https://github.com/example/demo</connection>
            <developerConnection>scm:git:https://github.com/example/demo</developerConnection>
            <url>https://github.com/example/demo</url>
          </scm>
        </project>
        """.trimIndent()
}
