package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.MavenCoordinates
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

/**
 * Functional tests for promotion-gated repositories (`promotionOnly` + `-Ppromote`).
 *
 * Plain `publish` never touches promotion-only repos; `-Ppromote` adds them on top of the
 * regular routing. Promoting a snapshot fails fast at configuration time.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class PromotionReposFuncTest {
    lateinit var project: GradleTestProject

    private fun stageReleaseFixture(
        plainDir: File,
        gatedDir: File,
    ) {
        project.configure {
            buildGradle(
                """
                import com.mreil.easy.publish.RepoRole

                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        signingEnabled.set(false)
                        mavenRepo("plain", "${plainDir.invariantSeparatorsPath}", RepoRole.RELEASE)
                        mavenRepo("gated") {
                            url.set("${gatedDir.invariantSeparatorsPath}")
                            role.set(RepoRole.RELEASE)
                            promotionOnly.set(true)
                        }
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }
    }

    /** Plain `publish` skips promotion-only repos. */
    @Test
    fun `release publish without flag skips promotion-only repos`() {
        lateinit var plainDir: File
        lateinit var gatedDir: File
        project.configure {
            plainDir = createDir("repoPlain")
            gatedDir = createDir("repoGated")
        }
        stageReleaseFixture(plainDir, gatedDir)

        project.build("publish")

        val coordinates = MavenCoordinates(name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(project).hasArtifact(plainDir, coordinates)
            softly.assertThat(project).doesNotHaveArtifact(gatedDir, coordinates)
        }
    }

    /** `-Ppromote` adds promotion-only repos and reuses the configuration cache. */
    @Test
    fun `release publish with promote flag publishes everywhere and reuses configuration cache`() {
        lateinit var plainDir: File
        lateinit var gatedDir: File
        project.configure {
            plainDir = createDir("repoPlain")
            gatedDir = createDir("repoGated")
        }
        stageReleaseFixture(plainDir, gatedDir)

        project.build("publish", "-Ppromote")

        val coordinates = MavenCoordinates(name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(project).hasArtifact(plainDir, coordinates)
            softly.assertThat(project).hasArtifact(gatedDir, coordinates)
        }

        val reused = project.build("publish", "-Ppromote")
        assertSoftly { softly ->
            softly.assertThat(reused.output).contains("Reusing configuration cache")
        }
    }

    /** Promoting a snapshot fails fast with the guard message. */
    @Test
    fun `promote flag on snapshot version fails fast`() {
        lateinit var gatedDir: File
        project.configure {
            version = "1.0.0-SNAPSHOT"
            gatedDir = createDir("repoGated")
            buildGradle(
                """
                import com.mreil.easy.publish.RepoRole

                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        signingEnabled.set(false)
                        mavenRepo("gated") {
                            url.set("${gatedDir.invariantSeparatorsPath}")
                            role.set(RepoRole.SNAPSHOT)
                            promotionOnly.set(true)
                        }
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result = project.buildAndFail("publish", "-Ppromote")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("requires a release version")
        }
    }
}
