package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.MavenCoordinates
import com.mreil.gradletest.project.assertj.assertSoftly
import com.mreil.gradletest.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Without Codemeta, `url`/`scm` are omitted from the POM rather than invented.
 *
 * For the central path, EasyJreleaserPlugin refuses to wire at all when codemeta
 * is missing — see `central wiring is skipped and logged when codemeta is missing`.
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
                        signingEnabled.set(false)
                        toMavenStaging()
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        // publishToMavenCentral does not exist when toMavenCentral() is not requested
        // (EasyJreleaserPlugin now skips wiring entirely), so no -x is needed.
        project.build("publish", "--info")

        val stagedPom = project.mavenArtifact(project.file("build/stagingRepo"), MavenCoordinates(name = rootName, extension = "pom"))
        assertSoftly { softly ->
            softly.assertThat(stagedPom).exists()
            val text = stagedPom.readText()
            softly.assertThat(text).doesNotContain("<url>")
            softly.assertThat(text).doesNotContain("<scm>")
            softly.assertThat(text).doesNotContain("gradle-easy-plugin")
        }
    }

    /** Central wiring is skipped when codemeta is not enabled: no `checkCentralPoms`,
     *  no `publishToMavenCentral`, no `generateJreleaserConfig`, and the lifecycle log
     *  explains why. The previous behaviour (POM validation catches the missing
     *  metadata) is unreachable now: EasyJreleaserPlugin refuses to wire at all
     *  without codemeta. */
    @Test
    fun `central wiring is skipped and logged when codemeta is missing`() {
        val probe =
            probeTask("verifyCentralWiringSkipped") {
                taskExists("HAS_CHECK_CENTRAL_POMS", "checkCentralPoms", expected = false)
                taskExists("HAS_PUBLISH_TO_MAVEN_CENTRAL", "publishToMavenCentral", expected = false)
                taskExists("HAS_GENERATE_JRELEASER_CONFIG", "generateJreleaserConfig", expected = false)
            }
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
                        signingEnabled.set(false)
                        toMavenCentral()
                        toMavenStaging()
                    }
                }
                ${probe.script()}
                """.trimIndent(),
            )
            javaSource()
        }

        val result = project.build("verifyCentralWiringSkipped", "--info")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("codemeta extension is required")
            probe.assertOutput(softly, result.output)
        }
    }
}
