package com.mreil.easy.publish.central

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Exercises [CheckCentralPomsTask.check] directly.
 *
 * The validation rules themselves are covered by [PomRequirementsCheckerTest];
 * this file proves the task aggregates violations per POM, throws on errors,
 * and silently skips non-file entries (e.g. directories in `pomFiles`).
 */
class CheckCentralPomsTaskTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `check passes when every pom has all required metadata`() {
        val task = createTask(pomFiles = listOf(validPom("a-1.0.0.pom"), validPom("b-1.0.0.pom")))

        assertThatCode { task.check() }.doesNotThrowAnyException()
    }

    @Test
    fun `check fails when a pom is missing url`() {
        val task =
            createTask(
                pomFiles =
                    listOf(
                        invalidPom(
                            "a-1.0.0.pom",
                            remove = "<url>https://github.com/example/demo</url>",
                        ),
                    ),
            )

        assertThatThrownBy { task.check() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("missing <url>")
    }

    @Test
    fun `check aggregates errors from every pom`() {
        val task =
            createTask(
                pomFiles =
                    listOf(
                        invalidPom(
                            "a-1.0.0.pom",
                            remove = "<url>https://github.com/example/demo</url>",
                        ),
                        invalidPom(
                            "b-1.0.0.pom",
                            remove = Regex("<scm>.*</scm>", RegexOption.DOT_MATCHES_ALL),
                        ),
                    ),
            )

        assertThatThrownBy { task.check() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("missing <url>")
            .hasMessageContaining("missing <scm>")
    }

    @Test
    fun `check warns but does not fail on developer missing email`() {
        val task =
            createTask(
                pomFiles =
                    listOf(
                        invalidPom("a-1.0.0.pom", remove = "<email>ada@example.com</email>"),
                    ),
            )

        // Email is a warning per PomRequirementsChecker — the build must not fail.
        assertThatCode { task.check() }.doesNotThrowAnyException()
    }

    @Test
    fun `check silently ignores non-file entries in pomFiles`() {
        // A directory slipped into `pomFiles` (e.g. through a misconfigured from())
        // must not crash the task — the filter passes only `isFile` entries.
        val task =
            createTask(
                pomFiles = listOf(tempDir, validPom("a-1.0.0.pom")),
            )

        assertThatCode { task.check() }.doesNotThrowAnyException()
    }

    private fun createTask(pomFiles: List<File>): CheckCentralPomsTask {
        val project = ProjectBuilder.builder().build()
        val task =
            project.tasks
                .register("checkCentralPoms", CheckCentralPomsTask::class.java) {
                    it.pomFiles.from(pomFiles)
                }.get()
        return task
    }

    private fun validPom(name: String): File = File(tempDir, name).apply { writeText(VALID_POM) }

    private fun invalidPom(
        name: String,
        remove: Any,
    ): File =
        File(tempDir, name).apply {
            writeText(
                when (remove) {
                    is String -> VALID_POM.replace(remove, "")
                    is Regex -> VALID_POM.replace(remove, "")
                    else -> error("unsupported remove type: ${remove::class}")
                },
            )
        }

    companion object {
        private val VALID_POM =
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
}
