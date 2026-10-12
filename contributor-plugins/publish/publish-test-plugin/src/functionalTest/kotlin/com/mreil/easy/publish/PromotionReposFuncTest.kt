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

    /** Repos declared in the root `easy {}` block (after plugin application) reach subprojects. */
    @Test
    fun `root-declared repos publish from subprojects`() {
        lateinit var repoDir: File
        project.configure {
            settings("include(\":child\")")
            repoDir = createDir("repo")
            buildGradle(
                """
                import com.mreil.easy.publish.RepoRole

                plugins {
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        signingEnabled.set(false)
                        mavenRepo("repos", "${repoDir.invariantSeparatorsPath}", RepoRole.RELEASE)
                    }
                }
                """.trimIndent(),
            )
            createChild {
                buildGradle(
                    """
                    plugins {
                        `java-library`
                    }
                    """.trimIndent(),
                )
            }
            javaSource()
        }

        project.build(":child:publish")

        val coordinates = MavenCoordinates(name = "child")
        assertSoftly { softly ->
            softly.assertThat(project).hasArtifact(repoDir, coordinates)
        }
    }

    /** `promoteToCentral` without the flag wires no Central tasks. */
    @Test
    fun `promoteToCentral without flag wires no central tasks`() {
        project.configure {
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "demo",
                  "version": "1.0.0"
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.publish")
                }
                repositories {
                    mavenCentral()
                }
                easy {
                    publish {
                        enabled.set(true)
                        promoteToCentral()
                    }
                    codemeta { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result = project.build("tasks", "--all")

        assertSoftly { softly ->
            softly.assertThat(result.output).doesNotContain("publishToMavenCentral")
        }
    }

    /** `promoteToCentral` with the flag activates the Central pipeline (hermetic: empty staging). */
    @Test
    fun `promoteToCentral with flag activates central deploy`() {
        project.configure {
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "demo",
                  "version": "1.0.0"
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.publish")
                }
                repositories {
                    mavenCentral()
                }
                easy {
                    publish {
                        enabled.set(true)
                        promoteToCentral()
                    }
                    codemeta { enabled.set(true) }
                }
                """.trimIndent(),
            )
            systemProperty("jreleaser.mavencentral.username", "test-central-username")
            systemProperty("jreleaser.mavencentral.password", "test-central-password")
        }

        val result = project.build("publishToMavenCentral", "-Ppromote")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("nothing staged, skipping deploy")
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
