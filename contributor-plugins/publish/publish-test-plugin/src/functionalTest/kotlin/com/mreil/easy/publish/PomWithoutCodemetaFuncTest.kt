package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.MavenCoordinates
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Without Codemeta, `url`/`scm` are omitted from the POM rather than invented —
 * and reported later by `checkCentralPoms` when deploying to Maven Central.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class PomWithoutCodemetaFuncTest {
    lateinit var project: GradleTestProject

    /** Staged POM has no `url`/`scm` without Codemeta (and no hardcoded placeholder). */
    @Test
    fun `pom omits url and scm without codemeta`() {
        val rootName = project.projectDir.name
        project.configure {
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        toMavenStaging()
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        project.build("publish", "--info", "-x", "publishToMavenCentral")

        val stagedPom = project.mavenArtifact(project.file("build/stagingRepo"), MavenCoordinates(name = rootName, extension = "pom"))
        assertSoftly { softly ->
            softly.assertThat(stagedPom).exists()
            val text = stagedPom.readText()
            softly.assertThat(text).doesNotContain("<url>")
            softly.assertThat(text).doesNotContain("<scm>")
            softly.assertThat(text).doesNotContain("gradle-easy-plugin")
        }
    }

    /** Missing `url`/`scm` fail `checkCentralPoms` before upload, not at POM generation. */
    @Test
    fun `checkCentralPoms reports missing url and scm without codemeta`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        toMavenCentral()
                        toMavenStaging()
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result = project.buildAndFail("publish", "--info", "-x", "publishToMavenCentral")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("checkCentralPoms")
            softly.assertThat(result.output).contains("missing <url>")
            softly.assertThat(result.output).contains("missing <scm>")
        }
    }
}
